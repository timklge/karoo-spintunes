package de.timklge.karoospintunes.spotify.model

import com.spotify.protocol.types.Track
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ResumePoint(
    @SerialName("fully_played") val fullyPlayed: Boolean,
    @SerialName("resume_position_ms") val resumePositionMs: Int
)


@Serializable
data class Item(
    val album: Album? = null,
    val artists: List<Artist>? = null,
    @SerialName("duration_ms") val durationMs: Int? = null,
    val explicit: Boolean? = null,
    @SerialName("external_ids") val externalIds: ExternalIds? = null,
    @SerialName("external_urls") val externalUrls: ExternalUrls? = null,
    val href: String? = null,
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    @SerialName("html_description") val htmlDescription: String? = null,
    val popularity: Int? = null,
    @SerialName("preview_url") val previewUrl: String? = null,
    @SerialName("track_number") val trackNumber: Int? = null,
    val type: String? = null,
    val uri: String? = null,
    @SerialName("is_local") val isLocal: Boolean? = null,
    // FIXME @SerialName("resume_point") val resumePoint: ResumePoint? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("release_date_precision") val releaseDatePrecision: String? = null,
    val images: List<Image>? = null,
    val show: Show? = null
): TrackObject {
    override fun getDefinedTrack(): Item {
        return this
    }

    companion object {
        fun fromSpotifyTrack(item: Track): Item {
            return Item(
                album = Album.fromSpotifyAlbum(item.album),
                artists = item.artists.map { Artist.fromSpotifyArtist(it) },
                durationMs = item.duration.toInt(),
                name = item.name,
                type = if(item.isEpisode) "episode" else "track",
                uri = item.uri,
                images = listOfNotNull(item.imageUri?.let { Image(it.raw) }),
            )
        }
    }
}