package de.timklge.karoospintunes.spotify

import android.util.Log
import de.timklge.karoospintunes.KarooSpintunesExtension
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PlayerInPreviewModeProvider {
    data class PlayerInPreviewModeState(val inPreviewMode: Int = 0)

    private val _state = MutableStateFlow(PlayerInPreviewModeState())

    val state = _state.asStateFlow()

    fun update(function: (PlayerInPreviewModeState) -> PlayerInPreviewModeState) {
        _state.update {
            val newState = function(it)

            Log.d(KarooSpintunesExtension.TAG, "Updating PlayerInPreviewModeState: ${newState.inPreviewMode}")

            newState
        }
    }
}