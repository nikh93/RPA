package de.heimkamera.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import de.heimkamera.Go2RtcClient
import de.heimkamera.Go2RtcException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Wie oft die Vorschaubilder in der Übersicht erneuert werden. */
private const val SNAPSHOT_INTERVAL_MS = 3_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraListScreen(
    client: Go2RtcClient,
    onOpenCamera: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var streams by remember(client) { mutableStateOf<List<String>?>(null) }
    var error by remember(client) { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(client, reload) {
        error = null
        try {
            streams = client.streams()
        } catch (e: Go2RtcException) {
            error = e.message
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HeimKamera") },
                actions = {
                    IconButton(onClick = { reload++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Neu laden")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                    }
                },
            )
        },
    ) { padding ->
        val list = streams
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                error != null -> Message(
                    text = "${error}\n\nIst Tailscale auf dem Handy aktiv und der Pi eingeschaltet?",
                    action = "Erneut versuchen",
                    onAction = { reload++ },
                )

                list == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                list.isEmpty() -> Message(
                    text = "Auf dem Pi sind noch keine Kameras eingetragen.\n" +
                        "Trage sie in /etc/go2rtc/go2rtc.yaml ein.",
                    action = "Neu laden",
                    onAction = { reload++ },
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 320.dp),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(list, key = { it }) { stream ->
                        CameraTile(client, stream, onClick = { onOpenCamera(stream) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraTile(client: Go2RtcClient, stream: String, onClick: () -> Unit) {
    var image by remember(client, stream) { mutableStateOf<ImageBitmap?>(null) }
    var offline by remember(client, stream) { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Nur aktualisieren, solange die App sichtbar ist (spart Akku und mobile Daten).
    LaunchedEffect(client, stream, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                try {
                    val bytes = client.snapshot(stream)
                    val bitmap = withContext(Dispatchers.Default) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    if (bitmap != null) image = bitmap.asImageBitmap()
                    offline = bitmap == null
                } catch (e: Go2RtcException) {
                    offline = true
                }
                delay(SNAPSHOT_INTERVAL_MS)
            }
        }
    }

    Card(onClick = onClick) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val current = image
            if (current != null) {
                Image(
                    bitmap = current,
                    contentDescription = Go2RtcClient.displayName(stream),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (!offline) {
                CircularProgressIndicator()
            }
            if (offline) {
                Text(
                    text = "Kamera nicht erreichbar",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(Color(0xAA000000))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Go2RtcClient.displayName(stream), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Text("Live", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun Message(text: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, textAlign = TextAlign.Center)
        Spacer(Modifier.padding(8.dp))
        Button(onClick = onAction) { Text(action) }
    }
}
