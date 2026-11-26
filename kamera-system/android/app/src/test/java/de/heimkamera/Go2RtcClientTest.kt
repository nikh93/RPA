package de.heimkamera

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class Go2RtcClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun client(user: String = "admin", password: String = "geheim") = Go2RtcClient(
        PiConfig(host = server.hostName, apiPort = server.port, user = user, password = password),
    )

    @Test
    fun parsesStreamNamesFromGo2RtcResponse() {
        // Antwortformat von go2rtc: Objekt mit Kameranamen als Schlüssel
        val json = """
            {
              "wohnzimmer": {"producers": [{"url": "rtsp://192.168.178.51:554/stream1"}], "consumers": null},
              "kueche": {"producers": [{"url": "http://192.168.178.53:81/stream"}], "consumers": []},
              "schlafzimmer": {"producers": null, "consumers": null}
            }
        """.trimIndent()
        assertEquals(
            listOf("kueche", "schlafzimmer", "wohnzimmer"),
            Go2RtcClient.parseStreamNames(json),
        )
    }

    @Test
    fun emptyConfigGivesEmptyList() {
        assertEquals(emptyList<String>(), Go2RtcClient.parseStreamNames("{}"))
    }

    @Test
    fun streamsSendsBasicAuth() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"flur":{}}""").build())

        assertEquals(listOf("flur"), client().streams())

        val request = server.takeRequest()
        assertEquals("/api/streams", request.target)
        assertEquals("Basic YWRtaW46Z2VoZWlt", request.headers["Authorization"])
    }

    @Test
    fun wrongPasswordGivesReadableError() = runTest {
        server.enqueue(MockResponse.Builder().code(401).build())
        try {
            client().streams()
            fail("Exception erwartet")
        } catch (e: Go2RtcException) {
            assertEquals("Benutzername oder Passwort falsch", e.message)
        }
    }

    @Test
    fun unreachablePiGivesReadableError() = runTest {
        val port = server.port
        server.close()
        try {
            Go2RtcClient(PiConfig(host = "127.0.0.1", apiPort = port)).streams()
            fail("Exception erwartet")
        } catch (e: Go2RtcException) {
            assertTrue(e.message!!.startsWith("Pi nicht erreichbar"))
        }
    }

    @Test
    fun snapshotRequestsFrameOfStream() = runTest {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        server.enqueue(MockResponse.Builder().body(okio.Buffer().write(jpeg)).build())

        val bytes = client().snapshot("wohn zimmer")

        assertTrue(bytes.contentEquals(jpeg))
        assertEquals("/api/frame.jpeg?src=wohn%20zimmer", server.takeRequest().target)
    }

    @Test
    fun rtspUrlContainsEncodedCredentials() {
        val c = Go2RtcClient(PiConfig(host = "100.64.0.10", user = "admin", password = "p@ss:wort"))
        assertEquals(
            "rtsp://admin:p%40ss%3Awort@100.64.0.10:8554/wohnzimmer",
            c.rtspUrl("wohnzimmer"),
        )
    }

    @Test
    fun ipv6HostIsBracketed() {
        val c = Go2RtcClient(PiConfig(host = "fd7a:115c::1", user = ""))
        assertEquals("rtsp://[fd7a:115c::1]:8554/kueche", c.rtspUrl("kueche"))
    }

    @Test
    fun displayNameIsCapitalized() {
        assertEquals("Wohn zimmer", Go2RtcClient.displayName("wohn_zimmer"))
    }
}
