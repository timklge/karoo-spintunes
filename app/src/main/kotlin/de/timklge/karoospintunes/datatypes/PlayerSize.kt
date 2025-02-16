package de.timklge.karoospintunes.datatypes

enum class PlayerSize {
    SINGLE_FIELD,
    SMALL,
    MEDIUM,
    FULL_PAGE; /* Full page */

    fun isLarge(): Boolean = (this == FULL_PAGE || this == MEDIUM)
}