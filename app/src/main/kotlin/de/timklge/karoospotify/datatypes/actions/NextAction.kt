package de.timklge.karoospotify.datatypes.actions

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import de.timklge.karoospotify.KarooSpotifyExtension
import de.timklge.karoospotify.spotify.APIClientProvider
import de.timklge.karoospotify.spotify.PlayerStateProvider
import de.timklge.karoospotify.spotify.WebAPIClient
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.TimeSource

class NextAction : ActionCallback, KoinComponent {
    private val apiClientProvider: APIClientProvider by inject()
    private val playerStateProvider: PlayerStateProvider by inject()

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val start = TimeSource.Monotonic.markNow()

        Log.d(KarooSpotifyExtension.TAG, "Next action called")

        val apiClient = apiClientProvider.getActiveAPIInstance().first()
        apiClient.next()

        playerStateProvider.update { appState ->
            appState.copy(commandPending = appState.commandPending || (apiClient is WebAPIClient), isInOptionsMenu = false)
        }

        val runtime = TimeSource.Monotonic.markNow() - start
        Log.d(KarooSpotifyExtension.TAG, "Next action took $runtime")
    }
}