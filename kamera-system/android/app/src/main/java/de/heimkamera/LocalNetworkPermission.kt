package de.heimkamera

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Ab Android 17 (API 37) blockiert das System für Apps mit targetSdk >= 37 jeden
 * Verkehr ins lokale Netz (WLAN, Hotspot), solange die Berechtigung
 * ACCESS_LOCAL_NETWORK nicht erteilt ist. Ohne sie kann der Stream-Server nur
 * über localhost erreicht werden und der Empfänger kein anderes Gerät erreichen.
 * Auf älteren Android-Versionen ist keine Abfrage nötig.
 */
private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

class LocalNetworkAccess internal constructor(
    private val context: Context,
    private val launch: () -> Unit,
    private val setPending: (() -> Unit) -> Unit,
    val denied: Boolean,
) {
    val granted: Boolean get() = isGranted(context)

    /** Führt [action] aus, sobald die Berechtigung erteilt ist (fragt bei Bedarf nach). */
    fun runWithPermission(action: () -> Unit) {
        if (granted) {
            action()
        } else {
            setPending(action)
            launch()
        }
    }

    /** Öffnet die App-Einstellungen, falls die Berechtigung dauerhaft abgelehnt wurde. */
    fun openAppSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    companion object {
        fun isGranted(context: Context): Boolean =
            Build.VERSION.SDK_INT < 37 ||
                ContextCompat.checkSelfPermission(context, ACCESS_LOCAL_NETWORK) ==
                PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun rememberLocalNetworkAccess(): LocalNetworkAccess {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        denied = !ok
        if (ok) pending?.invoke()
        pending = null
    }
    return LocalNetworkAccess(
        context = context,
        launch = { launcher.launch(ACCESS_LOCAL_NETWORK) },
        setPending = { pending = it },
        denied = denied,
    )
}
