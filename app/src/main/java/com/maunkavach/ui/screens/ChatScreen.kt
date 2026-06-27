package com.maunkavach.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.maunkavach.crypto.*
import com.maunkavach.data.model.ChatMessage
import com.maunkavach.data.model.MessageDirection
import com.maunkavach.network.ApiClient
import com.maunkavach.network.AuthSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    contactId: String,
    session: AuthSession,
    onOpenContactKeyMgmt: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val vaultKeyManager = remember { VaultKeyManager(context) }
    var contactKey by remember(contactId) { mutableStateOf(vaultKeyManager.getContactKey(contactId)) }
    val scope = rememberCoroutineScope()

    var sendCounter by remember { mutableStateOf(0L) }
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf<ChatMessage>() }

    fun loadMessages() {
        val key = contactKey ?: return
        busy = true
        status = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    ApiClient.fetchMessages(session.token, contactId)
                }
            }.onSuccess { rows ->
                val replayProtection = ReplayProtection()
                val decrypted = rows.mapNotNull { row ->
                    val pkg = row.toPackage()
                    val text = when (val result = MessagePipeline.decryptMessage(pkg, key, replayProtection)) {
                        is MessagePipeline.DecryptResult.Success -> result.plaintext
                        MessagePipeline.DecryptResult.HmacVerificationFailed -> "[HMAC verification failed]"
                        MessagePipeline.DecryptResult.ReplayRejected -> "[Replay rejected]"
                        MessagePipeline.DecryptResult.DecryptionFailed -> "[Decryption failed]"
                    }
                    ChatMessage(
                        id = pkg.messageId,
                        contactId = contactId,
                        direction = if (pkg.senderUuid == session.username) MessageDirection.SENT else MessageDirection.RECEIVED,
                        plainTextCache = text,
                        encryptedPayload = pkg.ciphertextBase64,
                        timestamp = pkg.timestampMillis
                    )
                }
                messages.clear()
                messages.addAll(decrypted)
                sendCounter = rows
                    .filter { it.senderUuid == session.username }
                    .maxOfOrNull { it.counter + 1 }
                    ?: 0L
            }.onFailure {
                status = it.message ?: "Could not load messages."
            }
            busy = false
        }
    }

    LaunchedEffect(contactId, contactKey) {
        loadMessages()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(contactId) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        },
        bottomBar = {
            Column {
                status?.let {
                    Surface(tonalElevation = 2.dp) {
                        Text(
                            it,
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input, onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(if (contactKey == null) "Create or import contact key first" else "Message") },
                        enabled = contactKey != null && !busy
                    )
                    IconButton(onClick = {
                        val key = contactKey
                        if (input.isNotBlank() && key != null) {
                            val plain = input
                            val pkg = MessagePipeline.encryptMessage(
                                plainText = plain,
                                contact = key,
                                senderUuid = session.username,
                                receiverUuid = contactId,
                                sendCounter = sendCounter
                            )
                            busy = true
                            status = null
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        ApiClient.sendMessage(session.token, pkg)
                                    }
                                }.onSuccess {
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
                                }.onFailure {
                                    status = it.message ?: "Message send failed."
                                }
                                busy = false
                            }
                        }
                    }, enabled = input.isNotBlank() && contactKey != null && !busy) {
                        Icon(Icons.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    ) { padding ->
        if (contactKey == null) {
            Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
                Text("No Vault Key for $contactId", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Create or import this contact's real Vault Key before sending or reading messages.")
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    contactKey = vaultKeyManager.getContactKey(contactId)
                    if (contactKey == null) onOpenContactKeyMgmt()
                }) {
                    Text("Manage contact key")
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(top = 8.dp)) {
                items(messages) { msg ->
                    com.maunkavach.ui.components.MessageBubble(text = msg.plainTextCache ?: "", direction = msg.direction)
                }
            }
        }
    }
}
