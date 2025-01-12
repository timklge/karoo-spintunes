package de.timklge.karoospotify.screens

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import de.timklge.karoospotify.theme.AppTheme
import io.hammerhead.karooext.KarooSystemService
import org.koin.compose.KoinContext

class QueueActivity : AppCompatActivity() {
    val karooSystemService = KarooSystemService(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        karooSystemService.connect()

        setContent {
            AppTheme {
                KoinContext {
                    PlaylistScreen(navController = null, playlistMode = PlaylistScreenMode.Queue, karooSystemService = karooSystemService, ::finish)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        karooSystemService.disconnect()
    }
}