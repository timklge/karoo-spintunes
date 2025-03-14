package de.timklge.karoospintunes.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.timklge.karoospintunes.KarooSpintunesExtension
import de.timklge.karoospintunes.theme.AppTheme
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.KoinContext
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OAuthRedirectScreen(intent: Intent, finish: () -> Unit) {
    val ctx = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var dialogVisible by remember { mutableStateOf(false) }
    var dialogMessage by remember { mutableStateOf("") }

    val oAuth2Client = koinInject<OAuth2Client>()

    LaunchedEffect(Unit) {
        val uri = intent.data

        Log.i(KarooSpintunesExtension.TAG, "Received OAuth redirect: $uri")

        if (uri != null) {
            val code = uri.getQueryParameter("code")
            val state = uri.getQueryParameter("state")

            // TODO Verify state matches what was sent
            if (code != null) {
                coroutineScope.launch {
                    val karooSystemService = KarooSystemService(ctx)

                    var connected = false
                    fun onConnected(){
                        if (connected) return
                        connected = true
                        coroutineScope.launch innerLaunch@{
                            try {
                                oAuth2Client.exchangeCodeForToken(code, ctx)
                            } catch (e: Throwable) {
                                dialogMessage = e.message ?: "Failed to authorize with Spotify"
                                dialogVisible = true

                                return@innerLaunch
                            }

                            finish()
                        }
                    }

                    karooSystemService.connect { onConnected() }

                    launch {
                        delay(5_000)
                        onConnected()
                    }
                }
            } else {
                dialogMessage = "Failed to authorize with Spotify"
                dialogVisible = true
            }
        } else {
            dialogMessage = "Failed to authorize with Spotify"
            dialogVisible = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(title = { Text("Spintunes") })
        Column(
            modifier = Modifier
                .padding(10.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Please wait...")

            Spacer(modifier = Modifier.padding(10.dp))

            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }

    if (dialogVisible){
        AlertDialog(onDismissRequest = { dialogVisible = false },
            confirmButton = { Button(onClick = {
                finish()
            }) { Text("OK") } },
            text = { Text(dialogMessage) }
        )
    }
}

class OAuthRedirectActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                KoinContext {
                    OAuthRedirectScreen(intent, ::finish)
                }
            }
        }
    }
}