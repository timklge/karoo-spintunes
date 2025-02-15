package de.timklge.karoospotify.spotify

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.core.content.ContextCompat.getSystemService
import com.spotify.protocol.types.ImageUri
import de.timklge.karoospotify.KarooSpotifyExtension.Companion.TAG
import de.timklge.karoospotify.KarooSystemServiceProvider
import de.timklge.karoospotify.makeHttpRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.time.TimeSource

class ThumbnailCache(
    private val karooSystemServiceProvider: KarooSystemServiceProvider,
    private val context: Context,
    private val apiClientProvider: APIClientProvider,
    private val playerStateProvider: PlayerStateProvider
) {
    private val thumbnailCacheDir = File(context.cacheDir, "thumbnails")
    private var enableThumbnailDownloadsWhenNotOnWifi = true

    init {
        if (!thumbnailCacheDir.exists()) {
            thumbnailCacheDir.mkdirs()
        }

        CoroutineScope(Dispatchers.IO).launch {
            karooSystemServiceProvider.streamSettings().collect { settings ->
                lock.withLock {
                    enableThumbnailDownloadsWhenNotOnWifi = settings.downloadThumbnailsViaCompanion
                }
            }
        }
    }

    private val lock: Mutex = Mutex()
    private val thumbnailCacheSize = 1_000

    private val connectivityManager = getSystemService(context, ConnectivityManager::class.java) as ConnectivityManager

    suspend fun clearCache() {
        lock.withLock {
            val files = thumbnailCacheDir.listFiles()?.sortedBy { it.lastModified() }

            if (files != null && files.size >= thumbnailCacheSize){
                val deleteCount = files.size - thumbnailCacheSize
                Log.i(TAG, "${files.size} files in thumbnail cache, deleting oldest $deleteCount files")

                files.take(deleteCount).forEach { it.delete() }
            } else {
                Log.i(TAG, "${files?.size ?: 0} files in thumbnail cache, no cleanup needed")
            }
        }
    }

    suspend fun getThumbnail(url: String): Bitmap? {
        val fileName = java.util.Base64.getEncoder().encodeToString(url.encodeToByteArray())
        val file = File(thumbnailCacheDir, fileName)

        return lock.withLock {
            try {
                if (file.exists()) {
                    file.setLastModified(System.currentTimeMillis())

                    BitmapFactory.decodeFile(file.absolutePath)
                } else {
                    val apiClient = apiClientProvider.getActiveAPIInstance().first()
                    val isLocalClient = apiClient is LocalClient

                    val start = TimeSource.Monotonic.markNow()

                    val wifiStatus = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false

                    val bitmap = if (!isLocalClient){
                        val downloadEnabled = wifiStatus || enableThumbnailDownloadsWhenNotOnWifi

                        if (downloadEnabled) {
                            val response = karooSystemServiceProvider.karooSystemService.makeHttpRequest("GET", url, false).singleOrNull()

                            val bytes = response?.body ?: error("Failed to get thumbnail data: ${response?.statusCode}")
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                            file.writeBytes(bytes)

                            val runtime = TimeSource.Monotonic.markNow() - start
                            Log.i(TAG, "Updated thumbnail cache with $url in $runtime (WiFi: $wifiStatus), ${bytes.size} bytes")

                            bitmap
                        } else {
                            Log.d(TAG, "Not downloading thumbnail for $url")
                            null
                        }
                    } else {
                        val bitmap = (apiClient as LocalClient).readImage(ImageUri(url)) ?: return@withLock null

                        ByteArrayOutputStream().use { outputStream ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                            val bytes = outputStream.toByteArray()
                            file.writeBytes(bytes)
                            val runtime = TimeSource.Monotonic.markNow() - start
                            Log.i(TAG, "Updated thumbnail cache with $url in $runtime (WiFi: $wifiStatus), ${bytes.size} bytes")
                        }

                        bitmap
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        // Trigger player update
                        playerStateProvider.update { state -> state }
                    }

                    bitmap
                }
            } catch(e: Throwable){
                Log.e(TAG, "Failed to get thumbnail for $url", e)

                null
            }
        }
    }

    data class CachedThumbnail(val requestedAt: Long, val bitmap: Bitmap)

    private val inMemoryCache: MutableMap<String, CachedThumbnail?> = mutableMapOf()
    private val inMemoryCacheLock: Mutex = Mutex()

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun ensureThumbnailIsInCache(url: String): Unit = inMemoryCacheLock.withLock {
        try {
            if (inMemoryCache.containsKey(url)) return@withLock

            inMemoryCache[url] = null

            CoroutineScope(Dispatchers.IO).launch {
                val bitmap = getThumbnail(url) ?: return@launch

                inMemoryCacheLock.withLock {
                    inMemoryCache[url] = CachedThumbnail(System.currentTimeMillis(), bitmap)
                    inMemoryCache.entries
                        .removeAll { it.value != null && it.value!!.requestedAt < System.currentTimeMillis() - 60 * 1_000 }

                    Log.i(TAG, "Cached thumbnail for $url")

                    playerStateProvider.update { state -> state }
                }
            }
        } catch(e: Throwable){
            Log.e(TAG, "Failed to cache thumbnail for $url", e)
        }
    }

    suspend fun getThumbnailFromInMemoryCache(url: String): Bitmap? = inMemoryCacheLock.withLock {
        inMemoryCache[url]?.bitmap
    }
}