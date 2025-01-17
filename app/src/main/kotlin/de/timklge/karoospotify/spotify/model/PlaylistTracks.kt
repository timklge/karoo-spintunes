package de.timklge.karoospotify.spotify.model

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistTracks(
    val href: String? = null,
    val total: Int? = null,
    val limit: Int? = null,
    val next: String? = null,
    val previous: String? = null,
    val items: List<PlaylistTrackObject>? = null
)