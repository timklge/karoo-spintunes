package de.timklge.karoospintunes.spotify.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Device(
    val id: String? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
    @SerialName("is_private_session") val isPrivateSession: Boolean? = null,
    @SerialName("is_restricted") val isRestricted: Boolean? = null,
    val name: String? = null,
    val type: String? = null,
    @SerialName("volume_percent") val volumePercent: Int? = null,
    @SerialName("supports_volume") val supportsVolume: Boolean? = null
)