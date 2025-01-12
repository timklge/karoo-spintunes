package de.timklge.karoospotify.spotify.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Album(
    @SerialName("album_type") val albumType: String? = null,
    @SerialName("total_tracks") val totalTracks: Int? = null,
    val href: String? = null,
    val id: String? = null,
    val images: List<Image>? = null,
    val name: String? = null,
    val type: String? = null,
    val uri: String? = null,
    val artists: List<Artist>? = null
) {
    companion object {
        fun fromSpotifyAlbum(album: com.spotify.protocol.types.Album): Album {
            return Album(
                name = album.name,
                uri = album.uri,
            )
        }
    }
}
