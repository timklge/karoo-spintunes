package de.timklge.karoospintunes.datatypes

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import de.timklge.karoospintunes.KarooSpintunesExtension
import de.timklge.karoospintunes.KarooSystemServiceProvider
import de.timklge.karoospintunes.R
import de.timklge.karoospintunes.datatypes.actions.FastForwardAction
import de.timklge.karoospintunes.datatypes.actions.LouderAction
import de.timklge.karoospintunes.datatypes.actions.NextAction
import de.timklge.karoospintunes.datatypes.actions.PauseAction
import de.timklge.karoospintunes.datatypes.actions.PlayAction
import de.timklge.karoospintunes.datatypes.actions.PreviousAction
import de.timklge.karoospintunes.datatypes.actions.QuieterAction
import de.timklge.karoospintunes.datatypes.actions.RewindAction
import de.timklge.karoospintunes.datatypes.actions.ToggleOptionsMenuCallback
import de.timklge.karoospintunes.datatypes.actions.ToggleRepeatAction
import de.timklge.karoospintunes.datatypes.actions.ToggleShuffleAction
import de.timklge.karoospintunes.screens.PlayActivity
import de.timklge.karoospintunes.screens.QueueActivity
import de.timklge.karoospintunes.spotify.PlayerAction
import de.timklge.karoospintunes.spotify.PlayerState
import de.timklge.karoospintunes.spotify.RepeatState
import de.timklge.karoospintunes.streamDatatypeIsVisible
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.ShowCustomStreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable fun ActionButton(
    @DrawableRes icon: Int,
    disabled: Boolean,
    toggledOff: Boolean = false,
    actionProvider: () -> Action
) {
    val context = LocalContext.current

    val colorFilter = if (disabled){
        ColorFilter.tint(ColorProvider(Color(ContextCompat.getColor(context, R.color.lighterGray)), Color(ContextCompat.getColor(context, R.color.darkerGray))))
    } else if (toggledOff){
        ColorFilter.tint(ColorProvider(Color(ContextCompat.getColor(context, R.color.lightGray)), Color(ContextCompat.getColor(context, R.color.darkGray))))
    } else {
        ColorFilter.tint(ColorProvider(Color.Black, Color.White))
    }

    var modifier = GlanceModifier.size(48.dp).padding(5.dp)

    if (!disabled) modifier = modifier.clickable(actionProvider())

    Image(ImageProvider(icon), "Toggle Repeat", modifier = modifier, colorFilter = colorFilter)
}

@Composable
fun PlayButton(playerState: PlayerState, playerSize: PlayerSize, disabled: Boolean) {
    if (playerState.isPlaying == true) {
        ActionButton(R.drawable.pause_regular_132, disabled) { actionRunCallback(PauseAction::class.java) }
    } else if(playerState.isPlaying == false) {
        ActionButton(R.drawable.play_regular_132, disabled) { actionRunCallback(PlayAction::class.java) }
    }

    if (playerState.isPlaying != null && playerSize == PlayerSize.SMALL){
        ActionButton(R.drawable.dots_vertical_rounded_regular_132, disabled) { actionRunCallback(ToggleOptionsMenuCallback::class.java)}
    }
}

@Composable
fun OptionsRows(
    context: Context,
    playerState: PlayerState,
    showToggle: Boolean,
    buttonsDisabled: Boolean,
    disabledActions: Map<PlayerAction, Boolean>
) {
    Row(modifier = GlanceModifier.fillMaxWidth().height(50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ActionButton(R.drawable.rewind_regular_132, buttonsDisabled || disabledActions[PlayerAction.SEEK] == true) { actionRunCallback(RewindAction::class.java) }
        ActionButton(R.drawable.fast_forward_regular_132, buttonsDisabled || disabledActions[PlayerAction.SEEK] == true) { actionRunCallback(FastForwardAction::class.java) }
        ActionButton(R.drawable.skip_previous_regular_132, buttonsDisabled || (disabledActions[PlayerAction.SKIP_PREVIOUS] == true && disabledActions[PlayerAction.SEEK] == true) ) { actionRunCallback(PreviousAction::class.java) }
        ActionButton(R.drawable.skip_next_regular_132, buttonsDisabled || disabledActions[PlayerAction.SKIP_NEXT] == true) { actionRunCallback(NextAction::class.java) }

        if (showToggle){
            ActionButton(R.drawable.dots_vertical_rounded_regular_132, buttonsDisabled) { actionRunCallback(ToggleOptionsMenuCallback::class.java)}
        }

        if (disabledActions[PlayerAction.SET_VOLUME] != true){
            val canMakeLouder = playerState.volume?.let { v -> v < 1.0f } ?: false
            ActionButton(R.drawable.volume_full_regular_132, buttonsDisabled, !canMakeLouder) { actionRunCallback(LouderAction::class.java)}
        }
    }

    Row(modifier = GlanceModifier.fillMaxWidth().height(50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val repeatIcon = when (playerState.isRepeating) {
            RepeatState.OFF, RepeatState.CONTEXT -> R.drawable.repeat_regular_132
            RepeatState.TRACK -> R.drawable.repeat_regular_1_132
            else -> R.drawable.repeat_regular_132
        }

        val canRepeat = !buttonsDisabled && playerState.disabledActions[PlayerAction.TOGGLE_REPEAT] != true
        ActionButton(repeatIcon, !canRepeat, playerState.isRepeating == RepeatState.OFF) { actionRunCallback(ToggleRepeatAction::class.java) }
        val canShuffle = !buttonsDisabled && playerState.disabledActions[PlayerAction.TOGGLE_SHUFFLE] != true
        ActionButton(R.drawable.shuffle_regular_132, !canShuffle, playerState.isShuffling != true) { actionRunCallback(ToggleShuffleAction::class.java) }
        ActionButton(R.drawable.playlist_solid_132, buttonsDisabled) {
            val intent = Intent(context, QueueActivity::class.java)
            actionStartActivity(intent)
        }
        // The library button should remain enabled in local mode even when 'buttonsDisabled' is true.
        ActionButton(R.drawable.library_regular_132, buttonsDisabled && !playerState.isLocalPlayer) {
            val intent = Intent(context, PlayActivity::class.java)
            actionStartActivity(intent)
        }

        if (disabledActions[PlayerAction.SET_VOLUME] != true){
            val canMakeQuieter = playerState.volume?.let { v -> v > 0.0f } ?: false
            ActionButton(R.drawable.volume_low_regular_132, buttonsDisabled, !canMakeQuieter) { actionRunCallback(QuieterAction::class.java)}
        }
    }
}

class PlayerDataType(
    val playerViewProvider: PlayerViewProvider,
    val karooSystemServiceProvider: KarooSystemServiceProvider
) : DataTypeImpl("karoo-spintunes", "player") {
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        Log.d(KarooSpintunesExtension.TAG, "Starting player view with $emitter - $config")

        val configJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
            awaitCancellation()
        }

        val playerSize = if (config.gridSize.first <= 30){
            PlayerSize.SINGLE_FIELD
        } else if (config.gridSize.second <= 30){
            PlayerSize.SMALL
        } else if (config.gridSize.second < 60){
            PlayerSize.MEDIUM
        } else {
            PlayerSize.FULL_PAGE
        }

        val viewJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(ShowCustomStreamState("", null))
            karooSystemServiceProvider.karooSystemService.streamDatatypeIsVisible(dataTypeId).collectLatest { playerIsVisible ->
                if (playerIsVisible) {
                    playerViewProvider.provideView(playerSize).collect { views ->
                        emitter.updateView(views)
                    }
                }
            }
        }
        emitter.setCancellable {
            Log.d(KarooSpintunesExtension.TAG, "Stopping player view with $emitter")
            configJob.cancel()
            viewJob.cancel()
        }
    }
}