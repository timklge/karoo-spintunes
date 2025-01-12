package de.timklge.karoospotify

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import de.timklge.karoospotify.screens.MainScreen
import de.timklge.karoospotify.theme.AppTheme
import org.koin.compose.KoinContext

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                KoinContext {
                    MainScreen()
                }
            }
        }
    }
}
