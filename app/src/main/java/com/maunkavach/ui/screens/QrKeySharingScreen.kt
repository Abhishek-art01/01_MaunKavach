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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrKeySharingScreen(contactId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val vaultKeyManager = remember { VaultKeyManager(context) }
    val record = remember(contactId) { vaultKeyManager.getContactKey(contactId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Share key — QR") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            if (record == null) {
                Text("No Vault Key exists for $contactId.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text("Create or import a real key before sharing.", style = MaterialTheme.typography.labelSmall)
            } else {
                Text("Offline key sharing is disabled in this release build.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Text("Fingerprint to verify: ${record.fingerprint}", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Use manual key entry until a real QR encoder and scanner are configured.",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
