package de.timklge.karoospotify.spotify

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.util.Log
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.Subscription.LifecycleCallback
import com.spotify.protocol.types.ImageUri
import com.spotify.protocol.types.PlayerState
import de.timklge.karoospotify.KarooSpotifyExtension
import de.timklge.karoospotify.spotify.model.PlayRequest
import de.timklge.karoospotify.spotify.model.PlayRequestUris
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

sealed class LocalClientConnectionState {
    data class Failed(val message: String) : LocalClientConnectionState()
    data object Connecting : LocalClientConnectionState()
    data object Connected : LocalClientConnectionState()
    data object Idle : LocalClientConnectionState()
    data object NotInstalled : LocalClientConnectionState()
}

@Serializable
data class LocalClientError(val message: String)

class LocalClient(val context: Context) : APIClient {
    private var spotifyRemote: SpotifyAppRemote? = null
    private val redirectUri = "karoospotify://oauth2redirect"
    private val mutex = Mutex()

    private var connectionStateFlow: MutableStateFlow<LocalClientConnectionState> = MutableStateFlow(LocalClientConnectionState.Idle)
    val connectionState: Flow<LocalClientConnectionState> get() = connectionStateFlow.asStateFlow()

    suspend fun isInstalled(): Boolean = withContext(Dispatchers.Main) {
        SpotifyAppRemote.isSpotifyInstalled(context)
    }

    suspend fun readImage(uri: ImageUri): Bitmap? = withContext(Dispatchers.Main) {
        mutex.withLock {
            val imageApi = spotifyRemote?.imagesApi ?: error("Content API not available")

            suspendCoroutine {
                val future = imageApi.getImage(uri)

                future.setResultCallback { bitmap ->
                    it.resume(bitmap)
                }

                future.setErrorCallback { error ->
                    Log.e(KarooSpotifyExtension.TAG, "Failed to load image: $error")
                    it.resumeWithException(error)
                }
            }
        }
    }

    fun streamState(): Flow<PlayerState> = channelFlow {
        connectionState.distinctUntilChanged().collectLatest { connectionState ->
            if (connectionState is LocalClientConnectionState.Connected) {
                withContext(Dispatchers.Main) {
                    val subscription = spotifyRemote?.playerApi?.subscribeToPlayerState()
                        ?: error ("Player subscription failed")

                    callbackFlow {
                        subscription.setEventCallback { playerState ->
                            CoroutineScope(Dispatchers.Default).launch {
                                Log.d(KarooSpotifyExtension.TAG, "Player state: $playerState")
                                send(playerState)
                            }
                        }

                        subscription.setLifecycleCallback(object : LifecycleCallback {
                            override fun onStart() {
                                Log.d(KarooSpotifyExtension.TAG, "Player subscription started")
                            }

                            override fun onStop() {
                                Log.d(KarooSpotifyExtension.TAG, "Player subscription stopped")
                                close()
                            }
                        })

                        awaitClose {
                            Log.d(KarooSpotifyExtension.TAG, "Player subscription cancelled")

                            try {
                                subscription.cancel()
                            } catch(e: Throwable){
                                Log.w(KarooSpotifyExtension.TAG, "Failed to cancel player subscription", e)
                            }
                        }
                    }.collect {
                        send(it)
                    }
                }
            }
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.Main) {
        mutex.withLock {
            if (spotifyRemote != null){
                SpotifyAppRemote.disconnect(spotifyRemote)
                connectionStateFlow.emit(LocalClientConnectionState.Idle)
            }
            spotifyRemote = null
        }
    }

    suspend fun initialize(): LocalClientConnectionState = withContext(Dispatchers.Main) {
        val connectionParams = ConnectionParams.Builder(de.timklge.karoospotify.BuildConfig.SPOTIFY_CLIENT_ID)
            .setRedirectUri(redirectUri)
            .showAuthView(true)
            .build()

        mutex.withLock {
            spotifyRemote?.let { remote ->
                if (remote.isConnected) {
                    return@withLock LocalClientConnectionState.Connected
                }
            }

            if (!SpotifyAppRemote.isSpotifyInstalled(context)) {
                connectionStateFlow.emit(LocalClientConnectionState.NotInstalled)
                return@withLock LocalClientConnectionState.NotInstalled
            }

            suspendCoroutine { continuation ->
                var resumed = false

                CoroutineScope(Dispatchers.Default).launch {
                    connectionStateFlow.emit(LocalClientConnectionState.Connecting)
                }

                SpotifyAppRemote.connect(context, connectionParams, object : Connector.ConnectionListener {
                    override fun onConnected(appRemote: SpotifyAppRemote) {
                        resumed = true

                        spotifyRemote = appRemote

                        CoroutineScope(Dispatchers.Default).launch {
                            Log.d(KarooSpotifyExtension.TAG, "Connected to Spotify")
                            connectionStateFlow.emit(LocalClientConnectionState.Connected)
                        }

                        continuation.resume(LocalClientConnectionState.Connected)
                    }

                    override fun onFailure(throwable: Throwable) {
                        var message = throwable.message ?: "Unknown error"
                        try {
                            message = de.timklge.karoospotify.jsonWithUnknownKeys.decodeFromString<LocalClientError>(message).message
                        } catch(e: Throwable){
                            Log.w(KarooSpotifyExtension.TAG, "Failed to parse error message", e)
                        }
                        val connectionState = LocalClientConnectionState.Failed(message)

                        CoroutineScope(Dispatchers.Default).launch {
                            connectionStateFlow.emit(connectionState)
                        }

                        if (!resumed) continuation.resume(connectionState)

                        resumed = true
                    }
                })
            }
        }
    }

    override suspend fun pause() {
        mutex.withLock {
            spotifyRemote?.playerApi?.pause()
        }
    }

    override suspend fun play(playRequest: PlayRequest?) {
        mutex.withLock {
            spotifyRemote?.playerApi?.play(playRequest?.uris?.first() ?: "")
        }
    }

    override suspend fun playUris(playRequest: PlayRequestUris) {
        mutex.withLock {
            spotifyRemote?.playerApi?.play(playRequest.uris?.first() ?: error("No URI provided"))
        }
    }

    override suspend fun next() {
        mutex.withLock {
            spotifyRemote?.playerApi?.skipNext()
        }
    }

    override suspend fun previous() {
        mutex.withLock {
            spotifyRemote?.playerApi?.skipPrevious()
        }
    }

    override suspend fun seek(positionInMs: Int) {
        mutex.withLock {
            spotifyRemote?.playerApi?.seekTo(positionInMs.toLong())
        }
    }

    override suspend fun toggleShuffle(shuffle: Boolean) {
        mutex.withLock {
            spotifyRemote?.playerApi?.setShuffle(shuffle)
        }
    }

    override suspend fun toggleRepeat(repeat: RepeatState) {
        mutex.withLock {
            spotifyRemote?.playerApi?.setRepeat(repeat.num)
        }
    }

    fun getVolume(): Float {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
        val volumeIndex = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        return (volumeIndex / maxVolume).coerceIn(0f, 1f)
    }

    override suspend fun setVolume(volume: Float) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volumeLevel = volume.coerceIn(0f, 1f)
        val volumeIndex = (maxVolume * volumeLevel).toInt().coerceIn(0, maxVolume)

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeIndex, 0)
    }

    override suspend fun addToQueue(uri: String) {
        mutex.withLock {
            spotifyRemote?.playerApi?.queue(uri)
        }
    }

    suspend fun getPlayerState(): PlayerState? {
        return mutex.withLock {
            spotifyRemote?.playerApi?.playerState?.let { playerStateResult ->
                suspendCoroutine { continuation ->
                    playerStateResult.setResultCallback { playerState ->
                        continuation.resume(playerState)
                    }
                    playerStateResult.setErrorCallback { error ->
                        Log.e(KarooSpotifyExtension.TAG, "Failed to get player state: $error")
                        continuation.resumeWithException(error)
                    }
                }
            }
        }
    }

    suspend fun addTrackToLibrary(ctx: Context, selectedTrackUris: List<String>) {
        return mutex.withLock {
            selectedTrackUris.forEach { uri ->
                spotifyRemote?.userApi?.addToLibrary(uri)
            }
        }
    }
}