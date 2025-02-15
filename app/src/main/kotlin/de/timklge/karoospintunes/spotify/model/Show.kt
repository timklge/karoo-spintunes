package de.timklge.karoospintunes.spotify.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface PlaylistsItem {
    fun getItemId(): String?
    fun getItemName(): String?
    fun getItemImages(): List<Image>?
    fun getItemCount(): Int?
}

@Serializable
data class Show(
    val description: String? = null,
    val explicit: Boolean? = null,
    val href: String? = null,
    val id: String? = null,
    val images: List<Image>? = null,
    val name: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    val publisher: String? = null,
    val uri: String? = null,
    @SerialName("total_episodes") val totalEpisodes: Int? = null
) : PlaylistsItem {
    override fun getItemId(): String? = id
    override fun getItemName(): String? = name
    override fun getItemImages(): List<Image>? = images
    override fun getItemCount(): Int? = totalEpisodes
}