package de.timklge.karoospotify.spotify.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Show(
    val description: String,
    val explicit: Boolean,
    val href: String,
    val id: String,
    val images: List<Image>? = null,
    val name: String,
    @SerialName("media_type") val mediaType: String,
    val publisher: String,
    val uri: String,
    @SerialName("total_episodes") val totalEpisodes: Int
)