package de.timklge.karoospintunes.spotify

enum class RepeatState(val num: Int, val id: String) {
    OFF(0, "off"),
    TRACK(1, "track"),
    CONTEXT(2, "context");

    companion object {
        fun fromString(string: String?): RepeatState? {
            return when (string) {
                "off" -> OFF
                "track" -> TRACK
                "context" -> CONTEXT
                else -> null
            }
        }

        fun fromInt(it: Int): RepeatState? {
            return when (it) {
                0 -> OFF
                1 -> TRACK
                2 -> CONTEXT
                else -> null
            }
        }
    }
}