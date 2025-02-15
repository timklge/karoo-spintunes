package de.timklge.karoospintunes.datatypes.actions

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import de.timklge.karoospintunes.KarooSpintunesExtension
import de.timklge.karoospintunes.spotify.APIClientProvider
import de.timklge.karoospintunes.spotify.PlayerStateProvider
import de.timklge.karoospintunes.spotify.WebAPIClient
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ToggleShuffleAction : ActionCallback, KoinComponent {
    private val apiClientProvider: APIClientProvider by inject()
    private val playerStateProvider: PlayerStateProvider by inject()

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        Log.d(KarooSpintunesExtension.TAG, "Toggle shuffle action called")

        val currentShuffleState = playerStateProvider.state.first().isShuffling == true

        val apiClient = apiClientProvider.getActiveAPIInstance().first()
        apiClient.toggleShuffle(!currentShuffleState)

        playerStateProvider.update { appState ->
            appState.copy(commandPending = appState.commandPending || (apiClient is WebAPIClient), isInOptionsMenu = false, isShuffling = !currentShuffleState)
        }
    }
}