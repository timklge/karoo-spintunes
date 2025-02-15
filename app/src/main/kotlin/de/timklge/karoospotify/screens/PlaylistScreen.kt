package de.timklge.karoospotify.screens

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.compose.collectAsLazyPagingItems
import de.timklge.karoospotify.KarooSpotifyExtension
import de.timklge.karoospotify.R
import de.timklge.karoospotify.spotify.APIClientProvider
import de.timklge.karoospotify.spotify.LocalClient
import de.timklge.karoospotify.spotify.PlayerStateProvider
import de.timklge.karoospotify.spotify.ThumbnailCache
import de.timklge.karoospotify.spotify.WebAPIClient
import de.timklge.karoospotify.spotify.model.EpisodesResponse
import de.timklge.karoospotify.spotify.model.LibraryItemsResponse
import de.timklge.karoospotify.spotify.model.Offset
import de.timklge.karoospotify.spotify.model.PlayRequest
import de.timklge.karoospotify.spotify.model.PlayRequestUris
import de.timklge.karoospotify.spotify.model.PlaylistItemsResponse
import de.timklge.karoospotify.spotify.model.TrackObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

sealed class PlaylistScreenMode {
    data class Playlist(val playlistId: String, val playlistName: String?, val playlistThumbnail: String?) : PlaylistScreenMode()
    data class Show(val showId: String, val showName: String?, val showThumbnail: String?) : PlaylistScreenMode()
    data object Library : PlaylistScreenMode()
    data object SavedEpisodes : PlaylistScreenMode()
    data object Queue : PlaylistScreenMode()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistScreen(
    navController: NavHostController?,
    playlistMode: PlaylistScreenMode,
    finish: () -> Unit
) {
    val ctx = LocalContext.current
    val coroutineContext = rememberCoroutineScope()
    var selectionMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<TrackObject>() }

    val webApiClient = koinInject<WebAPIClient>()
    val thumbnailCache = koinInject<ThumbnailCache>()
    val apiClientProvider = koinInject<APIClientProvider>()
    val playerStateProvider = koinInject<PlayerStateProvider>()

    // TODO Should queue be disabled in local mode? No, it should be enabled and still use the web API as its unsupported with the android SDK
    val apiClient by apiClientProvider.getActiveAPIInstance().collectAsStateWithLifecycle(initialValue = null)

    var playStarted by remember { mutableStateOf(false) }

    suspend fun startPlayback(item: TrackObject?){
        if (playStarted) return
        playStarted = true

        try {
            if (playlistMode is PlaylistScreenMode.Playlist && apiClient is WebAPIClient){
                val playRequest = PlayRequest(
                    contextUri = "spotify:playlist:${playlistMode.playlistId}",
                    offset = Offset(uri = item?.getDefinedTrack()?.uri)
                )

                webApiClient.play(playRequest)
            } else {
                val playRequest = PlayRequestUris(
                    uris = listOf(item?.getDefinedTrack()?.uri ?: "")
                )

                webApiClient.playUris(playRequest)
            }

            playerStateProvider.update { playerState ->
                playerState.copy(
                    commandPending = true
                )
            }

            finish()
        } finally {
            playStarted = false
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = {
            Text(when (playlistMode) {
                is PlaylistScreenMode.Playlist -> playlistMode.playlistName ?: "Unknown"
                PlaylistScreenMode.Library -> "Library"
                PlaylistScreenMode.Queue -> "Queue"
                PlaylistScreenMode.SavedEpisodes -> "Episodes"
                is PlaylistScreenMode.Show -> "Show"
            })
        }) },
        floatingActionButton = {
            if (playlistMode is PlaylistScreenMode.Playlist){
                Button(modifier = Modifier.defaultMinSize(minWidth = 56.dp, minHeight = 56.dp), shape = CircleShape, enabled = !playStarted, onClick = {
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            playStarted = true

                            if (apiClient is LocalClient){
                                val playlistUrl = "spotify:playlist:${playlistMode.playlistId}"

                                apiClient?.playUris(PlayRequestUris(listOf(playlistUrl)))
                            } else {
                                val playRequest = PlayRequest(contextUri = "spotify:playlist:${playlistMode.playlistId}")

                                webApiClient.play(playRequest)
                            }

                            finish()
                        } finally {
                            playStarted = false
                        }
                    }
                }) {
                    Icon(painter = painterResource(id = R.drawable.play_regular_132), contentDescription = "Play")
                }
            }
        },
        bottomBar = {
            if (selectionMode){
                BottomAppBar {
                    if (apiClient is WebAPIClient || selected.size == 1){
                        Icon(modifier = Modifier
                            .size(50.dp)
                            .padding(horizontal = 10.dp)
                            .clickable {
                                if (playStarted) return@clickable

                                CoroutineScope(Dispatchers.Main).launch {
                                    try {
                                        playStarted = true

                                        val selectedUri = selected.mapNotNull { it.getDefinedTrack()?.uri }

                                        if (playlistMode is PlaylistScreenMode.Playlist) {
                                            val contextUri =
                                                "spotify:playlist:${playlistMode.playlistId}"

                                            apiClient?.play(
                                                PlayRequest(
                                                    uris = selectedUri,
                                                    contextUri = contextUri
                                                )
                                            )
                                        } else {
                                            apiClient?.playUris(PlayRequestUris(uris = selectedUri))
                                        }

                                        finish()
                                    } finally {
                                        playStarted = false
                                    }
                                }
                            }, painter = painterResource(id = R.drawable.play_regular_132), contentDescription = "Play")
                    }

                    Icon(modifier = Modifier
                        .size(50.dp)
                        .padding(horizontal = 10.dp)
                        .clickable {
                            if (playStarted) return@clickable

                            CoroutineScope(Dispatchers.Main).launch {
                                try {
                                    playStarted = true
                                    val selectedUri = selected.mapNotNull { it.getDefinedTrack()?.uri }

                                    selectedUri.forEach { uri ->
                                        apiClient?.addToQueue(uri)
                                    }

                                    finish()
                                } finally {
                                    playStarted = false
                                }
                            }
                        }, painter = painterResource(id = R.drawable.add_to_queue_regular_132), contentDescription = "Add to queue")

                    BottomAppBar {
                        Icon(modifier = Modifier
                            .size(50.dp)
                            .padding(horizontal = 10.dp)
                            .clickable {
                                CoroutineScope(Dispatchers.Main).launch {
                                    try {
                                        playStarted = true

                                        when (apiClient) {
                                            is WebAPIClient -> {
                                                val selectedTrackIds =
                                                    selected.mapNotNull { it.getDefinedTrack()?.id }

                                                (apiClient as WebAPIClient).addTrackToLibrary(
                                                    selectedTrackIds
                                                )
                                            }

                                            is LocalClient -> {
                                                val selectedTrackUris =
                                                    selected.mapNotNull { it.getDefinedTrack()?.uri }

                                                (apiClient as LocalClient).addTrackToLibrary(
                                                    ctx,
                                                    selectedTrackUris
                                                )
                                            }

                                            else -> {
                                                error("Unknown API client")
                                            }
                                        }

                                        finish()
                                    } finally {
                                        playStarted = false
                                    }
                                }
                            }, painter = painterResource(id = R.drawable.like_regular_132), contentDescription = "Add to library")
                    }
                }
            }
    }, content = {
            if (apiClient != null){
                val playlistPager = remember {
                    Pager(
                        PagingConfig(
                            pageSize = 50,
                            maxSize = 100,
                            prefetchDistance = 20
                        )
                    ) {
                        when (playlistMode) {
                            is PlaylistScreenMode.Playlist -> {
                                val playlistId = playlistMode.playlistId
                                PlaylistPagingSource(playlistId, ctx, webApiClient)
                            }
                            PlaylistScreenMode.Library -> LibraryPagingSource(ctx, webApiClient)
                            PlaylistScreenMode.Queue -> QueuePagingSource(ctx, apiClient!!, webApiClient)
                            PlaylistScreenMode.SavedEpisodes -> SavedEpisodesPagingSource(ctx, webApiClient)
                            is PlaylistScreenMode.Show -> ShowPagingSource(playlistMode.showId, ctx, webApiClient)
                        }
                    }
                }
                val lazyPagingItems = playlistPager.flow.collectAsLazyPagingItems()

                val thumbnails = remember { mutableStateMapOf<String, ImageBitmap>() }
                var isRefreshing by remember { mutableStateOf(false) }
                val refreshState = rememberPullToRefreshState()

                LaunchedEffect(lazyPagingItems.loadState) {
                    isRefreshing = lazyPagingItems.loadState.refresh is androidx.paging.LoadState.Loading || lazyPagingItems.loadState.append is androidx.paging.LoadState.Loading
                    Log.i(KarooSpotifyExtension.TAG, "Load state: ${lazyPagingItems.loadState} - $isRefreshing")
                }

                LaunchedEffect(lazyPagingItems.itemSnapshotList) {
                    Log.i(KarooSpotifyExtension.TAG, "Songs loaded: ${lazyPagingItems.itemCount}")
                    lazyPagingItems.itemSnapshotList.items.filter { track ->
                        !track.getDefinedTrack()?.images.isNullOrEmpty() || !track.getDefinedTrack()?.album?.images.isNullOrEmpty()
                    }.forEach { track ->
                        val thumbnailUrl = track.getDefinedTrack()?.images?.last()?.url ?: track.getDefinedTrack()?.album?.images?.last()?.url

                        if (thumbnailUrl != null){
                            coroutineContext.launch {
                                val thumbnail = thumbnailCache.getThumbnail(thumbnailUrl)

                                if (thumbnail != null){
                                    thumbnails[thumbnailUrl] = thumbnail.asImageBitmap()
                                }
                            }
                        }
                    }
                }

                PullToRefreshBox(
                    state = refreshState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(it),
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        coroutineContext.launch {
                            when (playlistMode){
                                PlaylistScreenMode.Library -> webApiClient.clearCache<LibraryItemsResponse>("library", ctx)
                                is PlaylistScreenMode.Playlist -> webApiClient.clearCache<PlaylistItemsResponse>("playlist_items_${playlistMode.playlistId}", ctx)
                                PlaylistScreenMode.Queue -> Unit
                                PlaylistScreenMode.SavedEpisodes -> Unit // TODO add to cache?
                                is PlaylistScreenMode.Show -> webApiClient.clearCache<EpisodesResponse>("episodes_${playlistMode.showId}", ctx)
                            }
                            lazyPagingItems.refresh()
                        }
                    }
                ) {
                    LazyColumn(modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)) {

                        if (playlistMode == PlaylistScreenMode.Queue && apiClient is LocalClient){
                            item {
                                Text("Queue list is not updated when local Spotify is used and there is no active WiFi connection.", modifier = Modifier.padding(5.dp))
                            }
                        }

                        items(count = lazyPagingItems.itemCount) { index ->
                            val item = lazyPagingItems[index]

                            fun toggleSelect(){
                                if (item != null){
                                    if (selected.contains(item)){
                                        selected.remove(item)
                                        if (selected.isEmpty()) selectionMode = false
                                    } else {
                                        if (selected.size >= 5) selected.removeAt(0)
                                        selected.add(item)
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                                .fillMaxWidth()
                                .padding(2.dp)
                                .combinedClickable(
                                    onLongClick = {
                                        selectionMode = !selectionMode

                                        selected.clear()
                                        if (item != null && !selected.contains(item)) {
                                            selected.add(item)
                                        }
                                    }
                                ) {
                                    if (selectionMode) {
                                        toggleSelect()
                                    } else {
                                        coroutineContext.launch {
                                            startPlayback(item)
                                        }
                                    }
                                })
                            {

                                val thumbnail = thumbnails[item?.getDefinedTrack()?.images?.last()?.url ?: item?.getDefinedTrack()?.album?.images?.last()?.url]
                                if (thumbnail != null){
                                    Image(thumbnail, contentDescription = item?.getDefinedTrack()?.name, modifier = Modifier
                                        .size(50.dp)
                                        .padding(2.dp))
                                } else {
                                    Spacer(modifier = Modifier
                                        .size(50.dp)
                                        .padding(2.dp))
                                }

                                val isEpisode = item?.getDefinedTrack()?.type == "episode"

                                Column(modifier = Modifier
                                    //.height(if(isEpisode) 100.dp else 50.dp)
                                    //.weight(1.0f)
                                    , verticalArrangement = Arrangement.Center) {

                                    if (isEpisode){
                                        Text(item?.getDefinedTrack()?.name ?: "Unknown", fontSize = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    } else {
                                        Text(item?.getDefinedTrack()?.name ?: "Unknown", fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }

                                    Row {
                                        val subLabel = if (isEpisode){
                                            val releaseDate = item?.getDefinedTrack()?.releaseDate
                                            // val releaseDatePrecision = item.getDefinedTrack()?.releaseDatePrecision

                                            releaseDate // TODO pretty format?
                                        } else {
                                            item?.getDefinedTrack()?.artists?.joinToString(", ") { it.name ?: "" }
                                        }

                                        Text(subLabel  ?: "Unknown", fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))

                                        val lengthText = item?.getDefinedTrack()?.durationMs?.let { duration ->
                                            val minutes = duration / 60000
                                            val seconds = (duration % 60000) / 1000

                                            String.format(null, "%d:%02d", minutes, seconds)
                                        }

                                        if (!selectionMode){
                                            if (lengthText != null){
                                                Text(lengthText, fontSize = 15.sp, maxLines = 1, modifier = Modifier.wrapContentWidth())
                                            }
                                        }
                                    }
                                }

                                if (selectionMode){
                                    val isSelected = selected.contains(item)

                                    Checkbox(checked = isSelected, onCheckedChange = { toggleSelect() })
                                }
                            }
                        }
                    }
                }
        }
    })
}