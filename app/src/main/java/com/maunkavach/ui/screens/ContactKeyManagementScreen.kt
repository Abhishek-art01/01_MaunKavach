package com.maunkavach.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.maunkavach.crypto.KeyExpiry
import com.maunkavach.crypto.RegenerateMode
import com.maunkavach.crypto.RotationCipher
import com.maunkavach.crypto.SubstitutionTable
import com.maunkavach.crypto.VaultKeyManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactKeyManagementScreen(contactId: String, onOpenQr: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val vaultKeyManager = remember { VaultKeyManager(context) }
    var bundle by remember { mutableStateOf(vaultKeyManager.getContactKey(contactId)) }
    var selectedExpiry by remember { mutableStateOf(KeyExpiry.THIRTY_DAYS) }
    var showFullKeyWarning by remember { mutableStateOf(false) }
    var showRegenerateDialog by remember { mutableStateOf(false) }
    var showManualEntry by remember { mutableStateOf(false) }
    var manualTableInput by remember { mutableStateOf("") }
    var manualRotationInput by remember { mutableStateOf("") }
    var manualError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Key — $contactId") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
            Text("Fingerprint (not the full key):", style = MaterialTheme.typography.labelMedium)
            Text(bundle?.fingerprint ?: "—", style = MaterialTheme.typography.titleMedium, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            Text("Key version: ${bundle?.keyVersion ?: "—"}", style = MaterialTheme.typography.labelSmall)

            Spacer(Modifier.height(16.dp))
            Text("Key expiry for next regeneration", style = MaterialTheme.typography.labelMedium)
            Row {
                listOf(KeyExpiry.SEVEN_DAYS, KeyExpiry.THIRTY_DAYS, KeyExpiry.NINETY_DAYS, KeyExpiry.NO_EXPIRY).forEach { expiry ->
                    FilterChip(
                        selected = selectedExpiry == expiry,
                        onClick = { selectedExpiry = expiry },
                        label = { Text(if (expiry == KeyExpiry.NO_EXPIRY) "Never" else "${expiry.days}d") },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(onClick = { showRegenerateDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Regenerate key")
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showManualEntry = !showManualEntry }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showManualEntry) "Hide manual key entry" else "Manual key entry instead")
            }

            if (showManualEntry) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Paste a 256-number comma-separated permutation of 0–255 and a 999-number " +
                        "comma-separated rotation rule (0–255, repeats allowed).",
                    style = MaterialTheme.typography.labelSmall
                )
                OutlinedTextField(
                    value = manualTableInput, onValueChange = { manualTableInput = it },
                    label = { Text("Substitution table (256 numbers)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 3
                )
                OutlinedTextField(
                    value = manualRotationInput, onValueChange = { manualRotationInput = it },
                    label = { Text("Rotation rule (999 numbers)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 3
                )
                manualError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
                Button(
                    onClick = {
                        try {
                            val table = manualTableInput.split(",").map { it.trim().toInt() }.toIntArray()
                            val rotation = manualRotationInput.split(",").map { it.trim().toInt() }.toIntArray()
                            val tableCheck = SubstitutionTable.validate(table)
                            val rotationCheck = RotationCipher.validate(rotation)
                            if (tableCheck is com.maunkavach.crypto.ValidationResult.Invalid) {
                                manualError = "Table error: ${tableCheck.reason}"
                                return@Button
                            }
                            if (rotationCheck is com.maunkavach.crypto.ValidationResult.Invalid) {
                                manualError = "Rotation error: ${rotationCheck.reason}"
                                return@Button
                            }
                            bundle = vaultKeyManager.generateContactKeyManual(contactId, table, rotation, selectedExpiry)
                            manualError = null
                            showManualEntry = false
                        } catch (e: NumberFormatException) {
                            manualError = "Could not parse numbers — check for stray characters."
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text("Validate & save manual key") }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onOpenQr, modifier = Modifier.fillMaxWidth()) {
                Text("Share via QR (offline, in-person only)")
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { vaultKeyManager.deleteKey(contactId); bundle = null },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Delete key") }

            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { showFullKeyWarning = true }) {
                Text("Why can't I see the full key?")
            }

            if (showFullKeyWarning) {
                AlertDialog(
                    onDismissRequest = { showFullKeyWarning = false },
                    confirmButton = { TextButton(onClick = { showFullKeyWarning = false }) { Text("OK") } },
                    title = { Text("Key never shown in full") },
                    text = { Text("Showing the full key risks shoulder-surfing or screenshot leaks. The fingerprint is enough to verify both sides hold the same key — compare it with your contact in person or over a trusted channel.") }
                )
            }

            if (showRegenerateDialog) {
                AlertDialog(
                    onDismissRequest = { showRegenerateDialog = false },
                    title = { Text("Regenerate key") },
                    text = {
                        Text(
                            "Delete old key: you will permanently lose the ability to read any " +
                                "message encrypted under the current key, on this device.\n\n" +
                                "Keep old key version: old messages stay readable; only new " +
                                "messages use the new key."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            bundle = vaultKeyManager.regenerateKey(contactId, RegenerateMode.DELETE_OLD, selectedExpiry)
                            showRegenerateDialog = false
                        }) { Text("Delete old") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            bundle = vaultKeyManager.regenerateKey(contactId, RegenerateMode.KEEP_OLD_VERSION, selectedExpiry)
                            showRegenerateDialog = false
                        }) { Text("Keep old version") }
                    }
                )
            }
        }
    }
}
