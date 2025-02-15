package de.timklge.karoospintunes.spotify

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PlayerStateProvider {
    private val _state = MutableStateFlow(PlayerState())

    val state = _state.asStateFlow()

    fun update(function: (PlayerState) -> PlayerState) {
        _state.update {
            function(it)
        }
    }
}

