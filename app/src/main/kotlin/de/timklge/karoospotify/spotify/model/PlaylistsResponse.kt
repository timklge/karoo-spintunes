package de.timklge.karoospotify.spotify.model

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
data class Tracks(
    val href: String? = null,
    val total: Int? = null,
    val limit: Int? = null,
    val next: String? = null,
    val previous: String? = null,
    val items: List<Item>? = null
)

@Serializable
data class SearchResponse(
    val playlists: PlaylistsResponse? = null,
    val tracks: Tracks? = null,
    val artists: Artists
)