package de.timklge.karoospotify.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import de.timklge.karoospotify.KarooSpotifyExtension
import de.timklge.karoospotify.KarooSystemServiceProvider
import de.timklge.karoospotify.jsonWithUnknownKeys
import de.timklge.karoospotify.makeHttpRequest
import de.timklge.karoospotify.spotify.PlayerStateProvider
import io.hammerhead.karooext.models.HttpResponseState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
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
            Log.d(KarooSpotifyExtension.TAG, "Decompressed response from ${response.body?.size ?: 0} bytes to ${result.body?.size ?: 0} bytes")
            return result
        }
    } catch(e: Throwable) {
        Log.w(KarooSpotifyExtension.TAG, "Failed to decompress response. Assuming it is uncompressed.")
        return response
    }
}

class HttpException(val status: Int, message: String?, val body: ByteArray): Exception(message ?: "HTTP $status")

class OAuth2Client(private val karooSystemServiceProvider: KarooSystemServiceProvider, private val playerStateProvider: PlayerStateProvider) {
    private val lock: Mutex = Mutex()
    private val clientId: String = de.timklge.karoospotify.BuildConfig.SPOTIFY_CLIENT_ID

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
                // Handle specific refresh token errors
                when (response.statusCode) {
                    400 -> error("Invalid refresh token")
                    401 -> error("Client is not authorized")
                    else -> error("HTTP ${response.statusCode}: ${response.error}")
                }
            }

            if (response.error == null){
                val token = response.body?.let { body ->
                    jsonWithUnknownKeys.decodeFromString<TokenResponse>(body.decodeToString())
                }

                if (token == null) error("Failed to parse token response")

                Log.i(KarooSpotifyExtension.TAG, "Successfully exchanged code for token")

                karooSystemServiceProvider.saveSettings { settings -> settings.copy(token = token) }

                return token
            }

            error("Failed to exchange code for token: ${response.error}")
        } catch (e: Throwable){
            Log.e(KarooSpotifyExtension.TAG, "Failed to exchange code for token", e)

            karooSystemServiceProvider.saveSettings { settings -> settings.copy(token = null) }

            throw e
        }
    }

    fun exchangeCodeForToken(code: String, ctx: Context): Flow<TokenResponse>    {
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

        val responseFlow = karooSystemServiceProvider.karooSystemService.makeHttpRequest("POST", tokenEndpoint, headers = mapOf(
            "Content-Type" to "application/x-www-form-urlencoded"
        ), body = postData)

        return responseFlow.map { response -> updateAccessToken(response) }
    }

    private fun refreshAccessToken(): Flow<TokenResponse> = flow {
        Log.i(KarooSpotifyExtension.TAG, "Refreshing access token")
        val refreshToken = karooSystemServiceProvider.streamSettings().first().token?.refreshToken ?: error("No refresh token available")

        val formData = buildString {
            append("grant_type=refresh_token")
            append("&refresh_token=").append(URLEncoder.encode(refreshToken, "UTF-8"))
            append("&client_id=").append(URLEncoder.encode(clientId, "UTF-8"))
        }

        val postData = formData.toByteArray(Charset.forName("UTF-8"))

        val response = karooSystemServiceProvider.karooSystemService.makeHttpRequest("POST", tokenEndpoint, headers = mapOf(
            "Content-Type" to "application/x-www-form-urlencoded",
            "User-Agent" to "KarooSpotify"
        ), body = postData).first()

        val newAccessToken = updateAccessToken(response)

        emit(newAccessToken)
    }

    private suspend fun internalMakeAuthorizedRequest(method: String, url: String, queue: Boolean = false, headers: Map<String, String> = emptyMap(), body: ByteArray? = null, markAsPending: Boolean = true): HttpResponseState.Complete {
        if (markAsPending) playerStateProvider.update { it.copy(requestPending = it.requestPending + 1) }

        try {
            val token = karooSystemServiceProvider.streamSettings().first().token ?: error("No token available")

            val headersWithAuth = headers.toMutableMap()
            headersWithAuth["Authorization"] = "Bearer ${token.accessToken}"
            headersWithAuth["User-Agent"] = "KarooSpotify"
            headersWithAuth["Accept-Encoding"] = "gzip"

            var response = karooSystemServiceProvider.karooSystemService.makeHttpRequest(method, url, queue, headersWithAuth, body).single()

            if (response.statusCode == 401) {
                val newToken = refreshAccessToken().first()
                val newHeaders = headersWithAuth.toMutableMap()

                newHeaders["Authorization"] = "Bearer ${newToken.accessToken}"
                newHeaders["User-Agent"] = "KarooSpotify"

                response = karooSystemServiceProvider.karooSystemService.makeHttpRequest(method, url, queue, newHeaders, body).first()

                val responseBody = response.body
                if (responseBody != null && responseBody.isNotEmpty()) response = decompressResponse(response)
            }

            /* if (response.statusCode == 404) {
                Log.w(KarooSpotifyExtension.TAG, "Resource not found: $url. Assuming no active device.")

                return HttpResponseState.Complete(204, mapOf(), null, "No active device")
            } */

            if (response.statusCode == 429) {
                val retryAfterSeconds = response.headers["Retry-After"]?.toLongOrNull() ?: 30L
                Log.w(KarooSpotifyExtension.TAG, "Rate limited. Waiting for $retryAfterSeconds seconds before retrying")

                delay(retryAfterSeconds * 1000L)

                response = internalMakeAuthorizedRequest(method, url, queue, headers, body, markAsPending)
            }

            if (response.statusCode == 0){
                error("Network unavailable")
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
        } finally {
            if (markAsPending) playerStateProvider.update { it.copy(requestPending = it.requestPending - 1) }
        }
    }

    suspend fun makeAuthorizedRequest(method: String, url: String, queue: Boolean = false, headers: Map<String, String> = emptyMap(), body: ByteArray? = null, markAsPending: Boolean = true): HttpResponseState.Complete = lock.withLock {
        internalMakeAuthorizedRequest(method, url, queue, headers, body, markAsPending)
    }
}