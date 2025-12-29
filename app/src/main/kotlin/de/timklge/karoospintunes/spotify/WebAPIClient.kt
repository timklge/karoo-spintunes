package de.timklge.karoospintunes.spotify

import android.content.Context
import android.util.Log
import android.widget.Toast
import de.timklge.karoospintunes.KarooSpintunesExtension
import de.timklge.karoospintunes.KarooSystemServiceProvider
import de.timklge.karoospintunes.auth.OAuth2Client
import de.timklge.karoospintunes.jsonWithUnknownKeys
import de.timklge.karoospintunes.spotify.model.EpisodesResponse
import de.timklge.karoospintunes.spotify.model.LibraryItemsResponse
import de.timklge.karoospintunes.spotify.model.PlayRequest
import de.timklge.karoospintunes.spotify.model.PlayRequestUris
import de.timklge.karoospintunes.spotify.model.PlaybackStateResponse
import de.timklge.karoospintunes.spotify.model.Playlist
import de.timklge.karoospintunes.spotify.model.PlaylistItemsResponse
import de.timklge.karoospintunes.spotify.model.PlaylistsResponse
import de.timklge.karoospintunes.spotify.model.QueueResponse
import de.timklge.karoospintunes.spotify.model.SavedEpisodesResponse
import de.timklge.karoospintunes.spotify.model.SearchResponse
import de.timklge.karoospintunes.spotify.model.ShowsResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.math.roundToInt

class WebAPIClient(
    private val karooSystemServiceProvider: KarooSystemServiceProvider,
    private val playerStateProvider: PlayerStateProvider,
    private val oAuth2Client: OAuth2Client,
    private val context: Context
): APIClient {
    companion object {
        const val BASE_URL = "https://api.spotify.com/v1"
        const val PAGE_SIZE = 30
    }

    private suspend inline fun<reified K, reified V> readFromCache(identifier: String, key: K, crossinline supplier: suspend (K) -> V): V {
        return withContext(Dispatchers.IO) {
            val name = V::class.simpleName ?: error("Failed to get class name")
            val file = File(context.cacheDir, "webapi_${name}_${identifier}")
            val exists = file.exists()

            val map = if (exists){
                jsonWithUnknownKeys.decodeFromString<MutableMap<K, V>>(file.readText())
            } else {
                mutableMapOf<K, V>()
            }

            val value = if (map.containsKey(key)){
                Log.d(KarooSpintunesExtension.TAG, "WebAPI cache hit for $identifier")
                map.getValue(key)
            } else {
                Log.d(KarooSpintunesExtension.TAG, "WebAPI cache miss for $identifier")
                val result = supplier(key)
                map[key] = result
                file.writeText(jsonWithUnknownKeys.encodeToString(map))
                Files.setAttribute(file.toPath(), "basic:creationTime", FileTime.fromMillis(System.currentTimeMillis()))
                result
            }

            value
        }
    }

    suspend inline fun<reified T> clearCache(identifier: String, context: Context) {
        withContext(Dispatchers.IO) {
            val name = T::class.simpleName ?: error("Failed to get class name")
            val file = File(context.cacheDir, "webapi_${name}_${identifier}")
            if (file.exists()){
                file.delete()
            }
        }
    }

    override suspend fun pause() {
        try {
            oAuth2Client.makeAuthorizedRequest( "PUT", "$BASE_URL/me/player/pause")
        } catch (e: CancellationException) {
            Log.w(KarooSpintunesExtension.TAG, "Cancelled pause action", e)
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Pause", e.message ?: "Failed to pause playback", e)
        }
    }

    override suspend fun play(playRequest: PlayRequest?) {
        try {
            val body = playRequest?.let { jsonWithUnknownKeys.encodeToString(it).encodeToByteArray() }
            oAuth2Client.makeAuthorizedRequest("PUT", "$BASE_URL/me/player/play", false, emptyMap(), body)
        } catch (e: CancellationException) {
            Log.w(KarooSpintunesExtension.TAG, "Cancelled play action", e)
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Play", e.message ?: "Failed to start playing", e)
        }
    }

    override suspend fun playUris(playRequest: PlayRequestUris) {
        try {
            val body = jsonWithUnknownKeys.encodeToString(playRequest).encodeToByteArray()
            oAuth2Client.makeAuthorizedRequest("PUT", "$BASE_URL/me/player/play", false, emptyMap(), body)
        } catch (e: CancellationException) {
            Log.w(KarooSpintunesExtension.TAG, "Cancelled play action", e)
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Play", e.message ?: "Failed to start playing", e)
        }
    }

    override suspend fun seek(positionInMs: Int) {
        try {
            oAuth2Client.makeAuthorizedRequest("PUT", "$BASE_URL/me/player/seek?position_ms=$positionInMs")
        } catch (e: CancellationException) {
            Log.w(KarooSpintunesExtension.TAG, "Cancelled seek action", e)
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Seek", e.message ?: "Failed to seek to position", e)
        }
    }

    override suspend fun next() {
        try {
            oAuth2Client.makeAuthorizedRequest("POST", "$BASE_URL/me/player/next")
        } catch (e: CancellationException) {
            Log.w(KarooSpintunesExtension.TAG, "Cancelled seek action", e)
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Change track", e.message ?: "Failed to start next track", e)
        }
    }

    override suspend fun previous() {
        try {
            oAuth2Client.makeAuthorizedRequest("POST", "$BASE_URL/me/player/previous")
        } catch (e: CancellationException) {
            Log.w(KarooSpintunesExtension.TAG, "Cancelled seek action", e)
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Change track", e.message ?: "Failed to start previous track", e)
        }
    }

    suspend fun getPlayerState(): PlaybackStateResponse? {
        return try {
            val response = oAuth2Client.makeAuthorizedRequest("GET", "$BASE_URL/me/player?additional_types=episode", markAsPending = false)

            if (response.statusCode == 204) {
                playerStateProvider.update { it.copy(error = PlayerError("No player", "No active player found")) }
            }

            if (response.statusCode !in 200..299) {
                error("HTTP ${response.statusCode}: ${response.error}")
            }
            response.error?.let { error(it) }

            // No playback active
            if (response.statusCode == 204) {
                return null
            }

            val jsonString = response.body?.decodeToString() ?: error("Failed to read json")
            jsonWithUnknownKeys.decodeFromString(jsonString)
        } catch (e: Throwable) {
            Log.e(KarooSpintunesExtension.TAG, "Failed to get player state", e)
            // karooSystemService.showError("Player State", e.message ?: "Failed to get player state")
            null
        }
    }

    suspend fun search(q: String): SearchResponse? {
        return try {
            val encoded = withContext(Dispatchers.IO) {
                URLEncoder.encode(q, "UTF-8")
            }

            val response = oAuth2Client.makeAuthorizedRequest("GET", "$BASE_URL/search?q=${encoded}&type=track&")
            if (response.statusCode !in 200..299) {
                error("HTTP ${response.statusCode}: ${response.error}")
            }
            response.error?.let { error(it) }

            val jsonString = response.body?.decodeToString() ?: error("Failed to read json")
            jsonWithUnknownKeys.decodeFromString(jsonString)
        } catch (e: CancellationException) {
            Log.w(KarooSpintunesExtension.TAG, "Cancelled search action", e)
            null
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Search", e.message ?: "Failed to search", e)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            null
        }
    }

    suspend fun getPlaylist(playlistId: String): Playlist? {
        return try {
            val response = oAuth2Client.makeAuthorizedRequest("GET", "$BASE_URL/playlists/$playlistId")
            if (response.statusCode !in 200..299) {
                error("HTTP ${response.statusCode}: ${response.error}")
            }
            response.error?.let { error(it) }

            val jsonString = response.body?.decodeToString() ?: error("Failed to read json")
            jsonWithUnknownKeys.decodeFromString(jsonString)
        } catch (e: CancellationException) {
            Log.w(KarooSpintunesExtension.TAG, "Cancelled get playlist action", e)
            null
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Queue", e.message ?: "Failed to get playlist", e)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            null
        }
    }

    suspend fun getPlaylistItems(playlistId: String, offset: Int): PlaylistItemsResponse? {
        return try {
            readFromCache<Int, PlaylistItemsResponse>("playlist_items_${playlistId}", offset) {
                val response = oAuth2Client.makeAuthorizedRequest( "GET", "$BASE_URL/playlists/$playlistId/tracks?offset=$offset&limit=${PAGE_SIZE}")
                if (response.statusCode !in 200..299) {
                    error("HTTP ${response.statusCode}: ${response.error}")
                }
                response.error?.let { error(it) }

                val jsonString = response.body?.decodeToString() ?: error("Failed to read json")
                jsonWithUnknownKeys.decodeFromString(jsonString)
            }
        } catch (e: CancellationException) {
            Log.w(KarooSpintunesExtension.TAG, "Cancelled get playlist action", e)
            null
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Queue", e.message ?: "Failed to get playlist", e)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            null
        }
    }

    suspend fun getLibraryItems(offset: Int): LibraryItemsResponse? {
        return try {
            readFromCache<Int, LibraryItemsResponse>( "library", offset) {
                val response = oAuth2Client.makeAuthorizedRequest("GET", "$BASE_URL/me/tracks?offset=$offset&limit=${PAGE_SIZE}")
                if (response.statusCode !in 200..299) {
                    error("HTTP ${response.statusCode}: ${response.error}")
                }
                response.error?.let { error(it) }

                val jsonString = response.body?.decodeToString() ?: error("Failed to read json")
                jsonWithUnknownKeys.decodeFromString(jsonString)
            }
        } catch (e: CancellationException) {
            Log.w(KarooSpintunesExtension.TAG, "Cancelled get library items action", e)
            null
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Queue", e.message ?: "Failed to get library items", e)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            null
        }
    }

    suspend fun getSavedShows(offset: Int): ShowsResponse? {
        return try {
            readFromCache<Int, ShowsResponse>( "shows", offset) {
                val response = oAuth2Client.makeAuthorizedRequest("GET", "$BASE_URL/me/shows?offset=$offset&limit=${PAGE_SIZE}")
                if (response.statusCode !in 200..299) {
                    error("HTTP ${response.statusCode}: ${response.error}")
                }
                response.error?.let { error(it) }

                val jsonString = response.body?.decodeToString() ?: error("Failed to read json")
                jsonWithUnknownKeys.decodeFromString(jsonString)
            }
        } catch (e: CancellationException) {
            Log.w(KarooSpintunesExtension.TAG, "Cancelled shows action", e)
            null
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Shows", e.message ?: "Failed to get shows", e)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            null
        }
    }

    suspend fun getShowEpisodes(showId: String, offset: Int): EpisodesResponse? {
        return try {
            readFromCache<Int, EpisodesResponse>("episodes_${showId}", offset) {
                val response = oAuth2Client.makeAuthorizedRequest("GET", "$BASE_URL/shows/$showId/episodes?offset=$offset&limit=${PAGE_SIZE}")
                if (response.statusCode !in 200..299) {
                    error("HTTP ${response.statusCode}: ${response.error}")
                }
                response.error?.let { error(it) }

                val jsonString = response.body?.decodeToString() ?: error("Failed to read json")
                jsonWithUnknownKeys.decodeFromString(jsonString)
            }
        } catch (e: CancellationException) {
            Log.w(KarooSpintunesExtension.TAG, "Cancelled shows action", e)
            null
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Episodes", e.message ?: "Failed to get episodes", e)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            null
        }
    }

    suspend fun getSavedEpisodes(offset: Int): SavedEpisodesResponse? {
        return try {
            //readFromCache<Int, EpisodesResponse>("episodes_${showId}", offset) {
                val response = oAuth2Client.makeAuthorizedRequest("GET", "$BASE_URL/me/episodes?offset=$offset&limit=${PAGE_SIZE}")
                if (response.statusCode !in 200..299) {
                    error("HTTP ${response.statusCode}: ${response.error}")
                }
                response.error?.let { error(it) }

                val jsonString = response.body?.decodeToString() ?: error("Failed to read json")
                jsonWithUnknownKeys.decodeFromString(jsonString)
            //}
        } catch (e: CancellationException) {
            Log.w(KarooSpintunesExtension.TAG, "Cancelled shows action", e)
            null
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Episodes", e.message ?: "Failed to get episodes", e)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            null
        }
    }

    suspend fun getPlayerQueue(): QueueResponse? {
        return try {
            val response = oAuth2Client.makeAuthorizedRequest("GET", "$BASE_URL/me/player/queue")
            if (response.statusCode !in 200..299) {
                error("HTTP ${response.statusCode}: ${response.error}")
            }
            response.error?.let { error(it) }

            val jsonString = response.body?.decodeToString() ?: error("Failed to read json")
            jsonWithUnknownKeys.decodeFromString(jsonString)
        } catch (e: CancellationException) {
            Log.w(KarooSpintunesExtension.TAG, "Cancelled queue action", e)
            null
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Queue", e.message ?: "Failed to get queue", e)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            null
        }
    }

    suspend fun getPlaylists(offset: Int = 0): PlaylistsResponse? {
        return try {
            readFromCache<Int, PlaylistsResponse>("playlists", offset) {
                val response = oAuth2Client.makeAuthorizedRequest("GET", "$BASE_URL/me/playlists?limit=${PAGE_SIZE}&offset=$offset")
                if (response.statusCode !in 200..299) {
                    error("HTTP ${response.statusCode}: ${response.error}")
                }
                response.error?.let { error(it) }

                val jsonString = response.body?.decodeToString() ?: error("Failed to read json")
                jsonWithUnknownKeys.decodeFromString(jsonString)
            }
        } catch (e: CancellationException) {
            Log.w(KarooSpintunesExtension.TAG, "Cancelled getting playlists", e)
            null
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Playlists", e.message ?: "Failed to get playlists", e)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            null
        }
    }

    override suspend fun toggleShuffle(shuffle: Boolean) {
        try {
            oAuth2Client.makeAuthorizedRequest("PUT", "$BASE_URL/me/player/shuffle?state=$shuffle")
        } catch (e: CancellationException) {
            Log.w(KarooSpintunesExtension.TAG, "Cancelled shuffle action", e)
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Toggle Shuffle", e.message ?: "Failed to set shuffle mode", e)
        }
    }

    override suspend fun toggleRepeat(repeat: RepeatState) {
        try {
            oAuth2Client.makeAuthorizedRequest("PUT", "$BASE_URL/me/player/repeat?state=${repeat.id}")
        } catch (e: CancellationException) {
            Log.w(KarooSpintunesExtension.TAG, "Cancelled repeat action", e)
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Toggle Repeat", e.message ?: "Failed to set repeat mode", e)
        }
    }

    override suspend fun setVolume(volume: Float) {
        val volumePercent = (volume * 100).roundToInt().coerceIn(0..100)

        try {
            oAuth2Client.makeAuthorizedRequest("PUT", "$BASE_URL/me/player/volume?volume_percent=${volumePercent}")
        } catch (e: CancellationException) {
            Log.w(KarooSpintunesExtension.TAG, "Cancelled volume action", e)
        } catch(e: Throwable){
            karooSystemServiceProvider.showError("Volume Control", e.message ?: "Failed to set volume", e)
        }
    }

    suspend fun addTrackToLibrary(currentTrackIds: List<String>) {
        try {
            val encoded = withContext(Dispatchers.IO) {
                URLEncoder.encode(currentTrackIds.joinToString(","), "UTF-8")
            }

            oAuth2Client.makeAuthorizedRequest("PUT", "$BASE_URL/me/tracks/?ids=${encoded}")
        } catch (e: CancellationException) {
            Log.w(KarooSpintunesExtension.TAG, "Cancelled add track to library action", e)
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Add To Library", e.message ?: "Failed to add track", e)
        }
    }

    override suspend fun addToQueue(uri: String) {
        try {
            val url = withContext(Dispatchers.IO) {
                URLEncoder.encode(uri, "UTF-8")
            }
            oAuth2Client.makeAuthorizedRequest("POST", "$BASE_URL/me/player/queue?uri=${url}")
        } catch (e: CancellationException) {
            Log.w(KarooSpintunesExtension.TAG, "Cancelled add to queue action", e)
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Add To Queue", e.message ?: "Failed to add track", e)
        }
    }
}