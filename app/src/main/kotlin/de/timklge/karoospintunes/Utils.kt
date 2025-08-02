package de.timklge.karoospintunes

import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.OnHttpResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.readBytes
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpMethod
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.timeout
import kotlin.time.Duration.Companion.seconds

@OptIn(FlowPreview::class)
suspend fun KarooSystemService.makeHttpRequest(m: String, url: String, queue: Boolean = false, headers: Map<String, String> = emptyMap(), body: ByteArray? = null): HttpResponseState.Complete {
    if (!connected){
        // Fallback to Ktor client if not running on Karoo

        val client = HttpClient()
        try {
            val response = client.request(url) {
                method = HttpMethod.parse(m)
                headers {
                    headers.forEach { (key, value) ->
                        append(key, value)
                    }
                }
                if (body != null) {
                    setBody(body)
                }
            }
            val responseBody = response.readRawBytes()

            return HttpResponseState.Complete(
                response.status.value,
                response.headers.entries().associate { it.key to it.value.joinToString(",") },
                responseBody,
                null
            )
        } catch (e: Exception) {
            return HttpResponseState.Complete(500, emptyMap(), null, "Error: ${e.message}")
        } finally {
            client.close()
        }
    }

    val flow = callbackFlow {
        Log.d(KarooSpintunesExtension.TAG, "$m request to ${url}...")

        val listenerId = addConsumer(
            OnHttpResponse.MakeHttpRequest(
                method = m,
                url = url,
                waitForConnection = false,
                headers = headers,
                body = body
            ),
            onEvent = { event: OnHttpResponse ->
                // Log.d(KarooSpotifyExtension.TAG, "Http response event $event")
                if (event.state is HttpResponseState.Complete){
                    trySend(event.state as HttpResponseState.Complete)
                    close()
                }
            },
            onError = { s: String ->
                Log.e(KarooSpintunesExtension.TAG, "Failed to make http request: $s")
                close(IllegalStateException(s))
                Unit
            }
        )
        awaitClose {
            removeConsumer(listenerId)
        }
    }

    return if (queue){
        flow.first()
    } else {
        flow.timeout(60.seconds).catch { e: Throwable ->
            if (e is TimeoutCancellationException){
                emit(HttpResponseState.Complete(500, mapOf(), null, "Timeout"))
            } else {
                throw e
            }
        }.first()
    }
}

