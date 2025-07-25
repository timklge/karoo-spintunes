package de.timklge.karoospintunes.spotify.model

import kotlinx.serialization.Serializable

@Serializable
data class LibraryItemsResponse(
    val href: String? = null,
    val limit: Int? = null,
    val next: String? = null,
    val offset: Int? = null,
    val previous: String? = null,
    val total: Int? = null,
    val items: List<LibraryTrackObject>? = null
)