package de.timklge.karoospotify

import android.util.Log
import de.timklge.karoospotify.datatypes.PlayerDataType
import de.timklge.karoospotify.spotify.APIClientProvider
import de.timklge.karoospotify.spotify.LocalClient
import de.timklge.karoospotify.spotify.PlaybackType
import de.timklge.karoospotify.spotify.PlayerAction
import de.timklge.karoospotify.spotify.PlayerStateProvider
import de.timklge.karoospotify.spotify.RepeatState
import de.timklge.karoospotify.spotify.ThumbnailCache
import de.timklge.karoospotify.spotify.WebAPIClient
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.extension.KarooExtension
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import kotlin.time.TimeSource

class KarooSpotifyExtension : KarooExtension("karoo-spotify", "1.0-beta1") {
    companion object {
        const val TAG = "karoo-spotify"
    }

    private val jobs = mutableSetOf<Job>()

    private val webApiClient: WebAPIClient by inject()
    private val apiClientProvider: APIClientProvider by inject()
    private val karooSystem: KarooSystemServiceProvider by inject()
    private val playerDataType: PlayerDataType by inject()

    private val thumbnailCache: ThumbnailCache by inject()
    private val autoVolume: AutoVolume by inject()
    private val localClient: LocalClient by inject()
    private val playerStateProvider: PlayerStateProvider by inject()

    override val types: List<DataTypeImpl> = listOf(playerDataType)

    /**
     * Emits one value when the player has reached the end of the current track or the user has issued a new command
     */
    private fun playerExhaustedStream(): Flow<Unit> = flow {
        var isExhausted = false

        playerStateProvider.state.collect { state ->
            val newValue = (state.isPlaying == true
                    && state.playProgressInMs != null
                    && state.currentTrackLengthInMs != null
                    && state.playProgressInMs >= state.currentTrackLengthInMs) || state.commandPending

            if (newValue && !isExhausted){
                delay(1000)
                emit(Unit)
            }

            isExhausted = newValue
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun refreshWebPlayer() {
        val tickerFlow = flow {
            emit(Unit)
            emitAll(playerExhaustedStream())
        }

        val refreshFlow = tickerFlow.flatMapLatest {
            flow {
                while (true) {
                    emit(Unit)
                    delay(40 * 1_000)
                }
            }
        }

        refreshFlow.collect {
            try {
                Log.d(TAG, "Getting player state")
                val start = TimeSource.Monotonic.markNow()
                val playerState = webApiClient.getPlayerState(applicationContext)
                val runtime = TimeSource.Monotonic.markNow() - start
                Log.d(TAG, "Got player state in $runtime: $playerState")

                val disabledActions = mutableMapOf<PlayerAction, Boolean>()
                playerState?.device?.supportsVolume?.let { volumeSupported -> disabledActions.put(PlayerAction.SET_VOLUME, !volumeSupported) }
                playerState?.actions?.disallows?.let { disallows ->
                    disallows.pausing?.let { disabledActions.put(PlayerAction.PAUSE, it) }
                    disallows.seeking?.let { disabledActions.put(PlayerAction.SEEK, it) }
                    disallows.skippingNext?.let { disabledActions.put(PlayerAction.SKIP_NEXT, it) }
                    disallows.skippingPrev?.let { disabledActions.put(PlayerAction.SKIP_PREVIOUS, it) }
                    disallows.togglingRepeatContext?.let { disabledActions.put(PlayerAction.TOGGLE_REPEAT, it) }
                    disallows.togglingRepeatTrack?.let { disabledActions.put(PlayerAction.TOGGLE_REPEAT, it) }
                    disallows.resuming?.let { disabledActions.put(PlayerAction.PLAY, it) }
                    disallows.togglingShuffle?.let { disabledActions.put(PlayerAction.TOGGLE_SHUFFLE, it) }
                }

                playerStateProvider.update { state ->
                    state.copy(
                        isPlayingType = PlaybackType.fromString(playerState?.currentlyPlayingType),
                        isPlayingTrackName = playerState?.item?.name,
                        isPlayingTrackId = playerState?.item?.id,
                        isPlayingArtistName = playerState?.item?.artists?.joinToString(", ") {
                            it.name ?: ""
                        },
                        isPlayingShowName = playerState?.item?.show?.getItemName(),
                        isPlayingTrackThumbnailUrls = (playerState?.item?.images ?: playerState?.item?.album?.images)?.mapNotNull { it.url },
                        isShuffling = playerState?.shuffleState,
                        isRepeating = RepeatState.fromString(playerState?.repeatState),
                        isInitialized = true,
                        currentTrackLengthInMs = playerState?.item?.durationMs,
                        playProgressInMs = playerState?.progressMs,
                        isPlaying = playerState?.isPlaying,
                        commandPending = false,
                        disabledActions = disabledActions,
                        volume = playerState?.device?.volumePercent?.toFloat()?.div(100f)?.coerceIn(0f, 1f)
                    )
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to get player state: ${e.message}", e)
            }
        }
    }

    private fun startPlayerAdvanceJob(): Job = CoroutineScope(Dispatchers.IO).launch {
        while(true) {
            try {
                playerStateProvider.update { player ->
                    if (player.isPlaying == true) {
                        val currentProgress = player.playProgressInMs ?: 0
                        val length = player.currentTrackLengthInMs ?: 0

                        player.copy(playProgressInMs = (currentProgress + 5000).coerceAtMost(length))
                    } else {
                        player
                    }
                }

                delay(5_000)
            } catch(e: CancellationException){
                Log.w(TAG, "Player advance job was cancelled")
                break
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update player progress", e)
            }
        }
    }

    private fun startThumbnailCleanupJob(): Job = CoroutineScope(Dispatchers.IO).launch {
        while(true){
            try {
                thumbnailCache.clearCache()
            } catch (e: Exception){
                Log.e(TAG, "Failed to clean up thumbnail cache", e)
            }

            delay(60 * 60 * 1_000)
        }
    }

    private fun startLocalSpotifyJob(): Job = CoroutineScope(Dispatchers.IO).launch {
        karooSystem.streamSettings()
            .distinctUntilChangedBy { it.useLocalSpotifyIfAvailable }
            .collectLatest { settings ->
                // Disconnect if local connection is still active
                try {
                    localClient.disconnect()
                } catch(e: Throwable){
                    Log.e(TAG, "Failed to disconnect local Spotify client", e)
                }

                // If the user has disabled local Spotify, we don't need to do anything
                if (!settings.useLocalSpotifyIfAvailable) awaitCancellation()

                while(true){
                    val connectionState = localClient.initialize()
                    Log.d(TAG, "Local Spotify connection state: $connectionState")

                    delay(1_000L * 60)
                }
        }
    }

    override fun onCreate() {
        super.onCreate()

        jobs.forEach { it.cancel() }
        jobs.clear()

        jobs.addAll(setOf(
            startPlayerRefreshJob(),
            startPlayerAdvanceJob(),
            startThumbnailCleanupJob(),
            startLocalSpotifyJob(),
            startAutoVolumeJob()
        ))
    }

    private fun startAutoVolumeJob(): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            autoVolume.setAutoVolume()
        }
    }

    private fun startPlayerRefreshJob(): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            apiClientProvider.streamLocalSpotifyIsEnabled().collectLatest {
                Log.d(TAG, "Local Spotify enabled: $it")

                if (it) {
                    refreshLocalPlayer()
                } else {
                    refreshWebPlayer()
                }
            }
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun refreshLocalPlayer() {
        Log.d(TAG, "Getting local player state")

        localClient.streamState()
            .debounce(500L)
            .collect { playerState ->
                playerStateProvider.update { state ->
                    val disabled = mutableMapOf<PlayerAction, Boolean>()

                    disabled[PlayerAction.SET_VOLUME] = false

                    state.copy(
                        isPlayingType = if (playerState.track?.isEpisode == true) PlaybackType.EPISODE else PlaybackType.TRACK,
                        isPlayingTrackName = playerState.track?.name,
                        isPlayingTrackUri = playerState.track?.uri,
                        isPlayingArtistName = playerState.track?.artists?.joinToString(", ") {
                            it.name ?: ""
                        },
                        isPlayingShowName = if(playerState.track?.isEpisode == true || playerState.track?.isPodcast == true) playerState.track?.album?.name else null,
                        isPlayingTrackThumbnailUrls = playerState.track?.imageUri?.raw?.let {
                            Log.d(TAG, "Thumbnail URL: $it")
                            listOf(it)
                        },
                        isShuffling = playerState.playbackOptions?.isShuffling,
                        isRepeating = playerState.playbackOptions?.repeatMode?.let { RepeatState.fromInt(it) },
                        isInitialized = true,
                        currentTrackLengthInMs = playerState.track?.duration?.toInt(),
                        playProgressInMs = playerState.playbackPosition.toInt(),
                        isPlaying = playerState.track?.name?.let { !playerState.isPaused },
                        commandPending = false,
                        disabledActions = disabled,
                        volume = localClient.getVolume()
                    )
                }
        }
    }

    override fun onDestroy() {
        jobs.forEach { it.cancel() }
        jobs.clear()

        super.onDestroy()
    }
}