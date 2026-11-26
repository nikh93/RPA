package de.streamdemo.net

import java.io.BufferedOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Minimaler MJPEG-Server ("Motion JPEG über HTTP").
 *
 * Jeder Client bekommt eine HTTP-Antwort vom Typ multipart/x-mixed-replace,
 * in der ein JPEG-Bild nach dem anderen folgt. Das versteht die Control-Seite
 * dieser App, aber auch jeder Browser (einfach http://<ip>:8080 öffnen) und VLC.
 */
class MjpegServer(private val port: Int = DEFAULT_PORT) {

    private val lock = Object()
    private var latestFrame: ByteArray? = null
    private var frameSeq = 0L // 0 = noch kein Bild

    @Volatile
    private var running = false
    private var serverSocket: ServerSocket? = null
    private val receivers = mutableListOf<String>() // IP-Adressen der verbundenen Empfänger

    /**
     * Wird (auf einem Hintergrund-Thread) aufgerufen, wenn sich ein Empfänger
     * verbindet oder trennt. Übergeben wird die Liste der verbundenen IP-Adressen.
     */
    @Volatile
    var onReceiversChanged: ((List<String>) -> Unit)? = null

    val clientCount: Int get() = synchronized(receivers) { receivers.size }

    /** Tatsächlich belegter Port (wichtig, wenn mit Port 0 gestartet wurde). */
    val localPort: Int get() = serverSocket?.localPort ?: port

    @Throws(IOException::class)
    fun start() {
        val socket = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(port))
        }
        serverSocket = socket
        running = true
        thread(name = "mjpeg-accept", isDaemon = true) { acceptLoop(socket) }
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: IOException) {
        }
        synchronized(lock) { lock.notifyAll() }
    }

    /** Neues Kamerabild an alle Zuschauer verteilen. */
    fun publish(jpeg: ByteArray) {
        synchronized(lock) {
            latestFrame = jpeg
            frameSeq++
            lock.notifyAll()
        }
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running) {
            val client = try {
                socket.accept()
            } catch (_: IOException) {
                break // Server wurde gestoppt
            }
            thread(name = "mjpeg-client", isDaemon = true) { serve(client) }
        }
    }

    private fun serve(socket: Socket): Unit = socket.use {
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = 5_000
            // HTTP-Anfrage lesen und ignorieren - jeder Pfad liefert den Stream.
            val reader = MjpegReader(socket.getInputStream())
            reader.readLine() ?: return
            while (true) {
                val header = reader.readLine() ?: return
                if (header.isEmpty()) break
            }

            val out = BufferedOutputStream(socket.getOutputStream())
            out.write(
                ("HTTP/1.0 200 OK\r\n" +
                    "Content-Type: multipart/x-mixed-replace; boundary=$BOUNDARY\r\n" +
                    "Cache-Control: no-cache, no-store\r\n" +
                    "Pragma: no-cache\r\n" +
                    "Connection: close\r\n\r\n").toByteArray(Charsets.ISO_8859_1),
            )
            out.flush()

            val address = socket.inetAddress?.hostAddress ?: "?"
            changeReceivers { it += address }
            try {
                var sentSeq = 0L
                while (running) {
                    val frame: ByteArray = synchronized(lock) {
                        while (running && frameSeq == sentSeq) lock.wait(1_000)
                        if (!running) return
                        sentSeq = frameSeq
                        latestFrame!!
                    }
                    out.write(
                        ("--$BOUNDARY\r\n" +
                            "Content-Type: image/jpeg\r\n" +
                            "Content-Length: ${frame.size}\r\n\r\n").toByteArray(Charsets.ISO_8859_1),
                    )
                    out.write(frame)
                    out.write(CRLF)
                    out.flush()
                }
            } finally {
                changeReceivers { it -= address }
            }
        } catch (_: IOException) {
            // Zuschauer hat die Verbindung getrennt
        } catch (_: InterruptedException) {
        }
    }

    private fun changeReceivers(change: (MutableList<String>) -> Unit) {
        // Im Lock melden, damit Änderungen in der richtigen Reihenfolge ankommen.
        synchronized(receivers) {
            change(receivers)
            onReceiversChanged?.invoke(receivers.toList())
        }
    }

    companion object {
        const val DEFAULT_PORT = 8080
        const val BOUNDARY = "streamdemoframe"
        private val CRLF = "\r\n".toByteArray(Charsets.ISO_8859_1)

        /** IPv4-Adressen dieses Geräts im lokalen Netz (WLAN, Hotspot, Ethernet). */
        fun localIpAddresses(): List<String> = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .mapNotNull { it.hostAddress }
        } catch (_: IOException) {
            emptyList()
        }
    }
}
