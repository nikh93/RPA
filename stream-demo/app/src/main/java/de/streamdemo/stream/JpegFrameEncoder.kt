package de.streamdemo.stream

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import de.streamdemo.net.MjpegServer
import java.io.ByteArrayOutputStream

/**
 * Nimmt Kamerabilder von CameraX entgegen, wandelt sie in JPEG um
 * und gibt sie an den MJPEG-Server weiter.
 */
class JpegFrameEncoder(
    private val server: MjpegServer,
    private val maxFps: Int = 15,
    private val quality: Int = 70,
) : ImageAnalysis.Analyzer {

    private var lastFrameAt = 0L
    private val buffer = ByteArrayOutputStream(128 * 1024)

    override fun analyze(image: ImageProxy) {
        image.use {
            val now = SystemClock.elapsedRealtime()
            // Ohne Zuschauer nichts kodieren (spart Akku), sonst auf maxFps drosseln.
            if (server.clientCount == 0 || now - lastFrameAt < 1_000L / maxFps) return
            lastFrameAt = now

            val bitmap = rotate(image.toBitmap(), image.imageInfo.rotationDegrees)
            buffer.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, buffer)
            server.publish(buffer.toByteArray())
        }
    }

    private fun rotate(source: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return source
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }
}
