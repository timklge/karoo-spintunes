package de.timklge.karoospotify.spotify

import android.content.Context
import de.timklge.karoospotify.KarooSystemServiceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map

class APIClientProvider(
    private val karooSystemServiceProvider: KarooSystemServiceProvider,
    private val context: Context,
    private val webAPIClient: WebAPIClient,
    private val localClient: LocalClient
){
    /**
     * Returns a flow that emits true if the user has enabled local Spotify and the connection is active, and false otherwise
     */
    fun streamLocalSpotifyIsEnabled(): Flow<Boolean> =
        karooSystemServiceProvider.streamSettings()
            .distinctUntilChangedBy { it.useLocalSpotifyIfAvailable }
            .combine(localClient.connectionState) { settings, connectionState ->
                settings.useLocalSpotifyIfAvailable && connectionState == LocalClientConnectionState.Connected
            }
            .distinctUntilChanged()

    fun getActiveAPIInstance(): Flow<APIClient> {
        return streamLocalSpotifyIsEnabled().distinctUntilChanged().map { useLocalSpotify ->
            if (useLocalSpotify) {
                localClient
            } else {
                webAPIClient
            }
        }
    }
}