package com.maunkavach.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.maunkavach.crypto.VaultKeyManager
import com.maunkavach.security.DeviceIntegrity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(onBack: () -> Unit, onOpenDashboard: () -> Unit = {}) {
    val context = LocalContext.current
    val vaultKeyManager = remember { VaultKeyManager(context) }

    var appCamouflage by remember { mutableStateOf(false) }
    var panicPin by remember { mutableStateOf("") }
    var panicPinSaved by remember { mutableStateOf(false) }

    val rooted = remember { DeviceIntegrity.isLikelyRooted() }
    val debugger = remember { DeviceIntegrity.isDebuggerAttached() }
    val emulator = remember { DeviceIntegrity.isLikelyEmulator() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {

            OutlinedButton(onClick = onOpenDashboard, modifier = Modifier.fillMaxWidth()) {
                Text("Open Security Dashboard")
            }
            Spacer(Modifier.height(16.dp))

            Text("Device integrity", style = MaterialTheme.typography.titleSmall)
            Text("Rooted: ${if (rooted) "⚠ Yes" else "No"}")
            Text("Debugger attached: ${if (debugger) "⚠ Yes" else "No"}")
            Text("Emulator: ${if (emulator) "Yes (expected in demo)" else "No"}")
            Text(
                "These are advisory signals shown to the user, not a hard security boundary.",
                style = MaterialTheme.typography.labelSmall
            )

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("App camouflage")
                    Text("Disguise app icon/name on the home screen", style = MaterialTheme.typography.labelSmall)
                }
                Switch(checked = appCamouflage, onCheckedChange = { appCamouflage = it })
            }
            if (appCamouflage) {
                Text(
                    "TODO (real impl): swap <activity-alias> icon/label via PackageManager.setComponentEnabledSetting; " +
                        "demo only toggles this in-memory flag.",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(Modifier.height(24.dp))
            Text("Panic PIN / decoy vault", style = MaterialTheme.typography.titleSmall)
            Text(
                "Entering this PIN instead of your real PIN opens a harmless decoy Vault Key " +
                    "section with fake contacts, instead of your real keys.",
                style = MaterialTheme.typography.labelSmall
            )
            OutlinedTextField(
                value = panicPin, onValueChange = { panicPin = it; panicPinSaved = false },
                label = { Text("Set panic PIN") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Button(onClick = {
                if (panicPin.isNotBlank()) {
                    vaultKeyManager.setPanicPin(panicPin)
                    panicPinSaved = true
                }
            }, modifier = Modifier.padding(top = 8.dp)) {
                Text(if (panicPinSaved) "Saved ✓" else "Save panic PIN")
            }
        }
    }
}
