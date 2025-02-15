package de.timklge.karoospintunes.spotify.model

import kotlinx.serialization.Serializable

@Serializable
data class ExternalIds(
    val isrc: String
)

