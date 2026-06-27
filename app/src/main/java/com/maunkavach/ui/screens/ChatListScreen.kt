package com.maunkavach.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.maunkavach.crypto.VaultKeyManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    username: String,
    onOpenChat: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenContactKeyMgmt: (String) -> Unit
) {
    val context = LocalContext.current
    val vaultKeyManager = remember { VaultKeyManager(context) }
    var contactId by remember { mutableStateOf("") }
    var contactKeys by remember { mutableStateOf(vaultKeyManager.allContacts()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MaunKavach") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                "Signed in as $username",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium
            )

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = contactId,
                    onValueChange = { contactId = it.trim() },
                    label = { Text("Contact username") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val id = contactId.trim()
                        if (id.isNotBlank()) onOpenContactKeyMgmt(id)
                    },
                    enabled = contactId.isNotBlank()
                ) {
                    Text("Key")
                }
            }

            Button(
                onClick = {
                    val id = contactId.trim()
                    if (id.isNotBlank()) onOpenChat(id)
                },
                enabled = contactId.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Text("Open Chat")
            }

            Divider(Modifier.padding(top = 12.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        "Contacts with local Vault Keys",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                if (contactKeys.isEmpty()) {
                    item {
                        Text(
                            "No contact keys on this device. Enter a real server username above, create or import its Vault Key, then open chat.",
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                items(contactKeys) { bundle ->
                    ListItem(
                        headlineContent = { Text(bundle.contactId) },
                        supportingContent = { Text("Fingerprint: ${bundle.fingerprint}") },
                        modifier = Modifier.clickable { onOpenChat(bundle.contactId) }
                    )
                    Divider()
                }
                item {
                    LaunchedEffect(Unit) {
                        contactKeys = vaultKeyManager.allContacts()
                    }
                }
            }
        }
    }
}
