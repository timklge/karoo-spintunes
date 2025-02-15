package de.timklge.karoospintunes.spotify.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class QueueResponse(
    @SerialName("currently_playing") val currentlyPlaying: Item? = null,
    val queue: List<Item>? = null
)

class ItemWrapper(val item: Item) : TrackObject {
    override fun getDefinedTrack(): Item = item
}