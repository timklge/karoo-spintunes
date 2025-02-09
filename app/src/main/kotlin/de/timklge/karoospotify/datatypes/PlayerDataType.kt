package de.timklge.karoospotify.datatypes

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import de.timklge.karoospotify.KarooSpotifyExtension
import de.timklge.karoospotify.R
import de.timklge.karoospotify.datatypes.actions.FastForwardAction
import de.timklge.karoospotify.datatypes.actions.LouderAction
import de.timklge.karoospotify.datatypes.actions.NextAction
import de.timklge.karoospotify.datatypes.actions.PauseAction
import de.timklge.karoospotify.datatypes.actions.PlayAction
import de.timklge.karoospotify.datatypes.actions.PreviousAction
import de.timklge.karoospotify.datatypes.actions.QuieterAction
import de.timklge.karoospotify.datatypes.actions.RewindAction
import de.timklge.karoospotify.datatypes.actions.ToggleOptionsMenuCallback
import de.timklge.karoospotify.datatypes.actions.ToggleRepeatAction
import de.timklge.karoospotify.datatypes.actions.ToggleShuffleAction
import de.timklge.karoospotify.screens.PlayActivity
import de.timklge.karoospotify.screens.QueueActivity
import de.timklge.karoospotify.spotify.APIClientProvider
import de.timklge.karoospotify.spotify.LocalClient
import de.timklge.karoospotify.spotify.PlayerAction
import de.timklge.karoospotify.spotify.PlayerState
import de.timklge.karoospotify.spotify.PlayerStateProvider
import de.timklge.karoospotify.spotify.RepeatState
import de.timklge.karoospotify.spotify.ThumbnailCache
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Composable fun ActionButton(
    @DrawableRes icon: Int,
    disabled: Boolean,
    toggledOff: Boolean = false,
    actionProvider: () -> Action
) {
    val context = LocalContext.current

    val colorFilter = if (disabled){
        ColorFilter.tint(ColorProvider(Color(ContextCompat.getColor(context, R.color.lighterGray)), Color(ContextCompat.getColor(context, R.color.darkerGray))))
    } else if (toggledOff){
        ColorFilter.tint(ColorProvider(Color(ContextCompat.getColor(context, R.color.lightGray)), Color(ContextCompat.getColor(context, R.color.darkGray))))
    } else {
        ColorFilter.tint(ColorProvider(Color.Black, Color.White))
    }

    var modifier = GlanceModifier.size(48.dp).padding(5.dp)

    if (!disabled) modifier = modifier.clickable(actionProvider())

    Image(ImageProvider(icon), "Toggle Repeat", modifier = modifier, colorFilter = colorFilter)
}

@Composable
fun PlayButton(playerState: PlayerState, playerSize: PlayerDataType.PlayerSize, disabled: Boolean) {
    if (playerState.isPlaying == true) {
        ActionButton(R.drawable.pause_regular_132, disabled) { actionRunCallback(PauseAction::class.java) }
    } else if(playerState.isPlaying == false) {
        ActionButton(R.drawable.play_regular_132, disabled) { actionRunCallback(PlayAction::class.java) }
    }

    if (playerState.isPlaying != null && playerSize == PlayerDataType.PlayerSize.SMALL){
        ActionButton(R.drawable.dots_vertical_rounded_regular_132, disabled) { actionRunCallback(ToggleOptionsMenuCallback::class.java)}
    }
}

@Composable
fun OptionsRows(context: Context, playerState: PlayerState, showToggle: Boolean, buttonsDisabled: Boolean, disabledActions: Map<PlayerAction, Boolean>) {
    Row(modifier = GlanceModifier.fillMaxWidth().height(50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ActionButton(R.drawable.rewind_regular_132, buttonsDisabled || disabledActions[PlayerAction.SEEK] == true) { actionRunCallback(RewindAction::class.java) }
        ActionButton(R.drawable.fast_forward_regular_132, buttonsDisabled || disabledActions[PlayerAction.SEEK] == true) { actionRunCallback(FastForwardAction::class.java) }
        ActionButton(R.drawable.skip_previous_regular_132, buttonsDisabled || disabledActions[PlayerAction.SKIP_PREVIOUS] == true) { actionRunCallback(PreviousAction::class.java) }
        ActionButton(R.drawable.skip_next_regular_132, buttonsDisabled || disabledActions[PlayerAction.SKIP_NEXT] == true) { actionRunCallback(NextAction::class.java) }

        if (showToggle){
            ActionButton(R.drawable.dots_vertical_rounded_regular_132, buttonsDisabled) { actionRunCallback(ToggleOptionsMenuCallback::class.java)}
        }

        if (disabledActions[PlayerAction.SET_VOLUME] != true){
            val canMakeLouder = playerState.volume?.let { v -> v < 1.0f } ?: false
            ActionButton(R.drawable.volume_full_regular_132, buttonsDisabled, !canMakeLouder) { actionRunCallback(LouderAction::class.java)}
        }
    }

    Row(modifier = GlanceModifier.fillMaxWidth().height(50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val repeatIcon = when (playerState.isRepeating) {
            RepeatState.OFF, RepeatState.CONTEXT -> R.drawable.repeat_regular_132
            RepeatState.TRACK -> R.drawable.repeat_regular_1_132
            else -> R.drawable.repeat_regular_132
        }

        val canRepeat = !buttonsDisabled && playerState.disabledActions[PlayerAction.TOGGLE_REPEAT] != true
        ActionButton(repeatIcon, !canRepeat, playerState.isRepeating == RepeatState.OFF) { actionRunCallback(ToggleRepeatAction::class.java) }
        val canShuffle = !buttonsDisabled && playerState.disabledActions[PlayerAction.TOGGLE_SHUFFLE] != true
        ActionButton(R.drawable.shuffle_regular_132, !canShuffle, playerState.isShuffling != true) { actionRunCallback(ToggleShuffleAction::class.java) }
        ActionButton(R.drawable.playlist_solid_132, buttonsDisabled) {
            val intent = Intent(context, QueueActivity::class.java)
            actionStartActivity(intent)
        }
        ActionButton(R.drawable.library_regular_132, buttonsDisabled) {
            val intent = Intent(context, PlayActivity::class.java)
            actionStartActivity(intent)
        }

        if (disabledActions[PlayerAction.SET_VOLUME] != true){
            val canMakeQuieter = playerState.volume?.let { v -> v > 0.0f } ?: false
            ActionButton(R.drawable.volume_low_regular_132, buttonsDisabled, !canMakeQuieter) { actionRunCallback(QuieterAction::class.java)}
        }
    }
}

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class PlayerDataType(
    private val apiClientProvider: APIClientProvider,
    private val thumbnailCache: ThumbnailCache,
    private val playerStateProvider: PlayerStateProvider
) : DataTypeImpl("karoo-spotify", "player") {
    private val glance = GlanceRemoteViews()

    // FIXME: Remove. Currently, the data field will permanently show "no sensor" if no data stream is provided
    override fun startStream(emitter: Emitter<StreamState>) {
        val job = CoroutineScope(Dispatchers.Default).launch {
            emitter.onNext(StreamState.Streaming(DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to 0.0))))
        }
        emitter.setCancellable {
            job.cancel()
        }
    }

    enum class PlayerSize {
        SINGLE_FIELD,
        SMALL,
        MEDIUM,
        FULL_PAGE; /* Full page */

        fun isLarge(): Boolean = (this == FULL_PAGE || this == MEDIUM)
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        Log.d(KarooSpotifyExtension.TAG, "Starting player view with $emitter - $config")

        val configJob = CoroutineScope(Dispatchers.Default).launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
            awaitCancellation()
        }

        val playerSize = if (config.gridSize.first <= 30){
            PlayerSize.SINGLE_FIELD
        } else if (config.gridSize.second <= 30){
            PlayerSize.SMALL
        } else if (config.gridSize.second < 60){
            PlayerSize.MEDIUM
        } else {
            PlayerSize.FULL_PAGE
        }

        val viewJob = CoroutineScope(Dispatchers.Default).launch {
            playerStateProvider.state
                .combine(apiClientProvider.getActiveAPIInstance()) { state, apiClient ->
                    state to apiClient
                }
                .collect { (appState, apiClient) ->
                // Log.d(KarooSpotifyExtension.TAG, "Updating player view with $appState")

                // TODO Is it worth it to download the higher resolution thumbnails? Can take ~ 20 s via companion...
                val thumbnailUrl = when(playerSize){
                    PlayerSize.SINGLE_FIELD, PlayerSize.SMALL -> null
                    PlayerSize.MEDIUM -> appState.isPlayingTrackThumbnailUrls?.last()
                    PlayerSize.FULL_PAGE -> appState.isPlayingTrackThumbnailUrls?.getOrNull(1) ?: appState.isPlayingTrackThumbnailUrls?.last()
                }

                val cachedThumbnail = thumbnailUrl?.let { url ->
                    CoroutineScope(Dispatchers.Default).launch {
                        thumbnailCache.ensureThumbnailIsInCache(thumbnailUrl)
                    }

                    thumbnailCache.getThumbnailFromInMemoryCache(url)
                }

                val buttonsDisabled = appState.commandPending || appState.requestPending > 0 || appState.isPlaying == null

                val result = if (playerSize.isLarge() || (!appState.isInOptionsMenu && (playerSize == PlayerSize.MEDIUM || playerSize == PlayerSize.SMALL))){
                    glance.compose(context, DpSize.Unspecified) {
                        Column(
                            modifier = GlanceModifier.fillMaxSize().padding(2.dp),
                            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
                        ) {
                            if (playerSize == PlayerSize.MEDIUM || playerSize == PlayerSize.FULL_PAGE) {
                                Row(modifier = GlanceModifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.Vertical.CenterVertically, horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                                    Image(ImageProvider(R.drawable.spotify_full_logo_rgb_black), "Spotify", modifier = GlanceModifier.height(40.dp).padding(2.dp),
                                        colorFilter = ColorFilter.tint(ColorProvider(Color.Black, Color.White)))
                                }
                            }

                            if (playerSize == PlayerSize.FULL_PAGE) {
                                val isThumbnailAvailable = appState.isPlayingTrackThumbnailUrls?.isNotEmpty() == true && cachedThumbnail != null

                                if (isThumbnailAvailable){
                                    cachedThumbnail?.let { thumbnail ->
                                        Image(ImageProvider(thumbnail), "Thumbnail", modifier = GlanceModifier.height(120.dp).padding(5.dp, 2.dp))
                                    }
                                } else {
                                    Image(ImageProvider(R.drawable.photo_album_regular_240), "Spotify", modifier = GlanceModifier.height(120.dp).padding(5.dp, 2.dp),
                                        colorFilter = ColorFilter.tint(ColorProvider(Color.Black, Color.White)))
                                }
                            }

                            Row(modifier = GlanceModifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.Vertical.Top) {
                                if (playerSize == PlayerSize.MEDIUM && appState.isPlayingTrackThumbnailUrls?.isNotEmpty() == true){
                                    cachedThumbnail?.let { thumbnail ->
                                        Image(ImageProvider(thumbnail), "Thumbnail", modifier = GlanceModifier.size(45.dp).padding(2.dp))
                                    }
                                }

                                val artistName = appState.isPlayingArtistName ?: appState.isPlayingShowName

                                Column(modifier = if(playerSize == PlayerSize.FULL_PAGE) GlanceModifier.width(200.dp) else GlanceModifier.width(150.dp)) {
                                    Text(appState.isPlayingTrackName ?: "Unknown", style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ColorProvider(Color.Black, Color.White)), maxLines = 1)
                                    Text(artistName ?: "Waiting for playback...", style = TextStyle(fontSize = 14.sp, color = ColorProvider(Color.Black, Color.White)), maxLines = 1)
                                }

                                Spacer(modifier = GlanceModifier.defaultWeight())

                                Row(verticalAlignment = Alignment.Vertical.CenterVertically, modifier = GlanceModifier.height(50.dp)) {
                                    PlayButton(appState, playerSize, buttonsDisabled)
                                }
                            }

                            Spacer(modifier = GlanceModifier.defaultWeight())

                            if (playerSize.isLarge()){
                                OptionsRows(context = context,
                                    playerState = appState,
                                    showToggle = false,
                                    buttonsDisabled = buttonsDisabled,
                                    disabledActions = appState.disabledActions
                                )

                                Spacer(modifier = GlanceModifier.defaultWeight())
                            }

                            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                                val progressText = appState.playProgressInMs?.let {
                                    val minutes = it / 60000
                                    val seconds = (it % 60000) / 1000

                                    String.format(null, "%d:%02d", minutes, seconds)
                                } ?: "0:00"

                                val lengthText = appState.currentTrackLengthInMs?.let {
                                    val minutes = it / 60000
                                    val seconds = (it % 60000) / 1000

                                    String.format(null, "%d:%02d", minutes, seconds)
                                } ?: "0:00"

                                Text(progressText, style = TextStyle(color = ColorProvider(Color.Black, Color.White), fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center, fontSize = 18.sp))

                                val progress = (appState.playProgressInMs?.toFloat() ?: 0.0f) / (appState.currentTrackLengthInMs?.toFloat() ?: 1.0f)
                                LinearProgressIndicator(progress = progress, modifier = GlanceModifier.defaultWeight().padding(horizontal = 5.dp, vertical = 2.dp).height(20.dp))

                                Text(lengthText, style = TextStyle(color = ColorProvider(Color.Black, Color.White), fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center, fontSize = 18.sp))
                            }
                        }
                    }
                } else if (playerSize == PlayerSize.MEDIUM || playerSize == PlayerSize.SMALL){
                    glance.compose(context, DpSize.Unspecified) {
                        Column(
                            modifier = GlanceModifier.fillMaxSize().padding(2.dp),
                            verticalAlignment = Alignment.Vertical.CenterVertically
                        ) {
                            OptionsRows(context = context,
                                playerState = appState,
                                showToggle = true,
                                buttonsDisabled = buttonsDisabled,
                                disabledActions = appState.disabledActions)
                        }
                    }
                } else {
                    glance.compose(context, DpSize.Unspecified) {
                        Column(
                            modifier = GlanceModifier.fillMaxSize().padding(2.dp),
                            verticalAlignment = Alignment.Vertical.CenterVertically
                        ) {
                            Text(appState.isPlayingTrackName ?: "Unknown", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorProvider(Color.Black, Color.White)), maxLines = 1)
                            Text(appState.isPlayingArtistName ?: "Waiting for playback...", style = TextStyle(fontSize = 14.sp, color = ColorProvider(Color.Black, Color.White)), maxLines = 1)

                            Row(
                                modifier = GlanceModifier.fillMaxWidth().padding(2.dp),
                                verticalAlignment = Alignment.Vertical.CenterVertically,
                                horizontalAlignment = Alignment.Horizontal.CenterHorizontally
                            ) {
                                PlayButton(appState, playerSize, buttonsDisabled)

                                Image(ImageProvider(R.drawable.skip_next_regular_132), "Next", modifier = GlanceModifier.size(40.dp).padding(10.dp).clickable(actionRunCallback(
                                    NextAction::class.java)), colorFilter = ColorFilter.tint(ColorProvider(Color.Black, Color.White)))
                            }

                            val progress = (appState.playProgressInMs?.toFloat() ?: 0.0f) / (appState.currentTrackLengthInMs?.toFloat() ?: 1.0f)
                            LinearProgressIndicator(progress = progress, modifier = GlanceModifier.fillMaxWidth().padding(2.dp).height(10.dp))
                        }
                    }
                }

                emitter.updateView(result.remoteViews)
            }
        }
        emitter.setCancellable {
            Log.d(KarooSpotifyExtension.TAG, "Stopping player view with $emitter")
            configJob.cancel()
            viewJob.cancel()
        }
    }
}