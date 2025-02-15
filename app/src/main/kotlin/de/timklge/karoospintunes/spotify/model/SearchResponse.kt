package de.timklge.karoospintunes.spotify.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(
    val playlists: PlaylistsResponse? = null,
    val tracks: TracksResponse? = null,
    val artists: ArtistsResponse? = null,
    val shows: ShowsResponse? = null,
    val albumsResponse: AlbumsResponse? = null,
    val audiobookResponse: AudiobookResponse? = null,
    val episodesResponse: EpisodesResponse? = null
)

@Serializable
data class AudiobookResponse(
    val href: String? = null,
    val total: Int? = null,
    val limit: Int? = null,
    val next: String? = null,
    val previous: String? = null,
    val items: List<Audiobook>? = null
)

@Serializable
data class Author(
    val name: String? = null
)

@Serializable
data class Narrator(
    val name: String? = null
)

@Serializable
data class Audiobook(
    val authors: List<Author>? = null,
    val description: String? = null,
    @SerialName("html_description") val htmlDescription: String? = null,
    val images: List<Image>? = null,
    val languages: List<String>? = null,
    @SerialName("media_type") val mediaType: String? = null,
    val name: String? = null,
    val narrators: List<Narrator>? = null,
    val publisher: String? = null,
    val type: String? = null,
    @SerialName("total_chapters") val totalChapters: Int? = null
)