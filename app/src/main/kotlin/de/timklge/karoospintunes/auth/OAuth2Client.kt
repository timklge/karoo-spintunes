package de.timklge.karoospintunes.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import de.timklge.karoospintunes.KarooSpintunesExtension
import de.timklge.karoospintunes.KarooSystemServiceProvider
import de.timklge.karoospintunes.jsonWithUnknownKeys
import de.timklge.karoospintunes.makeHttpRequest
import de.timklge.karoospintunes.spotify.PlayerError
import de.timklge.karoospintunes.spotify.PlayerStateProvider
import io.hammerhead.karooext.models.HttpResponseState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URLEncoder
import java.nio.charset.Charset
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.GZIPInputStream

fun decompressResponse(response: HttpResponseState.Complete): HttpResponseState.Complete {
    try {
        GZIPInputStream(response.body?.inputStream()).use {
            val result = response.copy(body = it.readBytes())
            Log.d(KarooSpintunesExtension.TAG, "Decompressed response from ${response.body?.size ?: 0} bytes to ${result.body?.size ?: 0} bytes")
            return result
        }
    } catch(e: Throwable) {
        Log.w(KarooSpintunesExtension.TAG, "Failed to decompress response. Assuming it is uncompressed.")
        return response
    }
}

class HttpException(val status: Int, message: String?, val body: ByteArray): Exception(message ?: "HTTP $status")

class OAuth2Client(private val karooSystemServiceProvider: KarooSystemServiceProvider, private val playerStateProvider: PlayerStateProvider) {
    private val lock: Mutex = Mutex()
    private val clientId: String = de.timklge.karoospintunes.BuildConfig.SPOTIFY_CLIENT_ID

    private val authEndpoint: String = "https://accounts.spotify.com/authorize"
    private val tokenEndpoint: String = "https://accounts.spotify.com/api/token"
    private val redirectUri = "karoospotify://oauth2redirect"
    private val scope: String = "playlist-read-private playlist-read-collaborative user-library-read user-read-playback-state user-read-currently-playing user-modify-playback-state app-remote-control user-library-modify user-read-playback-position"

    private val codeVerifier = generateCodeVerifier()
    private val codeChallenge = generateCodeChallenge(codeVerifier)

    fun startAuthFlow(ctx: Context) {
        val authUri = Uri.parse(authEndpoint).buildUpon().apply {
            appendQueryParameter("response_type", "code")
            appendQueryParameter("client_id", clientId)
            appendQueryParameter("redirect_uri", redirectUri)
            appendQueryParameter("scope", scope)
            appendQueryParameter("code_challenge", codeChallenge)
            appendQueryParameter("code_challenge_method", "S256")
            appendQueryParameter("state", generateState())
        }.build()

        val intent = Intent(Intent.ACTION_VIEW, authUri)
        ctx.startActivity(intent)
    }

    private fun generateCodeVerifier(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray()
        val messageDigest = MessageDigest.getInstance("SHA-256")
        messageDigest.update(bytes)
        val digest = messageDigest.digest()
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generateState(): String {
        val random = SecureRandom()
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private suspend fun updateAccessToken(response: HttpResponseState.Complete): TokenResponse {
        try {
            if (response.statusCode !in 200..299) {
                val body = response.body ?: byteArrayOf()
                // Handle specific refresh token errors
                when (response.statusCode) {
                    400 -> throw HttpException(400, "Invalid refresh token. Please login again.", body)
                    401 -> throw HttpException(401, "Client is not authorized. Please login again.", body)
                    else -> throw HttpException(response.statusCode, "HTTP ${response.statusCode}: ${response.error}", body)
                }
            }

            if (response.error == null){
                val token = response.body?.let { body ->
                    jsonWithUnknownKeys.decodeFromString<TokenResponse>(body.decodeToString())
                }

                if (token == null) error("Failed to parse token response")

                Log.i(KarooSpintunesExtension.TAG, "Successfully exchanged code for token")

                karooSystemServiceProvider.saveSettings { settings ->
                    val newToken = settings.token?.copy(
                        accessToken = token.accessToken,
                        tokenType = token.tokenType,
                        expiresIn = token.expiresIn,
                        refreshToken = token.refreshToken ?: settings.token.refreshToken,
                        scope = token.scope ?: settings.token.scope
                    ) ?: token

                    settings.copy(token = newToken)
                }

                return token
            }

            throw HttpException(response.statusCode, "Failed to get auth token: ${response.error}. Please login again.", response.body ?: byteArrayOf())
        } catch (e: Throwable){
            Log.e(KarooSpintunesExtension.TAG, "Failed to get auth token. Please login again.", e)

            karooSystemServiceProvider.saveSettings { settings -> settings.copy(token = null) }

            throw e
        }
    }

    suspend fun exchangeCodeForToken(code: String, ctx: Context): TokenResponse {
        // Build form data
        val formData = buildString {
            append("grant_type=authorization_code")
            append("&code=").append(URLEncoder.encode(code, "UTF-8"))
            append("&redirect_uri=").append(URLEncoder.encode(redirectUri, "UTF-8"))
            append("&client_id=").append(URLEncoder.encode(clientId, "UTF-8"))
            append("&code_verifier=").append(URLEncoder.encode(codeVerifier, "UTF-8"))
        }

        // Convert to bytes and write
        val postData = formData.encodeToByteArray()

        val response = karooSystemServiceProvider.karooSystemService.makeHttpRequest("POST", tokenEndpoint, headers = mapOf(
            "Content-Type" to "application/x-www-form-urlencoded"
        ), body = postData)

        return updateAccessToken(response)
    }

    private fun refreshAccessToken(): Flow<TokenResponse> = flow {
        Log.i(KarooSpintunesExtension.TAG, "Refreshing access token")
        val refreshToken = karooSystemServiceProvider.streamSettings().first().token?.refreshToken ?: throw HttpException(401, "No refresh token available", byteArrayOf())
        
        val formData = buildString {
            append("grant_type=refresh_token")
            append("&refresh_token=").append(URLEncoder.encode(refreshToken, "UTF-8"))
            append("&client_id=").append(URLEncoder.encode(clientId, "UTF-8"))
        }

        val postData = formData.toByteArray(Charset.forName("UTF-8"))

        val response = karooSystemServiceProvider.karooSystemService.makeHttpRequest("POST", tokenEndpoint, headers = mapOf(
            "Content-Type" to "application/x-www-form-urlencoded",
            "User-Agent" to KarooSpintunesExtension.TAG
        ), body = postData)

        val newAccessToken = updateAccessToken(response)

        emit(newAccessToken)
    }

    private suspend fun internalMakeAuthorizedRequest(method: String, url: String, queue: Boolean = false, headers: Map<String, String> = emptyMap(), body: ByteArray? = null, markAsPending: Boolean = true): HttpResponseState.Complete {
        if (markAsPending) playerStateProvider.update { it.copy(requestPending = it.requestPending + 1) }

        try {
            val token = karooSystemServiceProvider.streamSettings().first().token ?: error("No token available")

            val headersWithAuth = headers.toMutableMap()
            headersWithAuth["Authorization"] = "Bearer ${token.accessToken}"
            headersWithAuth["User-Agent"] = KarooSpintunesExtension.TAG
            headersWithAuth["Accept-Encoding"] = "gzip"

            var response = karooSystemServiceProvider.karooSystemService.makeHttpRequest(method, url, queue, headersWithAuth, body)

            if (response.statusCode == 401) {
                val newToken = refreshAccessToken().first()
                val newHeaders = headersWithAuth.toMutableMap()

                newHeaders["Authorization"] = "Bearer ${newToken.accessToken}"
                newHeaders["User-Agent"] = KarooSpintunesExtension.TAG

                response = karooSystemServiceProvider.karooSystemService.makeHttpRequest(method, url, queue, newHeaders, body)

                val responseBody = response.body
                if (responseBody != null && responseBody.isNotEmpty()) response = decompressResponse(response)
            }

            /* if (response.statusCode == 404) {
                Log.w(KarooSpotifyExtension.TAG, "Resource not found: $url. Assuming no active device.")

                return HttpResponseState.Complete(204, mapOf(), null, "No active device")
            } */

            if (response.statusCode == 429) {
                val retryAfterSeconds = response.headers["Retry-After"]?.toLongOrNull() ?: 30L
                Log.w(KarooSpintunesExtension.TAG, "Rate limited. Waiting for $retryAfterSeconds seconds before retrying")

                delay(retryAfterSeconds * 1000L)

                response = internalMakeAuthorizedRequest(method, url, queue, headers, body, markAsPending)
            }

            val responseBody = response.body
            if (responseBody != null && responseBody.isNotEmpty()) response = decompressResponse(response)

            if (response.statusCode !in 200..299) {
                throw HttpException(response.statusCode, response.error, response.body ?: byteArrayOf())
            }

            if (response.error != null) {
                throw HttpException(response.statusCode, response.error, response.body ?: byteArrayOf())
            }

            return response
        } catch (e: HttpException) {
            Log.e(KarooSpintunesExtension.TAG, "HTTP request failed: ${e.status} - ${e.message}", e)

            val reportedError = when (e.status) {
                0, -1 -> PlayerError("Offline", "No internet connection")
                else -> PlayerError("HTTP ${e.status}", e.message ?: "Unknown error")
            }

            playerStateProvider.update { it.copy(error = reportedError) }
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(KarooSpintunesExtension.TAG, "Request failed: ${e.message}", e)
            playerStateProvider.update { it.copy(error = PlayerError("Error", e.message ?: "Unknown error")) }
            throw e
        } finally {
            if (markAsPending) playerStateProvider.update { it.copy(requestPending = it.requestPending - 1) }
        }
    }

    suspend fun makeAuthorizedRequest(method: String, url: String, queue: Boolean = false, headers: Map<String, String> = emptyMap(), body: ByteArray? = null, markAsPending: Boolean = true): HttpResponseState.Complete = lock.withLock {
        internalMakeAuthorizedRequest(method, url, queue, headers, body, markAsPending)
    }
}