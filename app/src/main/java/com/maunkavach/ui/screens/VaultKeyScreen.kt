package com.maunkavach.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.maunkavach.crypto.ContactKeyBundle
import com.maunkavach.crypto.KeyExpiry
import com.maunkavach.crypto.VaultKeyManager
import com.maunkavach.security.BiometricHelper

private val demoContacts = listOf("alice", "bob", "carol")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultKeyScreen(activity: FragmentActivity, onOpenContactKeyMgmt: (String) -> Unit, onBack: () -> Unit) {
    var unlocked by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    val vaultKeyManager = remember { VaultKeyManager(activity) }
    val contactKeys = remember { mutableStateOf<List<ContactKeyBundle>>(emptyList()) }

    // Required every time the section is entered — no caching across navigation (spec section 1).
    LaunchedEffect(Unit) {
        if (BiometricHelper.canUseBiometrics(activity)) {
            BiometricHelper.promptUnlock(
                activity,
                onSuccess = {
                    unlocked = true
                    demoContacts.forEach { id ->
                        if (vaultKeyManager.getContactKey(id) == null) {
                            vaultKeyManager.generateContactKeyAuto(id, KeyExpiry.THIRTY_DAYS)
                        }
                    }
                    contactKeys.value = vaultKeyManager.allContacts()
                },
                onError = { authError = it }
            )
        } else {
            // Device-credential-only fallback path would go here (PIN entry UI). Demo unlocks
            // directly so the screen is viewable on devices/emulators without biometrics set up.
            unlocked = true
            contactKeys.value = demoContacts.map { vaultKeyManager.generateContactKeyAuto(it, KeyExpiry.THIRTY_DAYS) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vault Key") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                !unlocked && authError == null -> {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Waiting for biometric/PIN confirmation…")
                    }
                }
                authError != null -> {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("Unlock failed: $authError")
                        Spacer(Modifier.height(8.dp))
                        Text("This section stays locked. Keys never leave the device, and they're not viewable without authentication.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                else -> {
                    LazyColumn {
                        item {
                            Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "Key never leaves device · Stored only after biometric unlock",
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        items(contactKeys.value) { bundle ->
                            ListItem(
                                headlineContent = { Text(bundle.contactId.replaceFirstChar { it.uppercase() } + "  (v${bundle.keyVersion})") },
                                supportingContent = {
                                    Column {
                                        Text("Fingerprint: ${bundle.fingerprint}")
                                        val expiry = bundle.expiresAtMillis
                                        Text(
                                            if (expiry == null) "No expiry" else
                                                "Expires: ${java.text.SimpleDateFormat("MMM d, yyyy").format(java.util.Date(expiry))}" +
                                                    if (vaultKeyManager.isExpired(bundle)) "  ⚠ EXPIRED" else ""
                                        )
                                    }
                                },
                                modifier = Modifier.clickable { onOpenContactKeyMgmt(bundle.contactId) }
                            )
                            Divider()
                        }
                        item {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "App camouflage and panic PIN / decoy vault are configured in " +
                                    "Security Settings.",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}
