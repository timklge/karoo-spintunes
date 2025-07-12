package de.timklge.karoospintunes.screens

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
import de.timklge.karoospintunes.KarooSpintunesExtension
import de.timklge.karoospintunes.R
import de.timklge.karoospintunes.spotify.ThumbnailCache
import de.timklge.karoospintunes.spotify.WebAPIClient
import de.timklge.karoospintunes.spotify.model.PlaylistsItem
import de.timklge.karoospintunes.spotify.model.PlaylistsResponse
import de.timklge.karoospintunes.spotify.model.ShowsResponse
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.net.URLEncoder

sealed class PlaylistsScreenMode {
    data object Playlists : PlaylistsScreenMode()
    data object Shows : PlaylistsScreenMode()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(navController: NavHostController, mode: PlaylistsScreenMode){
    val ctx = LocalContext.current
    val coroutineContext = rememberCoroutineScope()

    val thumbnailCache = koinInject<ThumbnailCache>()
    val webAPIClient = koinInject<WebAPIClient>()

    val playlistPager: Pager<Int, PlaylistsItem> = remember {
        Pager(PagingConfig(
            pageSize = 50,
            maxSize = 200
        )) {
            when (mode){
                PlaylistsScreenMode.Playlists -> PlaylistsPagingSource(ctx, webAPIClient)
                PlaylistsScreenMode.Shows -> ShowsPagingSource(ctx, webAPIClient)
            }
        }
    }
    val lazyPagingItems = playlistPager.flow.collectAsLazyPagingItems()

    val thumbnails = remember { mutableStateMapOf<String, ImageBitmap>() }

    var isRefreshing by remember { mutableStateOf(false) }
    val refreshState = rememberPullToRefreshState()

    LaunchedEffect(lazyPagingItems.itemSnapshotList) {
        Log.i(KarooSpintunesExtension.TAG, "Items loaded: ${lazyPagingItems.itemCount}")

        lazyPagingItems.itemSnapshotList.items.filter { playlist ->
            !playlist.getItemImages().isNullOrEmpty()
        }.forEach { playlist ->
            val thumbnailUrl = playlist.getItemImages()?.last()?.url

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
                when(mode){
                    PlaylistsScreenMode.Playlists -> {
                        webAPIClient.clearCache<PlaylistsResponse>("playlists", ctx)
                    }
                    PlaylistsScreenMode.Shows -> {
                        webAPIClient.clearCache<ShowsResponse>("shows", ctx)
                    }
                }
                lazyPagingItems.refresh()
            }
        }
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                val savedItemsId = when(mode){
                    PlaylistsScreenMode.Playlists -> "saved-songs"
                    PlaylistsScreenMode.Shows -> "saved-episodes"
                }
                val savedItemsName = when(mode){
                    PlaylistsScreenMode.Playlists -> "Saved Songs"
                    PlaylistsScreenMode.Shows -> "Saved Episodes"
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                    .fillMaxWidth()
                    .padding(5.dp)
                    .clickable { navController.navigate(route = savedItemsId) }
                ){
                    Image(
                        painterResource(R.drawable.library_regular_132), contentDescription = savedItemsName, modifier = Modifier
                            .size(50.dp)
                            .padding(2.dp))
                    Text(savedItemsName, fontSize = 20.sp)
                }
            }

            items(count = lazyPagingItems.itemCount) { index ->
                val item = lazyPagingItems[index]
                val targetPrefix = when(mode){
                    PlaylistsScreenMode.Playlists -> "playlists"
                    PlaylistsScreenMode.Shows -> "shows"
                }
                val itemCountSuffix = when (mode){
                    PlaylistsScreenMode.Playlists -> "tracks"
                    PlaylistsScreenMode.Shows -> "episodes"
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                    .fillMaxWidth()
                    .padding(2.dp)
                    .clickable {
                        val itemId = URLEncoder.encode(item?.getItemId(), "UTF-8")
                        val itemName = URLEncoder.encode(item?.getItemName(), "UTF-8")
                        val thumbnail = URLEncoder.encode(item?.getItemImages()?.first()?.url, "UTF-8")

                        navController.navigate(route = "$targetPrefix/${itemId}?name=${itemName}&thumbnail=${thumbnail}")
                    })
                {
                    val thumbnail = thumbnails[item?.getItemImages()?.last()?.url]
                    if (thumbnail != null){
                        Image(thumbnail, contentDescription = item?.getItemName(), modifier = Modifier
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
                        Text(item?.getItemName() ?: "Unknown", fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.0f))

                        val text = "${item?.getItemCount() ?: 0} $itemCountSuffix"
                        Text(text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}