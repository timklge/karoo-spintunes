package de.timklge.karoospintunes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.ActiveRidePage
import io.hammerhead.karooext.models.ActiveRideProfile
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

fun KarooSystemService.streamActiveRidePage(): Flow<ActiveRidePage?> {
    return callbackFlow {
        trySendBlocking(null)

        val listenerId = addConsumer { activeRidePage: ActiveRidePage ->
            trySendBlocking(activeRidePage)
        }
        awaitClose {
            removeConsumer(listenerId)
        }
    }
}

fun KarooSystemService.streamActiveRideProfile(): Flow<ActiveRideProfile?> {
    return callbackFlow {
        trySendBlocking(null)

        val listenerId = addConsumer { activeRideProfile: ActiveRideProfile ->
            trySendBlocking(activeRideProfile)
        }
        awaitClose {
            removeConsumer(listenerId)
        }
    }
}

fun powerSaveModeFlow(context: Context): Flow<Boolean> = callbackFlow {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    // Emit the initial state
    trySend(powerManager.isPowerSaveMode)

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            trySend(powerManager.isPowerSaveMode)
        }
    }

    val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
    context.registerReceiver(receiver, filter)

    awaitClose { context.unregisterReceiver(receiver) }
}

fun KarooSystemService.streamDatatypeIsVisible(
    datatype: String,
): Flow<Boolean> {
    return streamActiveRidePage().map { page ->
        page?.page?.elements?.any { it.dataTypeId == datatype } == true
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
