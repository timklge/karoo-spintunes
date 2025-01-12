package de.timklge.karoospotify.spotify.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlaybackStateResponse(
    val device: Device? = null,
    @SerialName("repeat_state") val repeatState: String? = null,
    @SerialName("shuffle_state") val shuffleState: Boolean? = null,
    val context: Context? = null,
    val timestamp: Long? = null,
    @SerialName("progress_ms") val progressMs: Int? = null,
    @SerialName("is_playing") val isPlaying: Boolean? = null,
    val item: Item? = null,
    @SerialName("currently_playing_type") val currentlyPlayingType: String? = null,
    @SerialName("smart_shuffle") val smartShuffle: Boolean? = null
)