package de.timklge.karoospotify.datatypes.actions

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import de.timklge.karoospotify.KarooSpotifyExtension
import de.timklge.karoospotify.spotify.APIClientProvider
import de.timklge.karoospotify.spotify.PlayerStateProvider
import de.timklge.karoospotify.spotify.RepeatState
import de.timklge.karoospotify.spotify.WebAPIClient
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ToggleRepeatAction : ActionCallback, KoinComponent {
    private val apiClientProvider: APIClientProvider by inject()
    private val playerStateProvider: PlayerStateProvider by inject()

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        Log.d(KarooSpotifyExtension.TAG, "Toggle repeat action called")

        val currentRepeatState = playerStateProvider.state.first().isRepeating

        Log.d(KarooSpotifyExtension.TAG, "Current repeat state: $currentRepeatState")

        val newRepeatState = when (currentRepeatState) {
            RepeatState.OFF, null -> RepeatState.CONTEXT
            RepeatState.CONTEXT -> RepeatState.TRACK
            RepeatState.TRACK -> RepeatState.OFF
        }

        val apiClient = apiClientProvider.getActiveAPIInstance().first()
        apiClient.toggleRepeat(newRepeatState)

        playerStateProvider.update { appState ->
            appState.copy(commandPending = appState.commandPending || (apiClient is WebAPIClient), isInOptionsMenu = false, isRepeating = newRepeatState)
        }
    }
}