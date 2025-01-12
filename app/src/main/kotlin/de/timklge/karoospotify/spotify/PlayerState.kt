package de.timklge.karoospotify.spotify

data class PlayerState(
    val isPlayingType: PlaybackType? = null,
    val isPlayingTrackName: String? = null,
    val isPlayingArtistName: String? = null,
    val isPlayingShowName: String? = null,
    val isShuffling: Boolean? = null,
    val isPlaying: Boolean? = null,
    val isRepeating: RepeatState? = null,
    val isInitialized: Boolean = false,
    val isInOptionsMenu: Boolean = false,
    val isPlayingTrackId: String? = null,
    val isPlayingTrackUri: String? = null,
    val isPlayingTrackThumbnailUrls: List<String>? = null,
    val commandPending: Boolean = false,
    val requestPending: Int = 0,
    val playProgressInMs: Int? = null,
    val currentTrackLengthInMs: Int? = null,
    val canControlVolume: Boolean = false,
    val volume: Float? = null
)