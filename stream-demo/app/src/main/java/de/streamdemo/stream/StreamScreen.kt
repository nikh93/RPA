package de.streamdemo.stream

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.streamdemo.net.MjpegServer
import java.io.IOException
import java.util.concurrent.Executors

private const val PORT = MjpegServer.DEFAULT_PORT

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { hasPermission = it }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var streaming by remember { mutableStateOf(false) }
    var useFrontCamera by remember { mutableStateOf(false) }
    var receivers by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    val previewView = remember { PreviewView(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { analysisExecutor.shutdown() } }

    // Bildschirm anlassen, solange gestreamt wird.
    val view = LocalView.current
    DisposableEffect(streaming) {
        view.keepScreenOn = streaming
        onDispose { view.keepScreenOn = false }
    }

    // --- Server: läuft nur, solange "streaming" an ist ---------------------------
    val server = remember(streaming) { if (streaming) MjpegServer(PORT) else null }
    DisposableEffect(server) {
        val mainHandler = Handler(Looper.getMainLooper())
        if (server != null) {
            server.onReceiversChanged = { list -> mainHandler.post { receivers = list } }
            try {
                server.start()
                error = null
            } catch (e: IOException) {
                error = "Server konnte nicht starten: ${e.message}"
                streaming = false
            }
        }
        onDispose {
            server?.stop()
            receivers = emptyList()
        }
    }

    // --- Kamera: Vorschau immer, Bildanalyse (= Senden) nur mit laufendem Server --
    DisposableEffect(hasPermission, useFrontCamera, server) {
        if (!hasPermission) return@DisposableEffect onDispose { }
        var disposed = false
        var provider: ProcessCameraProvider? = null
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            if (disposed) return@addListener
            val cameraProvider = providerFuture.get().also { provider = it }
            val useCases = mutableListOf<UseCase>(
                Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) },
            )
            if (server != null) {
                useCases += ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(640, 480),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                ),
                            )
                            .build(),
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, JpegFrameEncoder(server)) }
            }
            val selector = if (useFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, *useCases.toTypedArray())
            } catch (e: Exception) {
                error = "Kamera nicht verfügbar: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            provider?.unbindAll()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stream") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { useFrontCamera = !useFrontCamera }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Kamera wechseln")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Box(
                Modifier.weight(1f).fillMaxWidth().background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (hasPermission) {
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Kamera-Berechtigung fehlt", color = Color.White)
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Erlauben")
                        }
                    }
                }
                if (streaming) {
                    Text(
                        "● LIVE",
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .background(Color(0xCCD32F2F), MaterialTheme.shapes.small)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (streaming) {
                    ReceiverStatus(receivers)
                    val addresses = remember(streaming) { MjpegServer.localIpAddresses() }
                    Text("Im Control-Modus eingeben:", style = MaterialTheme.typography.labelLarge)
                    if (addresses.isEmpty()) {
                        Text("Keine Netzwerkverbindung gefunden – ist WLAN an?")
                    }
                    addresses.forEach { ip ->
                        Text(
                            "$ip  (Port $PORT)",
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Text(
                        "Auch im Browser: http://${addresses.firstOrNull() ?: "<ip>"}:$PORT",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth()) {
                    Button(
                        enabled = hasPermission,
                        onClick = { streaming = !streaming },
                        colors = if (streaming) {
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        } else {
                            ButtonDefaults.buttonColors()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (streaming) "Aufnahme stoppen" else "Aufnahme starten",
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** Gut sichtbare Anzeige, ob gerade ein Empfänger (Control-Handy) zuschaut. */
@Composable
private fun ReceiverStatus(receivers: List<String>) {
    val connected = receivers.isNotEmpty()
    val background = if (connected) Color(0xFF2E7D32) else Color(0xFFF9A825)
    val foreground = if (connected) Color.White else Color.Black
    Row(
        Modifier
            .fillMaxWidth()
            .background(background, MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (connected) "✔" else "…", color = foreground, style = MaterialTheme.typography.titleLarge)
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                when (receivers.size) {
                    0 -> "Warte auf Empfänger"
                    1 -> "Empfänger verbunden"
                    else -> "${receivers.size} Empfänger verbunden"
                },
                color = foreground,
                style = MaterialTheme.typography.titleMedium,
            )
            if (connected) {
                Text(receivers.distinct().joinToString(), color = foreground, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
