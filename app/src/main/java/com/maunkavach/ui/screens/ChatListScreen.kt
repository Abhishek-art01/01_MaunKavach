package com.maunkavach.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class ChatPreview(val contactId: String, val name: String, val lastMessagePreview: String, val time: String)

private val demoChats = listOf(
    ChatPreview("alice", "Alice", "🔒 Encrypted message", "09:14"),
    ChatPreview("bob", "Bob", "🔒 Encrypted file", "Yesterday"),
    ChatPreview("carol", "Carol", "🔒 Encrypted message", "Mon")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(onOpenChat: (String) -> Unit, onOpenSettings: () -> Unit) {
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
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(demoChats) { chat ->
                ListItem(
                    headlineContent = { Text(chat.name) },
                    supportingContent = { Text(chat.lastMessagePreview) },
                    trailingContent = { Text(chat.time, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.clickable { onOpenChat(chat.contactId) }
                )
                Divider()
            }
        }
    }
}
