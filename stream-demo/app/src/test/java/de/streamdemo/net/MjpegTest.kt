package de.streamdemo.net

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class MjpegTest {

    private val server = MjpegServer(port = 0) // 0 = freien Port wählen

    @After
    fun tearDown() = server.stop()

    private fun fakeJpeg(id: Int) =
        byteArrayOf(0xFF.toByte(), 0xD8.toByte(), id.toByte(), 0xFF.toByte(), 0xD9.toByte(), id.toByte(), 0xFF.toByte(), 0xD9.toByte())

    /** Veröffentlicht so lange Bilder, bis [done] true liefert (simuliert die Kamera). */
    private fun publishUntil(done: () -> Boolean, frame: (Int) -> ByteArray) {
        var i = 0
        val deadline = System.currentTimeMillis() + 5_000
        do {
            server.publish(frame(i++))
            if (System.currentTimeMillis() > deadline) fail("Timeout")
            Thread.sleep(20)
        } while (!done())
    }

    @Test
    fun clientReceivesPublishedFramesFromServer() {
        server.start()
        val received = mutableListOf<ByteArray>()
        val gotThree = CountDownLatch(1)

        val reader = thread {
            MjpegClient.connect("127.0.0.1", server.localPort).use { client ->
                repeat(3) { received += client.readFrame()!! }
            }
            gotThree.countDown()
        }
        publishUntil({ gotThree.count == 0L }) { fakeJpeg(it) }
        reader.join(1_000)

        assertEquals(3, received.size)
        received.forEach { frame ->
            // Jedes Bild komplett (inkl. eingebettetem FF D9) und unverändert angekommen
            assertEquals(8, frame.size)
            assertArrayEquals(fakeJpeg(frame[2].toInt()), frame)
        }
    }

    @Test
    fun clientCountGoesUpAndDown() {
        val counts = mutableListOf<Int>()
        server.onClientCountChanged = { synchronized(counts) { counts += it } }
        server.start()

        val client = MjpegClient.connect("127.0.0.1", server.localPort)
        publishUntil({ server.clientCount == 1 }) { fakeJpeg(it) }
        client.readFrame()
        client.close()
        // Server merkt das Trennen beim nächsten Schreibversuch
        publishUntil({ server.clientCount == 0 }) { fakeJpeg(it) }

        synchronized(counts) { assertEquals(listOf(1, 0), counts) }
    }

    @Test
    fun readerFallsBackToJpegEndMarkerWithoutContentLength() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())
        val stream = "--b\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray() + jpeg + "\r\n--b\r\n".toByteArray()
        val reader = MjpegReader(ByteArrayInputStream(stream))

        assertArrayEquals(jpeg, reader.readFrame())
        assertNull(reader.readFrame()) // Stream zu Ende
    }

    @Test
    fun connectFailsOnHttpError() {
        val fake = ServerSocket(0)
        thread {
            fake.accept().use { s ->
                s.getOutputStream().write("HTTP/1.0 404 Not Found\r\n\r\n".toByteArray())
            }
        }
        try {
            MjpegClient.connect("127.0.0.1", fake.localPort)
            fail("IOException erwartet")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("404"))
        } finally {
            fake.close()
        }
    }

    @Test
    fun connectFailsWhenNothingListens() {
        val port = ServerSocket(0).use { it.localPort }
        try {
            MjpegClient.connect("127.0.0.1", port)
            fail("IOException erwartet")
        } catch (_: IOException) {
        }
    }

    @Test
    fun stoppedServerEndsStream() {
        server.start()
        val client = MjpegClient.connect("127.0.0.1", server.localPort)
        publishUntil({ server.clientCount == 1 }) { fakeJpeg(it) }
        client.readFrame()
        server.stop()
        val ended = CountDownLatch(1)
        thread {
            try {
                while (client.readFrame() != null) { /* Rest leeren */ }
            } catch (_: IOException) {
            }
            ended.countDown()
        }
        assertTrue("Stream sollte nach stop() enden", ended.await(3, TimeUnit.SECONDS))
        client.close()
    }
}
