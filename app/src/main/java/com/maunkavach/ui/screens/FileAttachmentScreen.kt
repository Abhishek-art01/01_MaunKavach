package com.maunkavach.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.maunkavach.crypto.FileCrypto
import com.maunkavach.crypto.VaultKeyManager

@Composable
fun FileAttachmentScreen(contactId: String) {
    val context = LocalContext.current
    val vaultKeyManager = remember { VaultKeyManager(context) }
    val contact = remember(contactId) { vaultKeyManager.getContactKey(contactId) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Send a file to $contactId", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        if (contact == null) {
            Text("Create or import this contact's real Vault Key before sending encrypted files.")
        } else {
            Text(
                "File encryption is available through FileCrypto and requires a real user-selected file. " +
                    "Uploads must go through the authenticated encrypted file API.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(16.dp))
            Text("Encryption engine: ${FileCrypto::class.java.simpleName}", style = MaterialTheme.typography.labelSmall)
        }
    }
}
