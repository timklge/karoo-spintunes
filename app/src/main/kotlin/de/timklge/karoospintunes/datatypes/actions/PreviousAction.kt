package de.timklge.karoospintunes.datatypes.actions

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import de.timklge.karoospintunes.KarooSpintunesExtension
import de.timklge.karoospintunes.spotify.APIClientProvider
import de.timklge.karoospintunes.spotify.LocalClient
import de.timklge.karoospintunes.spotify.PlayerStateProvider
import de.timklge.karoospintunes.spotify.WebAPIClient
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.TimeSource

class PreviousAction : ActionCallback, KoinComponent {
    private val apiClientProvider: APIClientProvider by inject()
    private val playerStateProvider: PlayerStateProvider by inject()

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val start = TimeSource.Monotonic.markNow()

        Log.d(KarooSpintunesExtension.TAG, "Previous action called")

        val state = playerStateProvider.state.first()
        val currentPlayProgress = state.playProgressInMs ?: 0
        val currentTrackLength = state.currentTrackLengthInMs

        val isAtEndOfTrack = currentTrackLength != null && currentPlayProgress >= currentTrackLength - 2000
        val isAtStartOfTrack = currentPlayProgress < 15_000

        val apiClient = apiClientProvider.getActiveAPIInstance().first()

        when (apiClient) {
            is LocalClient -> {
                apiClient.previous()
            }

            is WebAPIClient -> {
                if (!isAtStartOfTrack && !isAtEndOfTrack){
                    apiClient.seek(0)

                    playerStateProvider.update { appState ->
                        appState.copy(commandPending = false, playProgressInMs = 0, isInOptionsMenu = false)
                    }

                    return
                } else {
                    apiClient.previous()
                }
            }

            else -> {
                error("Unknown API client type")
            }
        }

        playerStateProvider.update { appState ->
            appState.copy(commandPending = appState.commandPending || (apiClient is WebAPIClient), isInOptionsMenu = false)
        }

        val runtime = TimeSource.Monotonic.markNow() - start
        Log.d(KarooSpintunesExtension.TAG, "Previous action took $runtime")
    }
}