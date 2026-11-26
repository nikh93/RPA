package de.heimkamera

import android.content.Context
import androidx.core.content.edit

/** Verbindungsdaten zum Raspberry Pi (go2rtc). */
data class PiConfig(
    /** Tailscale-IP / -Name des Pi (z. B. 100.101.102.103) oder seine IP im Heim-WLAN. */
    val host: String = "",
    val apiPort: Int = 1984,
    val rtspPort: Int = 8554,
    val user: String = "admin",
    val password: String = "",
) {
    val isComplete: Boolean get() = host.isNotBlank()

    /** IPv6-Adressen müssen in URLs in eckigen Klammern stehen. */
    val hostForUrl: String
        get() = host.trim().let { if (it.contains(':') && !it.startsWith("[")) "[$it]" else it }
}

/** Speichert die Einstellungen lokal auf dem Handy. */
class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("pi_config", Context.MODE_PRIVATE)

    fun load(): PiConfig = PiConfig(
        host = prefs.getString("host", "") ?: "",
        apiPort = prefs.getInt("apiPort", 1984),
        rtspPort = prefs.getInt("rtspPort", 8554),
        user = prefs.getString("user", "admin") ?: "admin",
        password = prefs.getString("password", "") ?: "",
    )

    fun save(config: PiConfig) = prefs.edit {
        putString("host", config.host.trim())
        putInt("apiPort", config.apiPort)
        putInt("rtspPort", config.rtspPort)
        putString("user", config.user)
        putString("password", config.password)
    }
}
