package de.timklge.karoospintunes.screens

import de.timklge.karoospintunes.AutoVolumeConfig
import de.timklge.karoospintunes.auth.TokenResponse
import de.timklge.karoospintunes.jsonWithUnknownKeys
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class SpintuneSettings(
    val welcomeDialogAccepted: Boolean = false,
    val token: TokenResponse? = null,
    val downloadThumbnailsViaCompanion: Boolean = true,
    val useLocalSpotifyIfAvailable: Boolean = false,
    val autoVolumeConfig: AutoVolumeConfig = AutoVolumeConfig()
){
    companion object {
        val defaultSettings = jsonWithUnknownKeys.encodeToString(SpintuneSettings())
    }
}