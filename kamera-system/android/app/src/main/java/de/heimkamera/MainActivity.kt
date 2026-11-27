package de.heimkamera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.heimkamera.ui.CameraListScreen
import de.heimkamera.ui.LiveScreen
import de.heimkamera.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = ConfigStore(this)
        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                App(store)
            }
        }
    }
}

private sealed interface Screen {
    data object Cameras : Screen
    data class Live(val stream: String) : Screen
    data object Settings : Screen
}

@Composable
private fun App(store: ConfigStore) {
    var config by remember { mutableStateOf(store.load()) }
    var screen by remember {
        mutableStateOf<Screen>(if (config.isComplete) Screen.Cameras else Screen.Settings)
    }
    val client = remember(config) { Go2RtcClient(config) }

    // Android 17+: Zugriff aufs Heimnetz (Pi im WLAN) braucht eine eigene Berechtigung.
    val localNetwork = rememberLocalNetworkAccess()
    LaunchedEffect(Unit) { localNetwork.runWithPermission { } }

    when (val current = screen) {
        Screen.Cameras -> CameraListScreen(
            client = client,
            onOpenCamera = { screen = Screen.Live(it) },
            onOpenSettings = { screen = Screen.Settings },
        )

        is Screen.Live -> {
            BackHandler { screen = Screen.Cameras }
            LiveScreen(
                title = Go2RtcClient.displayName(current.stream),
                rtspUrl = client.rtspUrl(current.stream),
                onBack = { screen = Screen.Cameras },
            )
        }

        Screen.Settings -> {
            val canGoBack = config.isComplete
            BackHandler(enabled = canGoBack) { screen = Screen.Cameras }
            SettingsScreen(
                initial = config,
                onTest = { Go2RtcClient(it).streams() },
                onSave = {
                    store.save(it)
                    config = it
                    screen = Screen.Cameras
                },
                onBack = if (canGoBack) ({ screen = Screen.Cameras }) else null,
            )
        }
    }
}
