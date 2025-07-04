package de.timklge.karoospintunes.spotify.model

import kotlinx.serialization.Serializable

@Serializable
data class Image(
    val url: String? = null,
    val height: Int? = null,
    val width: Int? = null
)