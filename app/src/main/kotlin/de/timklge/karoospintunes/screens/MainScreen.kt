package de.timklge.karoospintunes.screens

import android.util.Log
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.timklge.karoospintunes.AutoVolume
import de.timklge.karoospintunes.AutoVolumeConfig
import de.timklge.karoospintunes.KarooSpintunesExtension
import de.timklge.karoospintunes.KarooSpintunesServices
import de.timklge.karoospintunes.KarooSystemServiceProvider
import de.timklge.karoospintunes.R
import de.timklge.karoospintunes.auth.OAuth2Client
import de.timklge.karoospintunes.auth.TokenResponse
import de.timklge.karoospintunes.datatypes.PlayerSize
import de.timklge.karoospintunes.datatypes.PlayerViewProvider
import de.timklge.karoospintunes.spotify.LocalClient
import de.timklge.karoospintunes.spotify.LocalClientConnectionState
import io.hammerhead.karooext.models.HardwareType
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onFinish: () -> Unit) {
    val localClient = koinInject<LocalClient>()
    val autoVolume = koinInject<AutoVolume>()
    val karooSystemServiceProvider = koinInject<KarooSystemServiceProvider>()
    val oauth2Client = koinInject<OAuth2Client>()
    val viewProvider = koinInject<PlayerViewProvider>()

    val ctx = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val karooConnected by karooSystemServiceProvider.connectionState.collectAsStateWithLifecycle()

    var welcomeDialogVisible by remember { mutableStateOf(false) }

    var token by remember { mutableStateOf<TokenResponse?>(null) }
    var settingsInitialized by remember { mutableStateOf(false) }
    var downloadThumbnails by remember { mutableStateOf(false) }
    val connectionState by localClient.connectionState.collectAsStateWithLifecycle(initialValue = LocalClientConnectionState.Idle)
    var enableLocalSpotify by remember { mutableStateOf(false) }
    var localSpotifyIsInstalled by remember { mutableStateOf(false) }
    var playerPreviewDialogVisible by remember { mutableStateOf(false) }

    var isImperial by remember { mutableStateOf(false) }

    var autoVolumeEnabled by remember { mutableStateOf(false) }
    var autoVolumeMinSpeed by remember { mutableStateOf("0") }
    var autoVolumeMaxSpeed by remember { mutableStateOf("0") }
    var autoVolumeMinVolume by remember { mutableStateOf("0") }
    var autoVolumeMaxVolume by remember { mutableStateOf("100") }

    val currentSpeed by karooSystemServiceProvider.streamSpeed().collectAsStateWithLifecycle(0)

    LaunchedEffect(Unit) {
        while (true){
            delay(30_000L)
        }
    }

    suspend fun updateSettings(){
        karooSystemServiceProvider.saveSettings { settings ->
            val minSpeedSetting = (autoVolumeMinSpeed.toIntOrNull()?.toFloat()?.div((if(isImperial) 2.23694f else 3.6f))) ?: AutoVolumeConfig.DEFAULT_MIN_VOLUME_AT_SPEED
            val maxSpeedSetting = (autoVolumeMaxSpeed.toIntOrNull()?.toFloat()?.div((if(isImperial) 2.23694f else 3.6f))) ?: AutoVolumeConfig.DEFAULT_MAX_VOLUME_AT_SPEED

            settings.copy(welcomeDialogAccepted = true,
                downloadThumbnailsViaCompanion = downloadThumbnails,
                useLocalSpotifyIfAvailable = enableLocalSpotify,
                autoVolumeConfig = AutoVolumeConfig(
                    enabled = autoVolumeEnabled,
                    minVolumeAtSpeed = minSpeedSetting,
                    maxVolumeAtSpeed = maxSpeedSetting,
                    minVolume = autoVolumeMinVolume.toIntOrNull()?.div(100f) ?: AutoVolumeConfig.DEFAULT_MIN_VOLUME,
                    maxVolume = autoVolumeMaxVolume.toIntOrNull()?.div(100f) ?: AutoVolumeConfig.DEFAULT_MAX_VOLUME)
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runBlocking {
                updateSettings()
            }
        }
    }

    BackHandler {
        coroutineScope.launch {
            updateSettings()
            onFinish()
        }
    }

    val owner = LocalLifecycleOwner.current

    DisposableEffect(Unit) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_START) {
                coroutineScope.launch {
                    localSpotifyIsInstalled = localClient.isInstalled()
                }
            }
        }

        val lifecycle = owner.lifecycle
        lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycle.removeObserver(lifecycleObserver)
        }
    }

    LaunchedEffect(Unit) {
        karooSystemServiceProvider.streamSettings().collect { settings ->
            welcomeDialogVisible = !settings.welcomeDialogAccepted
            token = settings.token
            settingsInitialized = true
            downloadThumbnails = settings.downloadThumbnailsViaCompanion
            enableLocalSpotify = settings.useLocalSpotifyIfAvailable
            autoVolumeEnabled = settings.autoVolumeConfig.enabled
            autoVolumeMinVolume = (settings.autoVolumeConfig.minVolume * 100).roundToInt().toString()
            autoVolumeMaxVolume = (settings.autoVolumeConfig.maxVolume * 100).roundToInt().toString()
            autoVolumeMinSpeed = (if(isImperial) settings.autoVolumeConfig.minVolumeAtSpeed * 2.23694f else settings.autoVolumeConfig.minVolumeAtSpeed * 3.6f).roundToInt().toString()
            autoVolumeMaxSpeed = (if(isImperial) settings.autoVolumeConfig.maxVolumeAtSpeed * 2.23694f else settings.autoVolumeConfig.maxVolumeAtSpeed * 3.6f).roundToInt().toString()
        }
    }

    LaunchedEffect(Unit) {
        karooSystemServiceProvider.streamUserProfile()
            .combine(karooSystemServiceProvider.streamSettings()) { profile, settings -> profile to settings}
            .distinctUntilChanged()
            .collect { (profile, _) ->
                isImperial = profile.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL
            }
    }

    Box(modifier = Modifier.fillMaxSize()){
        Column(modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)) {
            TopAppBar(title = { Text("Spintunes") })
            Column(modifier = Modifier
                .padding(10.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp))
            {

                if(settingsInitialized) {
                    if (token == null) {
                        Text("Please login to Spotify to enable the app.")

                        Spacer(modifier = Modifier.height(10.dp))

                        Text("You can then proceed to add a player widget to your data pages in your profile settings.")

                        Spacer(modifier = Modifier.height(10.dp))

                        FilledTonalButton(modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                            onClick = {
                                coroutineScope.launch {
                                    oauth2Client.startAuthFlow(ctx)
                                }
                            }) {
                            Icon(Icons.Default.Person, contentDescription = "Login")
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Login at Spotify")
                        }
                    } else {
                        Text("You are logged in to Spotify.")
                    }
                }

                if (token != null){
                    if (connectionState != LocalClientConnectionState.NotInstalled && localSpotifyIsInstalled && settingsInitialized){
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = enableLocalSpotify, onCheckedChange = {
                                enableLocalSpotify = it
                                runBlocking {
                                    updateSettings()
                                }
                            })
                            Spacer(modifier = Modifier.width(10.dp))

                            Text("Control Spotify app installed on Karoo")
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = downloadThumbnails, onCheckedChange = { downloadThumbnails = it})
                        Spacer(modifier = Modifier.width(10.dp))

                        if (karooSystemServiceProvider.karooSystemService.hardwareType == HardwareType.K2){
                            Text("Download thumbnails via mobile connection")
                        } else {
                            Text("Download thumbnails via companion app")
                        }
                    }

                    if (enableLocalSpotify && connectionState != LocalClientConnectionState.NotInstalled && settingsInitialized){
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = autoVolumeEnabled, onCheckedChange = { autoVolumeEnabled = it})
                            Spacer(modifier = Modifier.width(10.dp))

                            Text("Auto set volume based on speed")
                        }

                        if (autoVolumeEnabled){
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(value = autoVolumeMinSpeed, modifier = Modifier
                                    .weight(1f)
                                    .absolutePadding(right = 2.dp),
                                    onValueChange = { autoVolumeMinSpeed = it },
                                    label = { Text("Min Speed") },
                                    suffix = { Text(if (isImperial) "mph" else "kph") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true
                                )

                                OutlinedTextField(value = autoVolumeMaxSpeed, modifier = Modifier
                                    .weight(1f)
                                    .absolutePadding(left = 2.dp),
                                    onValueChange = { autoVolumeMaxSpeed = it },
                                    label = { Text("Max Speed") },
                                    suffix = { Text(if (isImperial) "mph" else "km/h") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(value = autoVolumeMinVolume, modifier = Modifier
                                    .weight(1f)
                                    .absolutePadding(right = 2.dp),
                                    onValueChange = { autoVolumeMinVolume = it },
                                    label = { Text("Min Vol") },
                                    suffix = { Text("%") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true
                                )

                                OutlinedTextField(value = autoVolumeMaxVolume, modifier = Modifier
                                    .weight(1f)
                                    .absolutePadding(left = 2.dp),
                                    onValueChange = { autoVolumeMaxVolume = it },
                                    label = { Text("Max Vol") },
                                    suffix = { Text("%") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true
                                )
                            }

                            val currentSpeedInUserUnit = if (isImperial) (currentSpeed * 2.23694f).roundToInt() else (currentSpeed * 3.6f).roundToInt()
                            val currentUserSpeedUnit = if (isImperial) "mph" else "km/h"
                            val currentAutoVolume = 100 * autoVolume.getVolumeForSpeed(AutoVolumeConfig(enabled = autoVolumeEnabled,
                                minVolume = autoVolumeMinVolume.toIntOrNull()?.div(100f) ?: AutoVolumeConfig.DEFAULT_MIN_VOLUME,
                                maxVolume = autoVolumeMaxVolume.toIntOrNull()?.div(100f) ?: AutoVolumeConfig.DEFAULT_MAX_VOLUME,
                                minVolumeAtSpeed = autoVolumeMinSpeed.toIntOrNull()?.toFloat()?.div((if(isImperial) 2.23694f else 3.6f)) ?: AutoVolumeConfig.DEFAULT_MIN_VOLUME_AT_SPEED,
                                maxVolumeAtSpeed = autoVolumeMaxSpeed.toIntOrNull()?.toFloat()?.div((if(isImperial) 2.23694f else 3.6f)) ?: AutoVolumeConfig.DEFAULT_MAX_VOLUME_AT_SPEED), currentSpeed.toFloat())

                            Text(text = "Current: $currentSpeedInUserUnit $currentUserSpeedUnit => ${currentAutoVolume.roundToInt()}%")
                        }
                    }
                }

                if (token != null && settingsInitialized) {
                    FilledTonalButton(modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp), onClick = {
                        playerPreviewDialogVisible = true
                    }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Preview")
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Preview Player")
                    }

                    FilledTonalButton(modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp), onClick = {
                        coroutineScope.launch {
                            karooSystemServiceProvider.saveSettings { settings ->
                                settings.copy(token = null)
                            }
                        }
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "Logout")
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Logout")
                    }
                }

                if (!karooConnected){
                    Text(modifier = Modifier.padding(5.dp), text = "Could not read device status. This app is supposed to be run on a Karoo bike computer.")
                }

                Log.d(KarooSpintunesExtension.TAG, "Connection state: $connectionState, localSpotifyIsInstalled: $localSpotifyIsInstalled, settingsInitialized: $settingsInitialized")

                if (localSpotifyIsInstalled && settingsInitialized){
                    when (connectionState){
                        is LocalClientConnectionState.Connecting -> {
                            Text(modifier = Modifier.padding(5.dp), text = "Trying to connect to local Spotify client...")
                        }
                        is LocalClientConnectionState.Connected -> {
                            Text(modifier = Modifier.padding(5.dp), text = "Connected to local Spotify client.")
                        }
                        is LocalClientConnectionState.Failed -> {
                            Text(modifier = Modifier.padding(5.dp), text = "Local Spotify connection failed: ${(connectionState as LocalClientConnectionState.Failed).message}")
                        }
                        is LocalClientConnectionState.NotInstalled -> {
                            Text(modifier = Modifier.padding(5.dp), text = "Local Spotify app is not installed.")
                        }
                        is LocalClientConnectionState.Idle -> {
                            Text(modifier = Modifier.padding(5.dp), text = "Local Spotify client is idle.")
                        }
                    }
                }

                Spacer(modifier = Modifier.padding(30.dp))
            }
         }

        Image(
            painter = painterResource(id = R.drawable.back),
            contentDescription = "Back",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 10.dp)
                .size(54.dp)
                .clickable {
                    onFinish()
                }
        )
    }

    if (welcomeDialogVisible){
        AlertDialog(onDismissRequest = { },
            confirmButton = { Button(onClick = {
                coroutineScope.launch {
                    karooSystemServiceProvider.saveSettings { settings -> settings.copy(welcomeDialogAccepted = true) }
                }
            }) { Text("OK") } },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Welcome to karoo-spintunes!")

                    Spacer(Modifier.padding(10.dp))

                    Text("Please note that this app requires an active internet connection during riding to control the Spotify app on your phone. To use it offline, please sideload the Spotify app on your Karoo.")
                }
            }
        )
    }

    if (playerPreviewDialogVisible){
        val services = koinInject<KarooSpintunesServices>()

        LaunchedEffect(Unit) {
            delay(500)
            if (!karooConnected){
                Log.i(KarooSpintunesExtension.TAG, "Player preview dialog opened (no Karoo)")
                services.startJobs()
            }
        }

        Dialog(onDismissRequest = { playerPreviewDialogVisible = false }, properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true, usePlatformDefaultWidth = false)) {
            Box(modifier = Modifier.padding(1.dp).height(400.dp).fillMaxWidth()) {
                Column(modifier = Modifier.align(Alignment.Center)) {
                    val view: RemoteViews? by viewProvider.provideView(PlayerSize.FULL_PAGE).collectAsStateWithLifecycle(initialValue = null)

                    AndroidView(
                        factory = { context ->
                            FrameLayout(context).apply {
                                layoutParams = FrameLayout.LayoutParams(
                                    480, // width
                                    400  // height
                                )

                                val addedView = view?.apply(context, this)
                                addedView?.let { v ->
                                    addView(v)
                                }
                            }
                        },
                        update = { frameLayout ->
                            frameLayout.removeAllViews()
                            val newView = view?.apply(frameLayout.context, frameLayout)
                            newView?.let { v -> frameLayout.addView(v) }
                        },
                        modifier = Modifier
                            .size(480.dp, 800.dp) // Force the AndroidView to this exact size
                            .background(if (isSystemInDarkTheme()) Color.Black else Color.White)
                    )
                }
            }
        }
    }
}