package de.timklge.karoospintunes.datatypes.actions

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import de.timklge.karoospintunes.KarooSpintunesExtension
import de.timklge.karoospintunes.spotify.APIClientProvider
import de.timklge.karoospintunes.spotify.PlayerStateProvider
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

const val SEEK_MS = 30_000

class RewindAction : ActionCallback, KoinComponent {
    private val apiClientProvider: APIClientProvider by inject()
    private val playerStateProvider: PlayerStateProvider by inject()

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        Log.d(KarooSpintunesExtension.TAG, "Rewind action called")

        val state = playerStateProvider.state.first()
        val newPosition = state.playProgressInMs?.minus(SEEK_MS)?.coerceAtLeast(0)

        val apiClient = apiClientProvider.getActiveAPIInstance().first()

        if (newPosition != null){
            apiClient.seek(newPosition)

            playerStateProvider.update { appState ->
                appState.copy(playProgressInMs = newPosition)
            }
        }
    }
}