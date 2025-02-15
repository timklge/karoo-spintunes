package de.timklge.karoospotify

import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.OnHttpResponse
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.timeout
import kotlin.time.Duration.Companion.seconds

@OptIn(FlowPreview::class)
fun KarooSystemService.makeHttpRequest(method: String, url: String, queue: Boolean = false, headers: Map<String, String> = emptyMap(), body: ByteArray? = null): Flow<HttpResponseState.Complete> {
    val flow = callbackFlow {
        Log.d(KarooSpotifyExtension.TAG, "$method request to ${url}...")


        val listenerId = addConsumer(
            OnHttpResponse.MakeHttpRequest(
                method = method,
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
                Log.e(KarooSpotifyExtension.TAG, "Failed to make http request: $s")
                close(IllegalStateException(s))
                Unit
            }
        )
        awaitClose {
            removeConsumer(listenerId)
        }
    }

    return if (queue){
        flow
    } else {
        flow.timeout(60.seconds).catch { e: Throwable ->
            if (e is TimeoutCancellationException){
                emit(HttpResponseState.Complete(500, mapOf(), null, "Timeout"))
            } else {
                throw e
            }
        }
    }
}

