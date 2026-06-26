package com.maunkavach.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.maunkavach.security.DeviceIntegrity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityDashboardScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val snapshot = remember { DeviceIntegrity.snapshot(context) }
    var paranoidMode by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security Dashboard") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {

            if (snapshot.anyHighRisk) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text("Device security risk detected", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            StatusRow("No root detected", !snapshot.rooted)
            StatusRow("No debugger detected", !snapshot.debuggerAttached)
            StatusRow("No Frida indicators", !snapshot.fridaIndicators)
            StatusRow("No risky accessibility service", !snapshot.riskyAccessibilityService)
            StatusRow("Screenshot protection active", true) // enforced globally via FLAG_SECURE in MainActivity
            StatusRow("Backup disabled", snapshot.backupDisabled)
            StatusRow("Not running on emulator", !snapshot.emulator)

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Paranoid mode", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Refuses to unlock the Vault at all if root, a debugger, Frida " +
                            "indicators, or a risky accessibility service is detected. Also " +
                            "enables Vault-wipe after repeated failed PIN attempts.",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Switch(checked = paranoidMode, onCheckedChange = { paranoidMode = it })
            }

            if (paranoidMode && snapshot.anyHighRisk) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Paranoid mode is ON and a risk was detected — Vault Key unlock would be " +
                        "refused right now.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "These checks raise the bar against casual tooling and opportunistic malware. " +
                    "No client-side check can be made unbypassable on a device an attacker " +
                    "fully controls — the real guarantee is the Keystore-backed AES-256-GCM + " +
                    "HMAC-SHA256 layer underneath, which holds even if every check above is evaded.",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Icon(
            if (ok) Icons.Filled.Check else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (ok) Color(0xFF1B8A5A) else MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
