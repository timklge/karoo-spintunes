package de.timklge.karoospintunes.spotify

enum class PlaybackType {
    TRACK,
    EPISODE,
    AD;

    companion object {
        fun fromString(string: String?): PlaybackType? {
            return when (string) {
                "track" -> TRACK
                "episode" -> EPISODE
                "ad" -> AD
                else -> null
            }
        }
    }
}