package de.streamdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.streamdemo.control.ControlScreen
import de.streamdemo.stream.StreamScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                Surface(Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

private enum class Mode { STREAM, CONTROL }

@Composable
private fun App() {
    var mode by rememberSaveable { mutableStateOf<Mode?>(null) }

    BackHandler(enabled = mode != null) { mode = null }

    when (mode) {
        Mode.STREAM -> StreamScreen(onBack = { mode = null })
        Mode.CONTROL -> ControlScreen(onBack = { mode = null })
        null -> ModeSelection(onSelect = { mode = it })
    }
}

@Composable
private fun ModeSelection(onSelect: (Mode) -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("StreamDemo", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Beide Geräte müssen im selben WLAN sein.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = { onSelect(Mode.STREAM) },
            modifier = Modifier.fillMaxWidth().height(72.dp),
        ) {
            Text("Stream\nKamera senden", textAlign = TextAlign.Center)
        }
        OutlinedButton(
            onClick = { onSelect(Mode.CONTROL) },
            modifier = Modifier.fillMaxWidth().height(72.dp),
        ) {
            Text("Control\nStream empfangen", textAlign = TextAlign.Center)
        }
    }
}
