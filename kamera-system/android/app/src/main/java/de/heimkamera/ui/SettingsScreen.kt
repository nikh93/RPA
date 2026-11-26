package de.heimkamera.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.heimkamera.Go2RtcClient
import de.heimkamera.Go2RtcException
import de.heimkamera.PiConfig
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    initial: PiConfig,
    onTest: suspend (PiConfig) -> List<String>,
    onSave: (PiConfig) -> Unit,
    onBack: (() -> Unit)?,
) {
    var host by remember { mutableStateOf(initial.host) }
    var user by remember { mutableStateOf(initial.user) }
    var password by remember { mutableStateOf(initial.password) }
    var apiPort by remember { mutableStateOf(initial.apiPort.toString()) }
    var rtspPort by remember { mutableStateOf(initial.rtspPort.toString()) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val apiPortNumber = apiPort.toIntOrNull()?.takeIf { it in 1..65535 }
    val rtspPortNumber = rtspPort.toIntOrNull()?.takeIf { it in 1..65535 }
    val config = if (host.isNotBlank() && apiPortNumber != null && rtspPortNumber != null) {
        PiConfig(host.trim(), apiPortNumber, rtspPortNumber, user.trim(), password)
    } else {
        null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verbindung zum Pi") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Trage die Tailscale-IP des Pi ein (Befehl auf dem Pi: tailscale ip -4, " +
                    "beginnt mit 100.). Damit funktioniert die App zuhause und unterwegs. " +
                    "Benutzer und Passwort sind die aus /etc/go2rtc/go2rtc.yaml.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = host,
                onValueChange = { host = it; testResult = null },
                label = { Text("Adresse des Pi") },
                placeholder = { Text("100.64.0.10") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = user,
                onValueChange = { user = it; testResult = null },
                label = { Text("Benutzer") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; testResult = null },
                label = { Text("Passwort") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = apiPort,
                    onValueChange = { apiPort = it; testResult = null },
                    label = { Text("API-Port") },
                    isError = apiPortNumber == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = rtspPort,
                    onValueChange = { rtspPort = it; testResult = null },
                    label = { Text("RTSP-Port") },
                    isError = rtspPortNumber == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    enabled = config != null && !testing,
                    onClick = {
                        val cfg = config ?: return@OutlinedButton
                        testing = true
                        testResult = "Teste Verbindung …"
                        scope.launch {
                            testResult = try {
                                val streams = onTest(cfg)
                                "Verbunden. Kameras: " + if (streams.isEmpty()) {
                                    "keine eingetragen"
                                } else {
                                    streams.joinToString { Go2RtcClient.displayName(it) }
                                }
                            } catch (e: Go2RtcException) {
                                "Fehler: ${e.message}"
                            }
                            testing = false
                        }
                    },
                ) { Text("Verbindung testen") }

                Button(
                    enabled = config != null,
                    onClick = { config?.let(onSave) },
                ) { Text("Speichern") }
            }

            testResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
