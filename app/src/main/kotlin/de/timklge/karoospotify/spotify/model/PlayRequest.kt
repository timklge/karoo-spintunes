package de.timklge.karoospotify.spotify.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlayRequest(
    @SerialName("context_uri") val contextUri: String? = null,
    val offset: Offset? = null,
    @SerialName("position_ms") val positionMs: Int? = null,
    val uris: List<String>? = null
)

@Serializable
data class PlayRequestUris(
    val uris: List<String>? = null
)
