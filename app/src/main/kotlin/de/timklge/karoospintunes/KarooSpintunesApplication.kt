package de.timklge.karoospintunes

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import de.timklge.karoospintunes.KarooSpintunesExtension.Companion.TAG
import de.timklge.karoospintunes.auth.HttpException
import de.timklge.karoospintunes.auth.OAuth2Client
import de.timklge.karoospintunes.datatypes.PlayerDataType
import de.timklge.karoospintunes.datatypes.PlayerViewProvider
import de.timklge.karoospintunes.screens.SpintuneSettings
import de.timklge.karoospintunes.spotify.APIClientProvider
import de.timklge.karoospintunes.spotify.LocalClient
import de.timklge.karoospintunes.spotify.PlayerInPreviewModeProvider
import de.timklge.karoospintunes.spotify.PlayerStateProvider
import de.timklge.karoospintunes.spotify.ThumbnailCache
import de.timklge.karoospintunes.spotify.WebAPIClient
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.InRideAlert
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import kotlin.math.roundToInt

class KarooSystemServiceProvider(private val context: Context) {
    val karooSystemService: KarooSystemService = KarooSystemService(context)

    private val _connectionState = MutableStateFlow(false)
    val connectionState = _connectionState.asStateFlow()

    init {
        karooSystemService.connect { connected ->
            if (connected) {
                Log.d(TAG, "Connected to Karoo system")
            }

            CoroutineScope(Dispatchers.IO).launch {
                _connectionState.emit(connected)
            }
        }
    }

    val settingsKey = stringPreferencesKey("settings")

    fun readSettings(settingsJson: String?): SpintuneSettings {
        return if (settingsJson != null){
            jsonWithUnknownKeys.decodeFromString<SpintuneSettings>(settingsJson)
        } else {
            val defaultSettings = jsonWithUnknownKeys.decodeFromString<SpintuneSettings>(
                SpintuneSettings.defaultSettings)

            defaultSettings.copy()
        }
    }

    suspend fun saveSettings(function: (settings: SpintuneSettings) -> SpintuneSettings) {
        context.dataStore.edit { t ->
            val settings = readSettings(t[settingsKey])
            val newSettings = function(settings)
            t[settingsKey] = jsonWithUnknownKeys.encodeToString(newSettings)
        }
    }

    fun streamSettings(): Flow<SpintuneSettings> {
        return context.dataStore.data.map { settingsJson ->
            try {
                readSettings(settingsJson[settingsKey])
            } catch(e: Throwable){
                Log.e(TAG, "Failed to read preferences", e)
                jsonWithUnknownKeys.decodeFromString<SpintuneSettings>(SpintuneSettings.defaultSettings)
            }
        }.distinctUntilChanged()
    }

    fun streamRideState(): Flow<RideState> {
        return callbackFlow {
            val listenerId = karooSystemService.addConsumer { rideState: RideState ->
                trySendBlocking(rideState)
            }
            awaitClose {
                karooSystemService.removeConsumer(listenerId)
            }
        }
    }

    fun streamUserProfile(): Flow<UserProfile> {
        return callbackFlow {
            val listenerId = karooSystemService.addConsumer { userProfile: UserProfile ->
                trySendBlocking(userProfile)
            }
            awaitClose {
                karooSystemService.removeConsumer(listenerId)
            }
        }
    }

    fun streamSpeed(): Flow<Int> {
        return karooSystemService.streamDataFlow(DataType.Type.SMOOTHED_3S_AVERAGE_SPEED)
            .map { (it as? StreamState.Streaming)?.dataPoint?.singleValue ?: 0.0 }
            .map { it.roundToInt() } // Round to nearest meter per second
            .distinctUntilChanged()
            .throttle(1_000L) // Throttle to 1 second
    }

    fun showError(header: String, message: String, e: Throwable? = null) {
        var errorMessageString = message

        if (e is HttpException){
            try {
                val deserializedError = message.let { jsonWithUnknownKeys.decodeFromString<WebAPIErrorResponse>(it) }
                errorMessageString = "${deserializedError.error.status} - ${deserializedError.error.message}"
            } catch(e: Throwable) {
                Log.w(TAG, "Expected error to be in WebAPIErrorResponse format, but was not: ${e.message}")
            }
        }

        Log.e(TAG, "Error: $header - $errorMessageString", e)

        karooSystemService.dispatch(InRideAlert(id = "error-${System.currentTimeMillis()}", icon = R.drawable.spotify, title = header, detail = errorMessageString, autoDismissMs = 10_000L, backgroundColor = R.color.hRed, textColor = R.color.black))
    }
}

val appModule = module {
    singleOf(::KarooSystemServiceProvider)
    singleOf(::APIClientProvider)
    singleOf(::OAuth2Client)
    singleOf(::PlayerDataType)
    singleOf(::WebAPIClient)
    singleOf(::ThumbnailCache)
    singleOf(::AutoVolume)
    singleOf(::LocalClient)
    singleOf(::PlayerStateProvider)
    singleOf(::PlayerInPreviewModeProvider)
    singleOf(::PlayerViewProvider)
    singleOf(::KarooSpintunesServices)
}

class KarooSpintunesApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@KarooSpintunesApplication)
            modules(appModule)
        }
    }
}