package de.timklge.karoospintunes

import android.util.Log
import de.timklge.karoospintunes.datatypes.PlayerDataType
import de.timklge.karoospintunes.spotify.APIClientProvider
import de.timklge.karoospintunes.spotify.LocalClient
import de.timklge.karoospintunes.spotify.PlaybackType
import de.timklge.karoospintunes.spotify.PlayerAction
import de.timklge.karoospintunes.spotify.PlayerStateProvider
import de.timklge.karoospintunes.spotify.RepeatState
import de.timklge.karoospintunes.spotify.ThumbnailCache
import de.timklge.karoospintunes.spotify.WebAPIClient
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.extension.KarooExtension
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import kotlin.time.TimeSource

class KarooSpintunesExtension : KarooExtension("karoo-spintunes", BuildConfig.VERSION_NAME) {
    companion object {
        const val TAG = "karoo-spintunes"
    }

    private val servicesProvider: KarooSpintunesServices = KarooSpintunesServices(
        get(), get(), get(), get(), get(), get(), get(), get())
    private val playerDataType: PlayerDataType by inject()

    override val types: List<DataTypeImpl> = listOf(playerDataType)

    override fun onCreate() {
        servicesProvider.startJobs()
        super.onCreate()
    }

    override fun onDestroy() {
        servicesProvider.cancelJobs()
        super.onDestroy()
    }
}