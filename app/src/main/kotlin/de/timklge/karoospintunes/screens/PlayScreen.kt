package de.timklge.karoospintunes.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.timklge.karoospintunes.R
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.launch

@Composable
fun PlayScreen(navController: NavHostController, karooSystemService: KarooSystemService, finish: () -> Unit) {
    val ctx = LocalContext.current
    val selectedTabIndex by remember { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    val pagerState = rememberPagerState(pageCount = { 2 })

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Image(
                painter = painterResource(R.drawable.spotify_full_logo_rgb_black),
                contentDescription = "Spotify Logo",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(5.dp)
            )

            // TopAppBar(title = { Text("Play") })
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) {
                when (it) {
                    0 -> {
                        PlaylistsScreen(navController, PlaylistsScreenMode.Playlists)
                    }
                    /* 1 -> {
                        BrowseScreen(navController, karooSystemService)
                    } */
                    1 -> {
                        PlaylistsScreen(navController, PlaylistsScreenMode.Shows)
                    }
                }
            }

            TabRow(selectedTabIndex = pagerState.currentPage, modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
                divider = { }, // Remove default divider
                indicator = { tabPositions ->
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .tabIndicatorOffset(tabPositions[pagerState.currentPage])
                                .align(androidx.compose.ui.Alignment.TopStart)
                                .height(2.dp)
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            ) {

                Tab(selected = selectedTabIndex == 0, text = { Text("Playlist") }, icon = { Icon(
                    painterResource(R.drawable.playlist_solid_132), contentDescription = "Playlists", modifier = Modifier
                        .size(30.dp)
                        .padding(2.dp)
                ) }, onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } })

                /* Tab(selected = selectedTabIndex == 1, text = { Text("Browse") }, icon = { Icon(
                    painterResource(R.drawable.spotify), contentDescription = "Browse", modifier = Modifier
                        .size(30.dp)
                        .padding(2.dp)
                ) }, onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } }) */

                Tab(selected = selectedTabIndex == 1, text = { Text("Pods") }, icon = { Icon(
                    painterResource(R.drawable.podcast_regular_132), contentDescription = "Podcasts", modifier = Modifier
                        .size(30.dp)
                        .padding(2.dp)
                ) }, onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } })
            }
        }

        Image(
            painter = painterResource(id = R.drawable.back),
            contentDescription = "Back",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 10.dp)
                .size(54.dp)
                .clickable {
                    finish()
                }
        )
    }
}