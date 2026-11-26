package de.heimkamera.ui

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(title: String, rtspUrl: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    // Hochzählen erzeugt einen neuen Player (Neuverbindung).
    var attempt by remember { mutableIntStateOf(0) }
    var stopped by remember { mutableStateOf(false) }

    val player = remember(rtspUrl, attempt) { createPlayer(context, rtspUrl) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                playing = false
                error = "Live-Bild nicht verfügbar (${e.errorCodeName})"
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                if (isPlaying) error = null
            }
        }
        player.addListener(listener)
        player.prepare()
        player.playWhenReady = true
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // App im Hintergrund: Stream beenden. Zurück im Vordergrund: neu verbinden,
    // damit man wieder das aktuelle Bild sieht und nicht einen alten Puffer.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        stopped = true
        player.stop()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (stopped) {
            stopped = false
            error = null
            attempt++
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier.padding(padding).fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        keepScreenOn = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize(),
            )
            val message = error
            when {
                message != null -> Box(Modifier.background(MaterialTheme.colorScheme.surface)) {
                    Message(text = message, action = "Erneut verbinden", onAction = {
                        error = null
                        attempt++
                    })
                }

                !playing -> CircularProgressIndicator()
            }
        }
    }
}

@OptIn(UnstableApi::class)
private fun createPlayer(context: Context, rtspUrl: String): ExoPlayer {
    // Kleiner Puffer = geringe Verzögerung zum echten Geschehen.
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(500, 2_000, 250, 250)
        .build()
    val source = RtspMediaSource.Factory()
        // TCP statt UDP: funktioniert zuverlässig über Tailscale und Mobilfunk.
        .setForceUseRtpTcp(true)
        .setTimeoutMs(8_000)
        .createMediaSource(MediaItem.fromUri(rtspUrl))
    return ExoPlayer.Builder(context)
        .setLoadControl(loadControl)
        .build()
        .apply {
            setMediaSource(source)
            volume = 0f // Ton aus (Mikrofon in den Kameras am besten ganz abschalten)
        }
}
