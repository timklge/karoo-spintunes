package de.timklge.karoospintunes.spotify.model

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistsResponse(
    val href: String? = null,
    val limit: Int? = null,
    val next: String? = null,
    val offset: Int? = null,
    val previous: String? = null,
    val total: Int? = null,
    val items: List<Playlist>? = null
)

@Serializable
data class TracksResponse(
    val href: String? = null,
    val total: Int? = null,
    val limit: Int? = null,
    val next: String? = null,
    val previous: String? = null,
    val items: List<Item>? = null
)

@Serializable
data class ArtistsResponse(
    val href: String? = null,
    val total: Int? = null,
    val limit: Int? = null,
    val next: String? = null,
    val previous: String? = null,
    val items: List<Artist>? = null
)

@Serializable
data class ShowsResponse(
    val href: String? = null,
    val total: Int? = null,
    val limit: Int? = null,
    val next: String? = null,
    val previous: String? = null,
    val items: List<ShowItem>? = null
)

@Serializable
data class ShowItem(
    val show: Show? = null
)

@Serializable
data class AlbumsResponse(
    val href: String? = null,
    val total: Int? = null,
    val limit: Int? = null,
    val next: String? = null,
    val previous: String? = null,
    val items: List<Album>? = null
)

@Serializable
data class EpisodesResponse(
    val href: String? = null,
    val total: Int? = null,
    val limit: Int? = null,
    val next: String? = null,
    val previous: String? = null,
    val items: List<Item>? = null
)

@Serializable
data class SavedEpisodesResponse(
    val href: String? = null,
    val total: Int? = null,
    val limit: Int? = null,
    val next: String? = null,
    val previous: String? = null,
    val items: List<EpisodeItem>? = null
)

@Serializable
data class EpisodeItem(
    val episode: Item? = null
) : TrackObject {
    override fun getDefinedTrack(): Item {
        return episode ?: Item()
    }
}