package de.timklge.karoospotify.spotify

import android.content.Context
import android.util.Log
import de.timklge.karoospotify.KarooSpotifyExtension
import de.timklge.karoospotify.KarooSystemServiceProvider
import de.timklge.karoospotify.auth.OAuth2Client
import de.timklge.karoospotify.jsonWithUnknownKeys
import de.timklge.karoospotify.spotify.model.EpisodesResponse
import de.timklge.karoospotify.spotify.model.LibraryItemsResponse
import de.timklge.karoospotify.spotify.model.PlayRequest
import de.timklge.karoospotify.spotify.model.PlayRequestUris
import de.timklge.karoospotify.spotify.model.PlaybackStateResponse
import de.timklge.karoospotify.spotify.model.Playlist
import de.timklge.karoospotify.spotify.model.PlaylistItemsResponse
import de.timklge.karoospotify.spotify.model.PlaylistsResponse
import de.timklge.karoospotify.spotify.model.QueueResponse
import de.timklge.karoospotify.spotify.model.SavedEpisodesResponse
import de.timklge.karoospotify.spotify.model.SearchResponse
import de.timklge.karoospotify.spotify.model.ShowsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.File
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import kotlin.math.roundToInt

class WebAPIClient(
    private val karooSystemServiceProvider: KarooSystemServiceProvider,
    private val oAuth2Client: OAuth2Client,
    private val context: Context
): APIClient {
    companion object {
        const val CACHE_TIMEOUT = 1000 * 60 * 60 * 24 * 7L // 1 week
        const val BASE_URL = "https://api.spotify.com/v1"
    }

    private suspend inline fun<reified K, reified V> readFromCache(identifier: String, key: K, crossinline supplier: suspend (K) -> V): V {
        return withContext(Dispatchers.IO) {
            val name = V::class.simpleName ?: error("Failed to get class name")
            val file = File(context.cacheDir, "webapi_${name}_${identifier}")
            val exists = file.exists()
            val creationTime = if (exists){
                Files.readAttributes(file.toPath(), BasicFileAttributes::class.java).creationTime()?.toMillis() ?: 0L
            } else {
                0L
            }
            val cacheFileValid = exists && creationTime > System.currentTimeMillis() - CACHE_TIMEOUT

            val map = if (cacheFileValid){
                jsonWithUnknownKeys.decodeFromString<MutableMap<K, V>>(file.readText())
            } else {
                mutableMapOf()
            }

            val value = if (map.containsKey(key)){
                Log.d(KarooSpotifyExtension.TAG, "WebAPI cache hit for $identifier")
                map.getValue(key)
            } else {
                Log.d(KarooSpotifyExtension.TAG, "WebAPI cache miss for $identifier")
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
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Pause", e.message ?: "Failed to pause playback", e)
        }
    }

    override suspend fun play(playRequest: PlayRequest?) {
        try {
            val body = playRequest?.let { jsonWithUnknownKeys.encodeToString(it).encodeToByteArray() }
            oAuth2Client.makeAuthorizedRequest("PUT", "$BASE_URL/me/player/play", false, emptyMap(), body)
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Play", e.message ?: "Failed to start playing", e)
        }
    }

    override suspend fun playUris(playRequest: PlayRequestUris) {
        try {
            val body = jsonWithUnknownKeys.encodeToString(playRequest).encodeToByteArray()
            oAuth2Client.makeAuthorizedRequest("PUT", "$BASE_URL/me/player/play", false, emptyMap(), body)
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Play", e.message ?: "Failed to start playing", e)
        }
    }

    override suspend fun seek(positionInMs: Int) {
        try {
            oAuth2Client.makeAuthorizedRequest("PUT", "$BASE_URL/me/player/seek?position_ms=$positionInMs")
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Seek", e.message ?: "Failed to seek to position", e)
        }
    }

    override suspend fun next() {
        try {
            oAuth2Client.makeAuthorizedRequest("POST", "$BASE_URL/me/player/next")
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Change track", e.message ?: "Failed to start next track", e)
        }
    }

    override suspend fun previous() {
        try {
            oAuth2Client.makeAuthorizedRequest("POST", "$BASE_URL/me/player/previous")
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Change track", e.message ?: "Failed to start previous track", e)
        }
    }

    suspend fun getPlayerState(ctx: Context): PlaybackStateResponse? {
        return try {
            val response = oAuth2Client.makeAuthorizedRequest("GET", "$BASE_URL/me/player?additional_types=episode", markAsPending = false)

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
            Log.e(KarooSpotifyExtension.TAG, "Failed to get player state", e)
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
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Search", e.message ?: "Failed to search", e)
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
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Queue", e.message ?: "Failed to get playlist", e)
            null
        }
    }

    suspend fun getPlaylistItems(playlistId: String, offset: Int): PlaylistItemsResponse? {
        return try {
            readFromCache<Int, PlaylistItemsResponse>("playlist_items_${playlistId}", offset) {
                val response = oAuth2Client.makeAuthorizedRequest( "GET", "$BASE_URL/playlists/$playlistId/tracks?offset=$offset&limit=50")
                if (response.statusCode !in 200..299) {
                    error("HTTP ${response.statusCode}: ${response.error}")
                }
                response.error?.let { error(it) }

                val jsonString = response.body?.decodeToString() ?: error("Failed to read json")
                jsonWithUnknownKeys.decodeFromString(jsonString)
            }
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Queue", e.message ?: "Failed to get playlist", e)
            null
        }
    }

    suspend fun getLibraryItems(offset: Int): LibraryItemsResponse? {
        return try {
            readFromCache<Int, LibraryItemsResponse>( "library", offset) {
                val response = oAuth2Client.makeAuthorizedRequest("GET", "$BASE_URL/me/tracks?offset=$offset&limit=50")
                if (response.statusCode !in 200..299) {
                    error("HTTP ${response.statusCode}: ${response.error}")
                }
                response.error?.let { error(it) }

                val jsonString = response.body?.decodeToString() ?: error("Failed to read json")
                jsonWithUnknownKeys.decodeFromString(jsonString)
            }
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Queue", e.message ?: "Failed to get library items", e)
            null
        }
    }

    suspend fun getSavedShows(offset: Int): ShowsResponse? {
        return try {
            readFromCache<Int, ShowsResponse>( "shows", offset) {
                val response = oAuth2Client.makeAuthorizedRequest("GET", "$BASE_URL/me/shows?offset=$offset&limit=50")
                if (response.statusCode !in 200..299) {
                    error("HTTP ${response.statusCode}: ${response.error}")
                }
                response.error?.let { error(it) }

                val jsonString = response.body?.decodeToString() ?: error("Failed to read json")
                jsonWithUnknownKeys.decodeFromString(jsonString)
            }
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Shows", e.message ?: "Failed to get shows", e)
            null
        }
    }

    suspend fun getShowEpisodes(showId: String, offset: Int): EpisodesResponse? {
        return try {
            readFromCache<Int, EpisodesResponse>("episodes_${showId}", offset) {
                val response = oAuth2Client.makeAuthorizedRequest("GET", "$BASE_URL/shows/$showId/episodes?offset=$offset&limit=50")
                if (response.statusCode !in 200..299) {
                    error("HTTP ${response.statusCode}: ${response.error}")
                }
                response.error?.let { error(it) }

                val jsonString = response.body?.decodeToString() ?: error("Failed to read json")
                jsonWithUnknownKeys.decodeFromString(jsonString)
            }
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Episodes", e.message ?: "Failed to get episodes", e)
            null
        }
    }

    suspend fun getSavedEpisodes(offset: Int): SavedEpisodesResponse? {
        return try {
            //readFromCache<Int, EpisodesResponse>("episodes_${showId}", offset) {
                val response = oAuth2Client.makeAuthorizedRequest("GET", "$BASE_URL/me/episodes?offset=$offset&limit=50")
                if (response.statusCode !in 200..299) {
                    error("HTTP ${response.statusCode}: ${response.error}")
                }
                response.error?.let { error(it) }

                val jsonString = response.body?.decodeToString() ?: error("Failed to read json")
                jsonWithUnknownKeys.decodeFromString(jsonString)
            //}
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Episodes", e.message ?: "Failed to get episodes", e)
            null
        }
    }

    suspend fun getPlayerQueue(ctx: Context): QueueResponse? {
        return try {
            val response = oAuth2Client.makeAuthorizedRequest("GET", "$BASE_URL/me/player/queue")
            if (response.statusCode !in 200..299) {
                error("HTTP ${response.statusCode}: ${response.error}")
            }
            response.error?.let { error(it) }

            val jsonString = response.body?.decodeToString() ?: error("Failed to read json")
            jsonWithUnknownKeys.decodeFromString(jsonString)
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Queue", e.message ?: "Failed to get queue", e)
            null
        }
    }

    suspend fun getPlaylists(ctx: Context, offset: Int = 0): PlaylistsResponse? {
        return try {
            readFromCache<Int, PlaylistsResponse>("playlists", offset) {
                val response = oAuth2Client.makeAuthorizedRequest("GET", "$BASE_URL/me/playlists?limit=50&offset=$offset")
                if (response.statusCode !in 200..299) {
                    error("HTTP ${response.statusCode}: ${response.error}")
                }
                response.error?.let { error(it) }

                val jsonString = response.body?.decodeToString() ?: error("Failed to read json")
                jsonWithUnknownKeys.decodeFromString(jsonString)
            }
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Playlists", e.message ?: "Failed to get playlists", e)
            null
        }
    }

    override suspend fun toggleShuffle(shuffle: Boolean) {
        try {
            oAuth2Client.makeAuthorizedRequest("PUT", "$BASE_URL/me/player/shuffle?state=$shuffle")
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Toggle Shuffle", e.message ?: "Failed to set shuffle mode", e)
        }
    }

    override suspend fun toggleRepeat(repeat: RepeatState) {
        try {
            oAuth2Client.makeAuthorizedRequest("PUT", "$BASE_URL/me/player/repeat?state=${repeat.id}")
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Toggle Repeat", e.message ?: "Failed to set repeat mode", e)
        }
    }

    override suspend fun setVolume(volume: Float) {
        val volumePercent = (volume * 100).roundToInt().coerceIn(0..100)

        try {
            oAuth2Client.makeAuthorizedRequest("PUT", "$BASE_URL/me/player/volume?volume_percent=${volumePercent}")
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
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Add To Library", e.message ?: "Failed to add track", e)
        }
    }

    override suspend fun addToQueue(uri: String) {
        try {
            oAuth2Client.makeAuthorizedRequest("POST", "$BASE_URL/me/player/queue?uri=${URLEncoder.encode(uri, "UTF-8")}")
        } catch (e: Throwable) {
            karooSystemServiceProvider.showError("Add To Queue", e.message ?: "Failed to add track", e)
        }
    }
}