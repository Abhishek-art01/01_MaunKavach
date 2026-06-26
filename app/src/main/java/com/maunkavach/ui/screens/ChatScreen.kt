package com.maunkavach.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.maunkavach.crypto.*
import com.maunkavach.data.model.ChatMessage
import com.maunkavach.data.model.MessageDirection

/**
 * Demo-scope: generates a throwaway in-memory ContactKeyBundle instead of pulling the real
 * one from VaultKeyManager, so this screen is usable without forcing a biometric prompt on
 * every preview build. Wire `VaultKeyManager(context).getContactKey(contactId)` in for the
 * real flow — MessagePipeline calls below are identical either way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(contactId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val demoVault = remember { VaultKeyManager(context) }
    val demoContact = remember {
        demoVault.getContactKey(contactId) ?: demoVault.generateContactKeyAuto(contactId, KeyExpiry.THIRTY_DAYS)
    }
    val replayProtection = remember { ReplayProtection() }

    var sendCounter by remember { mutableStateOf(0L) }
    var input by remember { mutableStateOf("") }
    var pipelineLog by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatMessage>() }

    val myUuid = "me"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(contactId.replaceFirstChar { it.uppercase() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        },
        bottomBar = {
            Column {
                if (pipelineLog.isNotEmpty()) {
                    Surface(tonalElevation = 2.dp) {
                        Text(
                            pipelineLog,
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    IconButton(onClick = { /* TODO: open FileAttachmentScreen */ }) {
                        Icon(Icons.Filled.AttachFile, contentDescription = "Attach file")
                    }
                    OutlinedTextField(
                        value = input, onValueChange = { input = it },
                        modifier = Modifier.weight(1f), placeholder = { Text("Message") }
                    )
                    IconButton(onClick = {
                        if (input.isNotBlank()) {
                            val plain = input

                            // --- Full spec pipeline: substitution -> rotation -> ephemeral key -> AES-GCM -> HMAC ---
                            val pkg = MessagePipeline.encryptMessage(
                                plainText = plain,
                                contact = demoContact,
                                senderUuid = myUuid,
                                receiverUuid = contactId,
                                sendCounter = sendCounter
                            )

                            pipelineLog = buildString {
                                appendLine("plaintext      : \"$plain\"")
                                appendLine("→ substitution + 999-step rotation applied")
                                appendLine("→ ephemeral key derived from counter=$sendCounter + nonce")
                                appendLine("→ AES-256-GCM ciphertext (${pkg.ciphertextBase64.length} b64 chars)")
                                appendLine("→ HMAC-SHA256 tag: ${pkg.hmacBase64.take(24)}…")
                                append("server sees only: message_id, nonce, ciphertext, hmac — no plaintext")
                            }

                            messages.add(
                                ChatMessage(
                                    id = pkg.messageId,
                                    contactId = contactId,
                                    direction = MessageDirection.SENT,
                                    plainTextCache = plain,
                                    encryptedPayload = pkg.ciphertextBase64,
                                    timestamp = pkg.timestampMillis
                                )
                            )
                            sendCounter++
                            input = ""

                            // Simulate "server round trip then receiver decrypts" — proves HMAC verify +
                            // replay-check + decrypt all succeed for a legitimate, unmodified package.
                            val result = MessagePipeline.decryptMessage(pkg, demoContact, replayProtection)
                            check(result is MessagePipeline.DecryptResult.Success && result.plaintext == plain) {
                                "round-trip sanity check failed: $result"
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(top = 8.dp)) {
            items(messages) { msg ->
                com.maunkavach.ui.components.MessageBubble(text = msg.plainTextCache ?: "", direction = msg.direction)
            }
        }
    }
}
