package de.timklge.karoospotify

import android.util.Log
import de.timklge.karoospotify.spotify.APIClient
import de.timklge.karoospotify.spotify.APIClientProvider
import de.timklge.karoospotify.spotify.LocalClient
import io.hammerhead.karooext.models.InRideAlert
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class AutoVolumeConfig(
    val enabled: Boolean = false,
    val minVolume: Float = DEFAULT_MIN_VOLUME,
    val maxVolume: Float = DEFAULT_MAX_VOLUME,
    val minVolumeAtSpeed: Float = DEFAULT_MIN_VOLUME_AT_SPEED,
    val maxVolumeAtSpeed: Float = DEFAULT_MAX_VOLUME_AT_SPEED
) {
    companion object {
        const val DEFAULT_MIN_VOLUME = 0.3f
        const val DEFAULT_MAX_VOLUME = 0.9f
        const val DEFAULT_MIN_VOLUME_AT_SPEED = 5.0f * 0.277778f // By default, min volume is reached at 5 km/h
        const val DEFAULT_MAX_VOLUME_AT_SPEED = 50 * 0.277778f // By default,  max volume is reached at 50 km/h
    }
}

class AutoVolume(
    private val karooSystemServiceProvider: KarooSystemServiceProvider,
    private val apiClientProvider: APIClientProvider,
) {
    fun getVolumeForSpeed(volumeConfig: AutoVolumeConfig, speedInMs: Float): Float {
        if (speedInMs < volumeConfig.minVolumeAtSpeed) {
            return volumeConfig.minVolume
        }

        if (speedInMs >= volumeConfig.maxVolumeAtSpeed) {
            return volumeConfig.maxVolume
        }

        val normalizedSpeed = (speedInMs - volumeConfig.minVolumeAtSpeed) / (volumeConfig.maxVolumeAtSpeed - volumeConfig.minVolumeAtSpeed)
        val quadraticFactor = normalizedSpeed * normalizedSpeed

        return volumeConfig.minVolume + quadraticFactor * (volumeConfig.maxVolume - volumeConfig.minVolume)
    }

    data class AutoVolumeStreamState(val apiClient: APIClient, val config: AutoVolumeConfig)

    @OptIn(FlowPreview::class)
    suspend fun setAutoVolume() {
        var lastSetVolume: Float? = null
        var userOffset: Float? = null

        val apiClientStream = apiClientProvider.getActiveAPIInstance()
        val settingsStream = karooSystemServiceProvider.streamSettings()
            .map { it.autoVolumeConfig }
            .distinctUntilChanged()
            .map {
                lastSetVolume = null
                it
            }

        val stateStream = apiClientStream.combine(settingsStream) { apiClient, settings -> AutoVolumeStreamState(apiClient, settings) }

        val speedStream = karooSystemServiceProvider.streamSpeed()

        stateStream
            .distinctUntilChanged()
            .combine(speedStream) { apiClient, speed -> apiClient to speed }
            .filter { (state, _) -> state.config.enabled }
            .collect { (state, speed) ->
                val volumeDiff = if (lastSetVolume != null && state.apiClient is LocalClient){
                    val currentVolume = state.apiClient.getVolume()
                    val volumeDiff = currentVolume - lastSetVolume!!

                    volumeDiff
                } else 0.0f

                userOffset = userOffset?.plus(volumeDiff) ?: volumeDiff
                val offset = userOffset ?: 0.0f
                val userOffsetPercent = (offset * 100).roundToInt()

                if (volumeDiff >= 0.1f){
                    val userOffsetPercentString = if (userOffsetPercent > 0) "+${userOffsetPercent}" else userOffsetPercent.toString()

                    karooSystemServiceProvider.karooSystemService.dispatch(
                        InRideAlert(id = "autovol-${System.currentTimeMillis()}", icon = R.drawable.spotify, title = "Auto Volume", detail = "Manual Volume ${userOffsetPercentString}$", autoDismissMs = 5_000L, backgroundColor = R.color.hYellow, textColor = R.color.black)
                    )
                }

                val volume = (getVolumeForSpeed(state.config, speed.toFloat()) + offset).coerceIn(0.0f, 1.0f)

                Log.d(KarooSpotifyExtension.TAG, "Setting volume to $volume for speed $speed m/s - user offset ${userOffsetPercent}%")

                state.apiClient.setVolume(volume)

                lastSetVolume = volume
            }
    }
}