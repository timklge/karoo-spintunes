package de.timklge.karoospintunes

import android.content.Context
import android.util.Log
import de.timklge.karoospintunes.KarooSpintunesExtension.Companion.TAG
import de.timklge.karoospintunes.datatypes.PlayerDataType
import de.timklge.karoospintunes.screens.SpintuneSettings
import de.timklge.karoospintunes.spotify.APIClientProvider
import de.timklge.karoospintunes.spotify.LocalClient
import de.timklge.karoospintunes.spotify.PlaybackType
import de.timklge.karoospintunes.spotify.PlayerAction
import de.timklge.karoospintunes.spotify.PlayerInPreviewModeProvider
import de.timklge.karoospintunes.spotify.PlayerStateProvider
import de.timklge.karoospintunes.spotify.RepeatState
import de.timklge.karoospintunes.spotify.ThumbnailCache
import de.timklge.karoospintunes.spotify.WebAPIClient
import io.hammerhead.karooext.models.ActiveRideProfile
import io.hammerhead.karooext.models.RideState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlin.time.TimeSource

class KarooSpintunesServices(private val webAPIClient: WebAPIClient,
                             private val apiClientProvider: APIClientProvider,
                             private val thumbnailCache: ThumbnailCache,
                             private val autoVolume: AutoVolume,
                             private val localClient: LocalClient,
                             private val playerStateProvider: PlayerStateProvider,
                             private val playerInPreviewModeProvider: PlayerInPreviewModeProvider,
                             private val context: Context,
                             private val karooSystem: KarooSystemServiceProvider) {

    private val scope = CoroutineScope(Dispatchers.IO)

    fun startJobs(){
        scope.launch {
            supervisorScope {
                launch { startPlayerRefreshJob() }
                launch { startPlayerAdvanceJob() }
                launch { startThumbnailCleanupJob() }
                launch { startLocalSpotifyJob() }
                launch { startAutoVolumeJob() }
            }
        }
    }

    fun cancelJobs(){
        scope.cancel()
    }

    private suspend fun startAutoVolumeJob() {
        autoVolume.setAutoVolume()
    }

    private suspend fun startPlayerRefreshJob() {
        apiClientProvider.streamLocalSpotifyIsEnabled().collectLatest {
            Log.d(TAG, "Local Spotify enabled: $it")

            if (it) {
                refreshLocalPlayer()
            } else {
                refreshWebPlayer()
            }
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun refreshLocalPlayer() {
        Log.d(TAG, "Getting local player state")

        localClient.streamState()
            .debounce(500L)
            .collect { (playerError, playerState) ->
                playerStateProvider.update { state ->
                    val disabled = mutableMapOf<PlayerAction, Boolean>()

                    disabled[PlayerAction.SET_VOLUME] = false

                    state.copy(
                        isPlayingType = if (playerState?.track?.isEpisode == true) PlaybackType.EPISODE else PlaybackType.TRACK,
                        isPlayingTrackName = playerState?.track?.name,
                        isPlayingTrackUri = playerState?.track?.uri,
                        isPlayingArtistName = playerState?.track?.artists?.joinToString(", ") {
                            it.name ?: ""
                        },
                        isPlayingShowName = if(playerState?.track?.isEpisode == true || playerState?.track?.isPodcast == true) playerState.track?.album?.name else null,
                        isPlayingTrackThumbnailUrls = playerState?.track?.imageUri?.raw?.let {
                            Log.d(TAG, "Thumbnail URL: $it")
                            listOf(it)
                        },
                        isShuffling = playerState?.playbackOptions?.isShuffling,
                        isRepeating = playerState?.playbackOptions?.repeatMode?.let { RepeatState.fromInt(it) },
                        isInitialized = true,
                        currentTrackLengthInMs = playerState?.track?.duration?.toInt(),
                        playProgressInMs = playerState?.playbackPosition?.toInt(),
                        isPlaying = playerState?.track?.name?.let { !playerState.isPaused },
                        commandPending = false,
                        isLocalPlayer = true,
                        disabledActions = disabled,
                        volume = localClient.getVolume(),
                        error = playerError
                    )
                }
            }
    }

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
                delay(3_000)
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

        val refreshRequestedFlow = tickerFlow.flatMapLatest {
            flow {
                while (true) {
                    emit(Unit)
                    delay(45 * 1_000)
                }
            }
        }

        data class StreamData(
            val isVisible: Boolean,
            val rideState: RideState,
            val rideProfile: ActiveRideProfile?,
            val settings: SpintuneSettings,
            val playerInPreviewMode: Int
        )

        val refreshRequestedFlowActiveOrPreview = combine(karooSystem.karooSystemService.streamDatatypeIsVisible(PlayerDataType.DATA_TYPE_ID),
            karooSystem.streamRideState(),
            karooSystem.karooSystemService.streamActiveRideProfile(),
            karooSystem.streamSettings(),
            playerInPreviewModeProvider.state
        ) { isVisible, rideState, rideProfile, settings, playerInPreviewMode ->
            StreamData(isVisible, rideState, rideProfile, settings, playerInPreviewMode.inPreviewMode)
        }.map { streamData ->
            if (streamData.settings.onlyRefreshOnActivePage == true) {
                Log.d(TAG, "Player visibility update: isVisible=${streamData.isVisible}, playerInPreviewMode=${streamData.playerInPreviewMode}, rideState=${streamData.rideState}")

                streamData.playerInPreviewMode > 0 || (streamData.isVisible && (streamData.rideState is RideState.Recording || streamData.rideState is RideState.Paused))
            } else {
                val datatypesOnCurrentProfile = streamData.rideProfile?.profile?.pages
                    ?.flatMap { it.elements }
                    ?.map { it.dataTypeId }
                    ?.toSet() ?: emptySet()

                val isOnCurrentProfile = (streamData.rideState is RideState.Recording || streamData.rideState is RideState.Paused) && datatypesOnCurrentProfile.contains(PlayerDataType.DATA_TYPE_ID)

                Log.d(TAG, "Player visibility update: playerInPreviewMode=${streamData.playerInPreviewMode}, isOnCurrentProfile=$isOnCurrentProfile, rideState=${streamData.rideState}")

                streamData.playerInPreviewMode > 0 || isOnCurrentProfile
            }
        }.distinctUntilChanged()

        val refreshFlow = combine(refreshRequestedFlow, refreshRequestedFlowActiveOrPreview) { _, activeOrPreview ->
            activeOrPreview
        }.filter { it }.throttle(10_000L)

        refreshFlow.collect {
            try {
                Log.d(TAG, "Getting player state")
                val start = TimeSource.Monotonic.markNow()
                val playerState = webAPIClient.getPlayerState()
                val runtime = TimeSource.Monotonic.markNow() - start
                Log.d(TAG, "Got player state in $runtime: $playerState")

                val disabledActions = mutableMapOf<PlayerAction, Boolean>()
                playerState?.device?.supportsVolume?.let { volumeSupported -> disabledActions.put(
                    PlayerAction.SET_VOLUME, !volumeSupported) }
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
                        isLocalPlayer = false,
                        disabledActions = disabledActions,
                        volume = playerState?.device?.volumePercent?.toFloat()?.div(100f)?.coerceIn(0f, 1f),
                        error = if (playerState != null) null else state.error
                    )
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to get player state: ${e.message}", e)
            }
        }
    }

    private suspend fun startPlayerAdvanceJob() {
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
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update player progress", e)
            }
        }
    }

    private suspend fun startThumbnailCleanupJob() {
        while(true){
            try {
                thumbnailCache.clearCache()
            } catch (e: Exception){
                Log.e(TAG, "Failed to clean up thumbnail cache", e)
            }

            delay(60 * 60 * 1_000)
        }
    }

    private suspend fun startLocalSpotifyJob() {
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
}