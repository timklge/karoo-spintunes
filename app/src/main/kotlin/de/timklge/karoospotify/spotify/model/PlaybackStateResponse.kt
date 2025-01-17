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
    val actions: Actions? = null,
)

@Serializable
data class Actions(
    val disallows: Disallows? = null,
)

@Serializable
data class Disallows(
    val pausing: Boolean? = null,
    val resuming: Boolean? = null,
    val seeking: Boolean? = null,
    val skippingNext: Boolean? = null,
    val skippingPrev: Boolean? = null,
    val togglingRepeatContext: Boolean? = null,
    val togglingRepeatTrack: Boolean? = null,
    val togglingShuffle: Boolean? = null,
    val transferringPlayback: Boolean? = null
)