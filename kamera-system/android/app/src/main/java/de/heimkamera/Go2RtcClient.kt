package de.heimkamera

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Fehler mit einer Meldung, die direkt in der App angezeigt werden kann. */
class Go2RtcException(message: String) : Exception(message)

/**
 * Spricht mit go2rtc auf dem Raspberry Pi.
 *
 *  - GET /api/streams          -> Liste der Kameras
 *  - GET /api/frame.jpeg?src=X -> aktuelles Standbild einer Kamera
 *  - rtsp://host:8554/X         -> Live-Video (wird vom Player geöffnet)
 */
class Go2RtcClient(
    private val config: PiConfig,
    private val http: OkHttpClient = defaultHttp,
) {
    private val baseUrl = "http://${config.hostForUrl}:${config.apiPort}"

    /** Namen aller Kameras, die in go2rtc.yaml eingetragen sind (alphabetisch). */
    suspend fun streams(): List<String> = withContext(Dispatchers.IO) {
        parseStreamNames(get("/api/streams").decodeToString())
    }

    /** Aktuelles Standbild als JPEG. */
    suspend fun snapshot(stream: String): ByteArray = withContext(Dispatchers.IO) {
        get("/api/frame.jpeg?src=${encode(stream)}")
    }

    /** RTSP-Adresse für den Live-Player, inklusive Zugangsdaten. */
    fun rtspUrl(stream: String): String {
        val auth = if (config.user.isNotEmpty()) {
            "${encode(config.user)}:${encode(config.password)}@"
        } else {
            ""
        }
        return "rtsp://$auth${config.hostForUrl}:${config.rtspPort}/${encode(stream)}"
    }

    private fun get(path: String): ByteArray {
        val request = Request.Builder()
            .url(baseUrl + path)
            .apply {
                if (config.user.isNotEmpty()) {
                    header("Authorization", Credentials.basic(config.user, config.password))
                }
            }
            .build()

        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw Go2RtcException("Pi nicht erreichbar (${e.message ?: e.javaClass.simpleName})")
        }
        response.use {
            when {
                it.code == 401 -> throw Go2RtcException("Benutzername oder Passwort falsch")
                !it.isSuccessful -> throw Go2RtcException("Pi meldet Fehler ${it.code}")
                else -> try {
                    return it.body.bytes()
                } catch (e: IOException) {
                    throw Go2RtcException("Verbindung abgebrochen (${e.message ?: e.javaClass.simpleName})")
                }
            }
        }
    }

    companion object {
        val defaultHttp: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            // Das erste Standbild kann einige Sekunden dauern (Kamera muss erst verbinden).
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        /** go2rtc liefert ein JSON-Objekt, dessen Schlüssel die Kameranamen sind. */
        fun parseStreamNames(json: String): List<String> =
            JSONObject(json).keys().asSequence().toList().sorted()

        /** Prozent-Kodierung für URL-Bestandteile (Leerzeichen als %20, nicht +). */
        fun encode(value: String): String =
            URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

        /** "wohn_zimmer" -> "Wohn zimmer" */
        fun displayName(stream: String): String =
            stream.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}
