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
    @SerialName("smart_shuffle") val smartShuffle: Boolean? = null,
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
    @SerialName("skipping_next") val skippingNext: Boolean? = null,
    @SerialName("skipping_prev") val skippingPrev: Boolean? = null,
    @SerialName("toggling_repeat_context") val togglingRepeatContext: Boolean? = null,
    @SerialName("toggling_repeat_track") val togglingRepeatTrack: Boolean? = null,
    @SerialName("toggling_shuffle") val togglingShuffle: Boolean? = null,
    @SerialName("transferring_playback") val transferringPlayback: Boolean? = null
)