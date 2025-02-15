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
import kotlin.time.TimeSource

class PlayAction : ActionCallback, KoinComponent {
    private val apiClientProvider: APIClientProvider by inject()
    private val playerStateProvider: PlayerStateProvider by inject()

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val start = TimeSource.Monotonic.markNow()
        Log.d(KarooSpintunesExtension.TAG, "Play action called")

        val apiClient = apiClientProvider.getActiveAPIInstance().first()
        apiClient.play()

        playerStateProvider.update { appState ->
            appState.copy(isPlaying = true)
        }

        val runtime = TimeSource.Monotonic.markNow() - start
        Log.d(KarooSpintunesExtension.TAG, "Play action took $runtime")
    }
}