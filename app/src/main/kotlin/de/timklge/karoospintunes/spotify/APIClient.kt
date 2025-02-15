package de.timklge.karoospintunes.spotify

import de.timklge.karoospintunes.spotify.model.PlayRequest
import de.timklge.karoospintunes.spotify.model.PlayRequestUris

interface APIClient {
    suspend fun pause()
    suspend fun play(playRequest: PlayRequest? = null)
    suspend fun playUris(playRequest: PlayRequestUris)
    suspend fun next()
    suspend fun previous()
    suspend fun seek(positionInMs: Int)
    suspend fun toggleShuffle(shuffle: Boolean)
    suspend fun toggleRepeat(repeat: RepeatState)
    suspend fun setVolume(volume: Float)
    suspend fun addToQueue(uri: String)
}