package de.timklge.karoospotify.spotify.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Playlist(
    val collaborative: Boolean? = null,
    val description: String? = null,
    @SerialName("external_urls") val externalUrls: ExternalUrls? = null,
    val href: String? = null,
    val id: String? = null,
    val images: List<Image>? = null,
    val name: String? = null,
    val owner: User? = null,
    val public: Boolean? = null,
    @SerialName("snapshot_id") val snapshotId: String? = null,
    val tracks: PlaylistTracks? = null,
    val type: String? = null,
    val uri: String? = null
) : PlaylistsItem {
    override fun getItemId(): String? = id
    override fun getItemName(): String? = name
    override fun getItemImages(): List<Image>? = images
    override fun getItemCount(): Int? = tracks?.total
}