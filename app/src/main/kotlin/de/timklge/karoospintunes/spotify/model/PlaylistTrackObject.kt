package de.timklge.karoospintunes.spotify.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlaylistTrackObject(
    @SerialName("added_at") val addedAt: String? = null,
    @SerialName("added_by") val addedBy: User? = null,
    @SerialName("is_local") val isLocal: Boolean? = null,
    val track: Item? = null
) : TrackObject {
    override fun getDefinedTrack(): Item? {
        return track
    }
}