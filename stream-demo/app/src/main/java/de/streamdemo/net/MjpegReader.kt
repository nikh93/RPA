package de.streamdemo.net

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Liest Zeilen und JPEG-Bilder aus einem MJPEG-Datenstrom (multipart/x-mixed-replace).
 *
 * Bevorzugt wird der Content-Length-Header jedes Bildes. Fehlt er (manche Kameras
 * senden keinen), wird bis zum JPEG-Endmarker FF D9 gelesen.
 */
class MjpegReader(input: InputStream) {
    private val input = BufferedInputStream(input, 64 * 1024)

    /** Eine Textzeile ohne CR/LF, oder null am Ende des Datenstroms. */
    fun readLine(): String? {
        val line = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (line.isEmpty()) null else line.toString()
            if (b == '\n'.code) return line.toString().trimEnd('\r')
            if (line.length >= MAX_LINE) throw IOException("Ungültige Antwort (Zeile zu lang)")
            line.append(b.toChar())
        }
    }

    /** Nächstes JPEG-Bild, oder null wenn der Stream zu Ende ist. */
    fun readFrame(): ByteArray? {
        var contentLength = -1
        var inPartHeader = false
        while (true) {
            val line = readLine() ?: return null
            when {
                line.isEmpty() -> if (inPartHeader) break // Ende der Bild-Header
                line.startsWith("--") -> inPartHeader = true // Trennzeile (Boundary)
                line.startsWith("content-length:", ignoreCase = true) -> {
                    contentLength = line.substringAfter(':').trim().toIntOrNull() ?: -1
                    inPartHeader = true
                }
                line.contains(':') -> inPartHeader = true // sonstiger Header
            }
        }
        if (contentLength > MAX_FRAME) throw IOException("Bild zu groß ($contentLength Bytes)")
        return if (contentLength >= 0) readExactly(contentLength) else readUntilJpegEnd()
    }

    private fun readExactly(length: Int): ByteArray? {
        val data = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val n = input.read(data, offset, length - offset)
            if (n == -1) return null
            offset += n
        }
        return data
    }

    private fun readUntilJpegEnd(): ByteArray? {
        val out = ByteArrayOutputStream()
        var previous = -1
        while (true) {
            val b = input.read()
            if (b == -1) return null
            out.write(b)
            if (previous == 0xFF && b == 0xD9) return out.toByteArray()
            if (out.size() > MAX_FRAME) throw IOException("Bild zu groß")
            previous = b
        }
    }

    private companion object {
        const val MAX_LINE = 8 * 1024
        const val MAX_FRAME = 10 * 1024 * 1024
    }
}

/** Verbindung der Control-Seite zu einem Stream-Gerät. */
class MjpegClient private constructor(private val socket: Socket) : Closeable {
    private val reader = MjpegReader(socket.getInputStream())

    fun readFrame(): ByteArray? = reader.readFrame()

    override fun close() = socket.close()

    private fun readResponseHeader() {
        val status = reader.readLine() ?: throw IOException("Keine Antwort vom Gerät")
        val code = status.split(' ').getOrNull(1)?.toIntOrNull()
        if (code != 200) throw IOException("Unerwartete Antwort: $status")
        while (true) {
            val line = reader.readLine() ?: throw IOException("Verbindung abgebrochen")
            if (line.isEmpty()) return
        }
    }

    companion object {
        /** Baut die Verbindung auf und liest die HTTP-Antwort. Blockiert - nicht im UI-Thread aufrufen. */
        @Throws(IOException::class)
        fun connect(host: String, port: Int, path: String = "/"): MjpegClient {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(host, port), 5_000)
                socket.soTimeout = 10_000
                socket.tcpNoDelay = true
                socket.getOutputStream().apply {
                    write("GET $path HTTP/1.0\r\nHost: $host:$port\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
                    flush()
                }
                return MjpegClient(socket).also { it.readResponseHeader() }
            } catch (e: IOException) {
                socket.close()
                throw e
            }
        }
    }
}
