package de.timklge.karoospintunes.datatypes

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import de.timklge.karoospintunes.KarooSystemServiceProvider
import de.timklge.karoospintunes.R
import de.timklge.karoospintunes.datatypes.actions.NextAction
import de.timklge.karoospintunes.screens.SpintuneSettings
import de.timklge.karoospintunes.spotify.APIClient
import de.timklge.karoospintunes.spotify.APIClientProvider
import de.timklge.karoospintunes.spotify.PlayerState
import de.timklge.karoospintunes.spotify.PlayerStateProvider
import de.timklge.karoospintunes.spotify.ThumbnailCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.util.Locale

fun formatMs(ms: Int?): String {
    return ms?.let {
        val hours = it / 3600000
        val minutes = (it % 3600000) / 60000
        val seconds = (it % 60000) / 1000

        if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    } ?: "0:00"
}

class PlayerViewProvider(private val apiClientProvider: APIClientProvider,
                         private val thumbnailCache: ThumbnailCache,
                         private val playerStateProvider: PlayerStateProvider,
                         private val context: Context,
                         private val karooSystemServiceProvider: KarooSystemServiceProvider
) {

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private val glance = GlanceRemoteViews()

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    fun provideView(playerSize: PlayerSize): Flow<RemoteViews> = flow {
        data class StreamData(
            val playerState: PlayerState,
            val apiClient: APIClient,
            val settings: SpintuneSettings
        )

        combine(playerStateProvider.state, apiClientProvider.getActiveAPIInstance(), karooSystemServiceProvider.streamSettings()) { state, apiClient, settings ->
                StreamData(state, apiClient, settings)
            }
            .collect { (appState, _, settings) ->
                val thumbnailUrl = when (playerSize) {
                    PlayerSize.SINGLE_FIELD, PlayerSize.SMALL -> null
                    PlayerSize.MEDIUM -> appState.isPlayingTrackThumbnailUrls?.last()
                    PlayerSize.FULL_PAGE -> {
                        if (settings.highResThumbnails) {
                            appState.isPlayingTrackThumbnailUrls?.getOrNull(1) ?: appState.isPlayingTrackThumbnailUrls?.last()
                        } else {
                            appState.isPlayingTrackThumbnailUrls?.last()
                        }
                    }
                }

                val cachedThumbnail = thumbnailUrl?.let { url ->
                    CoroutineScope(Dispatchers.IO).launch {
                        thumbnailCache.ensureThumbnailIsInCache(thumbnailUrl)
                    }

                    thumbnailCache.getThumbnailFromInMemoryCache(url)
                }

                val buttonsDisabled =
                    appState.commandPending || appState.requestPending > 0 || appState.isPlaying == null

                val result =
                    if (playerSize.isLarge() || (!appState.isInOptionsMenu && (playerSize == PlayerSize.MEDIUM || playerSize == PlayerSize.SMALL))) {
                        glance.compose(context, DpSize.Unspecified) {
                            Column(
                                modifier = GlanceModifier.fillMaxWidth().fillMaxHeight().padding(2.dp),
                                horizontalAlignment = Alignment.Horizontal.CenterHorizontally
                            ) {
                                if (playerSize == PlayerSize.MEDIUM || playerSize == PlayerSize.FULL_PAGE) {
                                    Row(
                                        modifier = GlanceModifier.fillMaxWidth().height(40.dp),
                                        verticalAlignment = Alignment.Vertical.CenterVertically,
                                        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
                                    ) {
                                        Image(
                                            ImageProvider(R.drawable.spotify_full_logo_rgb_black),
                                            "Spotify",
                                            modifier = GlanceModifier.height(40.dp).padding(2.dp),
                                            colorFilter = ColorFilter.tint(
                                                ColorProvider(
                                                    Color.Black,
                                                    Color.White
                                                )
                                            )
                                        )
                                    }
                                }

                                if (playerSize == PlayerSize.FULL_PAGE) {
                                    val isThumbnailAvailable =
                                        appState.isPlayingTrackThumbnailUrls?.isNotEmpty() == true && cachedThumbnail != null

                                    if (isThumbnailAvailable) {
                                        cachedThumbnail?.let { thumbnail ->
                                            Image(
                                                ImageProvider(thumbnail),
                                                "Thumbnail",
                                                modifier = GlanceModifier.fillMaxWidth().defaultWeight().padding(5.dp, 2.dp)
                                            )
                                        }
                                    } else {
                                        Image(
                                            ImageProvider(R.drawable.photo_album_regular_240),
                                            "Spotify",
                                            modifier = GlanceModifier.defaultWeight()
                                                .padding(5.dp, 2.dp),
                                            colorFilter = ColorFilter.tint(
                                                ColorProvider(
                                                    Color.Black,
                                                    Color.White
                                                )
                                            )
                                        )
                                    }
                                }

                                Row(
                                    modifier = GlanceModifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Vertical.Top
                                ) {
                                    if (playerSize == PlayerSize.MEDIUM && appState.isPlayingTrackThumbnailUrls?.isNotEmpty() == true) {
                                        cachedThumbnail?.let { thumbnail ->
                                            Image(
                                                ImageProvider(thumbnail),
                                                "Thumbnail",
                                                modifier = GlanceModifier.size(45.dp).padding(2.dp)
                                            )
                                        }
                                    }

                                    val artistName = if (appState.isPlayingArtistName.isNullOrBlank()) appState.isPlayingShowName else appState.isPlayingArtistName

                                    Column(
                                        modifier = GlanceModifier.defaultWeight()
                                    ) {
                                        Text(
                                            appState.isPlayingTrackName ?: (appState.error?.title ?: "Unknown"),
                                            style = TextStyle(
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ColorProvider(Color.Black, Color.White)
                                            ),
                                            maxLines = 1
                                        )

                                        Text(
                                            artistName ?: (appState.error?.message ?: "Waiting for playback..."),
                                            style = TextStyle(
                                                fontSize = 16.sp,
                                                color = ColorProvider(Color.Black, Color.White),
                                            ),
                                            maxLines = if (appState.error == null) 1 else 3,
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.Vertical.CenterVertically,
                                        modifier = GlanceModifier.height(50.dp)
                                    ) {
                                        PlayButton(appState, playerSize, buttonsDisabled)
                                    }
                                }

                                Spacer(modifier = GlanceModifier.height(2.dp))

                                if (playerSize.isLarge()) {
                                    OptionsRows(
                                        context = context,
                                        playerState = appState,
                                        showToggle = false,
                                        buttonsDisabled = buttonsDisabled,
                                        disabledActions = appState.disabledActions
                                    )

                                    Spacer(modifier = GlanceModifier.height(2.dp))
                                }

                                Row(
                                    modifier = GlanceModifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Vertical.CenterVertically
                                ) {
                                    val progressText = formatMs(appState.playProgressInMs)

                                    val lengthText = formatMs(appState.currentTrackLengthInMs)

                                    Text(
                                        progressText,
                                        style = TextStyle(
                                            color = ColorProvider(
                                                Color.Black,
                                                Color.White
                                            ),
                                            fontFamily = FontFamily.Monospace,
                                            textAlign = TextAlign.Center,
                                            fontSize = 18.sp
                                        )
                                    )

                                    val progress = (appState.playProgressInMs?.toFloat()
                                        ?: 0.0f) / (appState.currentTrackLengthInMs?.toFloat()
                                        ?: 1.0f)
                                    LinearProgressIndicator(
                                        progress = progress,
                                        modifier = GlanceModifier.defaultWeight()
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                            .height(20.dp),
                                        color = ColorProvider(
                                            Color(context.getColor(R.color.black)),
                                            Color(context.getColor(R.color.white))
                                        ),
                                        backgroundColor = ColorProvider(
                                            Color(context.getColor(R.color.lighterGray)),
                                            Color(context.getColor(R.color.darkGray))
                                        )
                                    )

                                    Text(
                                        lengthText,
                                        style = TextStyle(
                                            color = ColorProvider(
                                                Color.Black,
                                                Color.White
                                            ),
                                            fontFamily = FontFamily.Monospace,
                                            textAlign = TextAlign.Center,
                                            fontSize = 18.sp
                                        )
                                    )
                                }

                                if (playerSize.isLarge()) Spacer(modifier = GlanceModifier.height(5.dp))
                            }
                        }
                    } else if (playerSize == PlayerSize.MEDIUM || playerSize == PlayerSize.SMALL) {
                        glance.compose(context, DpSize.Unspecified) {
                            Column(
                                modifier = GlanceModifier.fillMaxSize().padding(2.dp),
                                verticalAlignment = Alignment.Vertical.CenterVertically
                            ) {
                                OptionsRows(
                                    context = context,
                                    playerState = appState,
                                    showToggle = true,
                                    buttonsDisabled = buttonsDisabled,
                                    disabledActions = appState.disabledActions
                                )
                            }
                        }
                    } else {
                        glance.compose(context, DpSize.Unspecified) {
                            Column(
                                modifier = GlanceModifier.fillMaxSize().padding(2.dp),
                                verticalAlignment = Alignment.Vertical.CenterVertically
                            ) {
                                Text(
                                    appState.isPlayingTrackName ?: "Unknown",
                                    style = TextStyle(
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorProvider(Color.Black, Color.White)
                                    ),
                                    maxLines = 1
                                )
                                Text(
                                    appState.isPlayingArtistName ?: "Waiting for playback...",
                                    style = TextStyle(
                                        fontSize = 14.sp,
                                        color = ColorProvider(Color.Black, Color.White)
                                    ),
                                    maxLines = 1
                                )

                                Row(
                                    modifier = GlanceModifier.fillMaxWidth().padding(2.dp),
                                    verticalAlignment = Alignment.Vertical.CenterVertically,
                                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally
                                ) {
                                    PlayButton(appState, playerSize, buttonsDisabled)

                                    Image(
                                        ImageProvider(R.drawable.skip_next_regular_132),
                                        "Next",
                                        modifier = GlanceModifier.size(40.dp).padding(10.dp)
                                            .clickable(
                                                actionRunCallback(
                                                    NextAction::class.java
                                                )
                                            ),
                                        colorFilter = ColorFilter.tint(
                                            ColorProvider(
                                                Color.Black,
                                                Color.White
                                            )
                                        )
                                    )
                                }

                                val progress = (appState.playProgressInMs?.toFloat()
                                    ?: 0.0f) / (appState.currentTrackLengthInMs?.toFloat() ?: 1.0f)
                                LinearProgressIndicator(
                                    progress = progress,
                                    modifier = GlanceModifier.fillMaxWidth().padding(2.dp)
                                        .height(10.dp)
                                )
                            }
                        }
                    }

                emit(result.remoteViews)
            }
    }

}