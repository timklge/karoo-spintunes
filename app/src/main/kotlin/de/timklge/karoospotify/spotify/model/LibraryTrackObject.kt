package de.timklge.karoospotify.spotify.model

import kotlinx.serialization.Serializable

@Serializable
data class LibraryTrackObject(
    val addedAt: String? = null,
    val track: Item? = null
) : ITrackObject {
    override fun getDefinedTrack(): Item? {
        return track
    }
}