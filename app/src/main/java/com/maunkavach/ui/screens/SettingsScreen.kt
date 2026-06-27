package com.maunkavach.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenVaultKey: () -> Unit,
    onOpenSecuritySettings: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column2(padding) {
            ListItem(
                headlineContent = { Text("Vault Key") },
                supportingContent = { Text("Manage encryption keys — protected by biometric/PIN") },
                leadingContent = { Icon(Icons.Filled.Lock, contentDescription = null) },
                modifier = Modifier.clickable { onOpenVaultKey() }
            )
            HorizontalDividerCompat()
            ListItem(
                headlineContent = { Text("Security") },
                supportingContent = { Text("App lock, screenshot protection, root detection") },
                leadingContent = { Icon(Icons.Filled.Security, contentDescription = null) },
                modifier = Modifier.clickable { onOpenSecuritySettings() }
            )
            HorizontalDividerCompat()
            ListItem(
                headlineContent = { Text("Sign out") },
                supportingContent = { Text("Clear the saved login on this device") },
                leadingContent = { Icon(Icons.Filled.ExitToApp, contentDescription = null) },
                modifier = Modifier.clickable { onSignOut() }
            )
        }
    }
}

// Tiny local helpers kept inline to avoid pulling in extra files for one-off layout tweaks.
@Composable
private fun Column2(padding: androidx.compose.foundation.layout.PaddingValues, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize().padding(padding), content = content)
}

@Composable
private fun HorizontalDividerCompat() {
    Divider()
}
