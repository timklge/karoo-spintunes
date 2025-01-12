package de.timklge.karoospotify.spotify.model

import com.spotify.protocol.types.Artist
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Artist(
    val id: String? = null,
    val name: String? = null,
    val type: String? = null,
    val uri: String? = null,
    val href: String? = null,
    @SerialName("external_urls") val externalUrls: ExternalUrls? = null
) {
    companion object {
        fun fromSpotifyArtist(item: Artist): de.timklge.karoospotify.spotify.model.Artist {
            return Artist(
                name = item.name,
                uri = item.uri,
            )
        }
    }
}