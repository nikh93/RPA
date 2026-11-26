package de.streamdemo.control

import android.content.Context
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import de.streamdemo.net.MjpegClient
import de.streamdemo.net.MjpegServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.io.IOException

private data class Target(val host: String, val port: Int, val attempt: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("control", Context.MODE_PRIVATE) }

    var host by remember { mutableStateOf(prefs.getString("host", "") ?: "") }
    var port by remember { mutableStateOf(prefs.getInt("port", MjpegServer.DEFAULT_PORT).toString()) }
    var target by remember { mutableStateOf<Target?>(null) }
    var attempts by remember { mutableIntStateOf(0) }

    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var fps by remember { mutableIntStateOf(0) }

    val portNumber = port.toIntOrNull()?.takeIf { it in 1..65535 }
    val canConnect = host.isNotBlank() && portNumber != null

    fun connect() {
        if (!canConnect) return
        prefs.edit {
            putString("host", host.trim())
            putInt("port", portNumber!!)
        }
        attempts++
        target = Target(host.trim(), portNumber!!, attempts)
    }

    // Empfang läuft, solange ein Ziel gesetzt ist. Neues Ziel = alte Verbindung wird beendet.
    LaunchedEffect(target) {
        val t = target ?: return@LaunchedEffect
        frame = null
        fps = 0
        status = "Verbinde mit ${t.host}:${t.port} …"
        status = try {
            receive(t) { image, currentFps ->
                frame = image
                fps = currentFps
                status = null
            }
            ensureActive()
            "Stream wurde beendet."
        } catch (e: IOException) {
            // Beim Trennen schließt sich der Socket absichtlich - dann keine Fehlermeldung.
            ensureActive()
            "Keine Verbindung: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Control") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("IP-Adresse des Stream-Geräts") },
                        placeholder = { Text("192.168.178.20") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go,
                        ),
                        keyboardActions = KeyboardActions(onGo = { connect() }),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Port") },
                        isError = portNumber == null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.45f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = canConnect, onClick = { connect() }) {
                        Text(if (target == null) "Verbinden" else "Neu verbinden")
                    }
                    if (target != null) {
                        OutlinedButton(onClick = {
                            target = null
                            frame = null
                            status = null
                        }) { Text("Trennen") }
                    }
                }
            }

            Box(
                Modifier.weight(1f).fillMaxWidth().background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                frame?.let {
                    Image(
                        bitmap = it,
                        contentDescription = "Empfangenes Kamerabild",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Text(
                        "$fps fps",
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .background(Color(0x99000000), MaterialTheme.shapes.small)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                status?.let {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (it.startsWith("Verbinde")) CircularProgressIndicator()
                        Text(it, color = Color.White, modifier = Modifier.padding(16.dp))
                    }
                }
                if (target == null && frame == null && status == null) {
                    Text(
                        "IP-Adresse eingeben, die im Stream-Modus angezeigt wird.",
                        color = Color.White,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
    }
}

/**
 * Empfängt Bilder, bis der Stream endet oder die Coroutine abgebrochen wird.
 * [onFrame] wird im UI-Thread aufgerufen.
 */
private suspend fun receive(target: Target, onFrame: (ImageBitmap, Int) -> Unit) {
    withContext(Dispatchers.IO) {
        val client = MjpegClient.connect(target.host, target.port)
        // Beim Abbrechen (Trennen, Zurück) den Socket schließen, damit read() sofort endet.
        val closer = currentCoroutineContext().job.invokeOnCompletion { client.close() }
        try {
            var windowStart = SystemClock.elapsedRealtime()
            var framesInWindow = 0
            var currentFps = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                val jpeg = client.readFrame() ?: break
                val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: continue
                framesInWindow++
                val now = SystemClock.elapsedRealtime()
                if (now - windowStart >= 1_000) {
                    currentFps = framesInWindow
                    framesInWindow = 0
                    windowStart = now
                }
                val image = bitmap.asImageBitmap()
                withContext(Dispatchers.Main) { onFrame(image, currentFps) }
            }
        } finally {
            closer.dispose()
            client.close()
        }
    }
}
