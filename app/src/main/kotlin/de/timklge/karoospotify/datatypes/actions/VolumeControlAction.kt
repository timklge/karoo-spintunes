package de.timklge.karoospotify.datatypes.actions

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import de.timklge.karoospotify.KarooSpotifyExtension
import de.timklge.karoospotify.spotify.APIClientProvider
import de.timklge.karoospotify.spotify.LocalClient
import de.timklge.karoospotify.spotify.PlayerStateProvider
import de.timklge.karoospotify.spotify.WebAPIClient
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.TimeSource

const val VOLUME_CONTROL_STEP = 0.2f

abstract class VolumeControlAction: ActionCallback, KoinComponent {
    private val apiClientProvider: APIClientProvider by inject()
    private val playerStateProvider: PlayerStateProvider by inject()

    protected abstract fun getVolumeStep(): Float

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val start = TimeSource.Monotonic.markNow()
        val playerState = playerStateProvider.state.first()

        Log.d(KarooSpotifyExtension.TAG, "Volume control action called with step ${getVolumeStep()}")

        val apiClient = apiClientProvider.getActiveAPIInstance().first()

        val oldVolume = when (apiClient) {
            is WebAPIClient -> {
                playerState.volume ?: 0.5f
            }

            is LocalClient -> {
                apiClient.getVolume()
            }

            else -> {
                error("Unknown API client")
            }
        }
        val volume = (oldVolume + getVolumeStep()).coerceIn(0f, 1f)

        apiClient.setVolume(volume)

        if (apiClient is WebAPIClient){
            playerStateProvider.update { appState ->
                appState.copy(commandPending = true, volume = volume)
            }
        } else {
            playerStateProvider.update { appState ->
                appState.copy(volume = volume)
            }
        }

        val runtime = TimeSource.Monotonic.markNow() - start
        Log.d(KarooSpotifyExtension.TAG, "Volume control action took $runtime")
    }
}


class LouderAction : VolumeControlAction() {
    override fun getVolumeStep(): Float = VOLUME_CONTROL_STEP
}

class QuieterAction : VolumeControlAction() {
    override fun getVolumeStep(): Float = -VOLUME_CONTROL_STEP
}
