package de.timklge.karoospotify.spotify.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    @SerialName("display_name") val displayName: String? = null,
    val href: String? = null,
    val id: String? = null,
    val uri: String? = null
)