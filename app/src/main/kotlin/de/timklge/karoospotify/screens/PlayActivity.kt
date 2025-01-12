package de.timklge.karoospotify.screens

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.timklge.karoospotify.theme.AppTheme
import io.hammerhead.karooext.KarooSystemService
import org.koin.compose.KoinContext

@Composable
fun PodcastsScreen(){
    Text("Not implemented yet.")
}

@Composable
fun BrowseScreen(navController: NavHostController, karooSystemService: KarooSystemService) {
    Text("Not implemented yet.")
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
            val playlistId = stack.arguments?.getString("id")
            val playlistName = stack.arguments?.getString("name")
            val playlistThumbnail = stack.arguments?.getString("thumbnail")

            if (playlistId != null){
                PlaylistScreen(navController, PlaylistScreenMode.Playlist(playlistId, playlistName, playlistThumbnail), karooSystemService, finish)
            } else {
                Text("Unknown playlist", modifier = Modifier.padding(5.dp))
            }
        }

        composable(route = "library"){
            PlayScreen(navController = navController, karooSystemService = karooSystemService)
        }

        composable(route = "saved-songs") {
            PlaylistScreen(navController, PlaylistScreenMode.Library, karooSystemService, finish)
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