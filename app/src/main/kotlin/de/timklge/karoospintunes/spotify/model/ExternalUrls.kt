package de.timklge.karoospintunes.spotify.model

import kotlinx.serialization.Serializable

@Serializable
data class ExternalUrls(
    val spotify: String? = null
)