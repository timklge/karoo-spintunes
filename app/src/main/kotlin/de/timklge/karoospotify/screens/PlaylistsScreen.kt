package de.timklge.karoospotify.screens

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.navigation.NavHostController
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.compose.collectAsLazyPagingItems
import de.timklge.karoospotify.KarooSpotifyExtension
import de.timklge.karoospotify.R
import de.timklge.karoospotify.spotify.ThumbnailCache
import de.timklge.karoospotify.spotify.WebAPIClient
import de.timklge.karoospotify.spotify.model.PlaylistsResponse
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(navController: NavHostController, karooSystemService: KarooSystemService){
    val ctx = LocalContext.current
    val coroutineContext = rememberCoroutineScope()

    val thumbnailCache = koinInject<ThumbnailCache>()
    val webAPIClient = koinInject<WebAPIClient>()

    val playlistPager = remember {
        Pager(
            PagingConfig(
            pageSize = 50,
            maxSize = 200
        )
        ) {
            PlaylistsPagingSource(ctx, webAPIClient)
        }
    }
    val lazyPagingItems = playlistPager.flow.collectAsLazyPagingItems()

    val thumbnails = remember { mutableStateMapOf<String, ImageBitmap>() }

    var isRefreshing by remember { mutableStateOf(false) }
    val refreshState = rememberPullToRefreshState()

    LaunchedEffect(lazyPagingItems.itemSnapshotList) {
        Log.i(KarooSpotifyExtension.TAG, "Playlists loaded: ${lazyPagingItems.itemCount}")
        lazyPagingItems.itemSnapshotList.items.filter { playlist ->
            !playlist.images.isNullOrEmpty()
        }.forEach { playlist ->
            val thumbnailUrl = playlist.images?.last()?.url

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

    isRefreshing = lazyPagingItems.loadState.refresh == LoadState.Loading || lazyPagingItems.loadState.append == LoadState.Loading

    PullToRefreshBox(
        state = refreshState,
        isRefreshing = isRefreshing,
        onRefresh = {
            coroutineContext.launch {
                webAPIClient.clearCache<PlaylistsResponse>("playlists", ctx)
                lazyPagingItems.refresh()
            }
        }
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                    .fillMaxWidth()
                    .padding(5.dp)
                    .clickable { navController.navigate(route = "saved-songs") }
                ){
                    Image(
                        painterResource(R.drawable.library_regular_132), contentDescription = "Saved Songs", modifier = Modifier
                            .size(50.dp)
                            .padding(2.dp))
                    Text("Saved Songs", fontSize = 20.sp)
                }
            }

            items(count = lazyPagingItems.itemCount) { index ->
                val item = lazyPagingItems[index]

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                    .fillMaxWidth()
                    .padding(2.dp)
                    .clickable { navController.navigate(route = "playlists/${item?.id}?name=${item?.name}&thumbnail=${item?.images?.first()}") })
                {

                    val thumbnail = thumbnails[item?.images?.last()?.url]
                    if (thumbnail != null){
                        Image(thumbnail, contentDescription = item?.name, modifier = Modifier
                            .size(50.dp)
                            .padding(2.dp))
                    } else {
                        Spacer(modifier = Modifier
                            .size(50.dp)
                            .padding(2.dp))
                    }

                    Column(modifier = Modifier
                        .height(50.dp)
                        .weight(1.0f), verticalArrangement = Arrangement.Center) {
                        Text(item?.name ?: "Unknown", fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.0f))

                        val text = "${item?.tracks?.total ?: 0} tracks"
                        Text(text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}