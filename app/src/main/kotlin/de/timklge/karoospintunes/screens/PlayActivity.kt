package de.timklge.karoospintunes.screens

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.timklge.karoospintunes.theme.AppTheme
import io.hammerhead.karooext.KarooSystemService
import org.koin.compose.KoinContext
import java.net.URLDecoder

@Composable
fun BrowseScreen(navController: NavHostController, karooSystemService: KarooSystemService) {
    Text("Not implemented yet.", modifier = Modifier.padding(5.dp).fillMaxSize(), textAlign = TextAlign.Center)
}

@Composable
fun NavScreen(karooSystemService: KarooSystemService, finish: () -> Unit, navController: NavHostController = rememberNavController()){
    NavHost(navController = navController, startDestination = "library") {
        composable(route = "playlists/{id}?name={name}&thumbnail={thumbnail}", arguments = listOf(
            navArgument("id") {
                type = NavType.StringType
                nullable = false
            },
            navArgument("name") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument("thumbnail") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )) { stack ->
            val playlistId = URLDecoder.decode(stack.arguments?.getString("id") ?: "", "UTF-8")
            val playlistName = URLDecoder.decode(stack.arguments?.getString("name") ?: "", "UTF-8")
            val playlistThumbnail = URLDecoder.decode(stack.arguments?.getString("thumbnail") ?: "", "UTF-8")

            if (playlistId != null){
                PlaylistScreen(
                    navController,
                    PlaylistScreenMode.Playlist(playlistId, playlistName, playlistThumbnail),
                    finish
                )
            } else {
                Text("Unknown playlist", modifier = Modifier.padding(5.dp))
            }
        }

        composable(route = "shows/{id}?name={name}&thumbnail={thumbnail}", arguments = listOf(
            navArgument("id") {
                type = NavType.StringType
                nullable = false
            },
            navArgument("name") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument("thumbnail") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )) { stack ->
            val showId = URLDecoder.decode(stack.arguments?.getString("id") ?: "", "UTF-8")
            val showName = URLDecoder.decode(stack.arguments?.getString("name") ?: "", "UTF-8")
            val showThumbnail = URLDecoder.decode(stack.arguments?.getString("thumbnail") ?: "", "UTF-8")

            if (showId != null){
                PlaylistScreen(
                    navController,
                    PlaylistScreenMode.Show(showId, showName, showThumbnail),
                    finish
                )
            } else {
                Text("Unknown playlist", modifier = Modifier.padding(5.dp))
            }
        }

        composable(route = "library"){
            PlayScreen(navController, karooSystemService)
        }

        composable(route = "saved-songs") {
            PlaylistScreen(navController, PlaylistScreenMode.Library, finish)
        }

        composable(route = "saved-episodes") {
            PlaylistScreen(navController, PlaylistScreenMode.SavedEpisodes, finish)
        }
    }
}

class PlayActivity : AppCompatActivity() {
    private val karooSystemService = KarooSystemService(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        karooSystemService.connect()

        intent.getStringExtra("spotify-uri")

        setContent {
            KoinContext {
                AppTheme {
                    NavScreen(karooSystemService, ::finish)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        karooSystemService.disconnect()
    }
}