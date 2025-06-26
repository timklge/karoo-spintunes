package de.timklge.karoospintunes

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.ActiveRidePage
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

fun KarooSystemService.streamDataFlow(dataTypeId: String): Flow<StreamState> {
    return callbackFlow {
        val listenerId = addConsumer(OnStreamState.StartStreaming(dataTypeId)) { event: OnStreamState ->
            trySendBlocking(event.state)
        }
        awaitClose {
            removeConsumer(listenerId)
        }
    }
}

fun KarooSystemService.streamActiveRidePage(): Flow<ActiveRidePage> {
    return callbackFlow {
        val listenerId = addConsumer { activeRidePage: ActiveRidePage ->
            trySendBlocking(activeRidePage)
        }
        awaitClose {
            removeConsumer(listenerId)
        }
    }
}

fun KarooSystemService.streamDatatypeIsVisible(
    datatype: String,
): Flow<Boolean> {
    return streamActiveRidePage().map { page ->
        page.page.elements.any { it.dataTypeId == datatype }
    }
}

@Serializable
data class WebAPIError(
    val status: Int,
    val message: String
)

@Serializable
data class WebAPIErrorResponse(
    val error: WebAPIError
)
