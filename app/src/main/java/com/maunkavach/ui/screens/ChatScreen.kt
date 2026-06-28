package com.maunkavach.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.maunkavach.crypto.*
import com.maunkavach.data.db.MaunKavachDbHelper
import com.maunkavach.data.db.MessageDao
import com.maunkavach.data.model.ChatMessage
import com.maunkavach.data.model.DeliveryStatus
import com.maunkavach.data.model.MessageDirection
import com.maunkavach.network.ApiClient
import com.maunkavach.network.AuthSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

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
    val messageDao = remember { MessageDao(MaunKavachDbHelper(context)) }
    var contactKey by remember(contactId) { mutableStateOf(vaultKeyManager.getContactKey(contactId)) }
    var ownReceiveKey by remember(session.username) { mutableStateOf(vaultKeyManager.getContactKey(session.username)) }
    val scope = rememberCoroutineScope()

    var sendCounter by remember { mutableStateOf(0L) }
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var realtimeConnected by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf<ChatMessage>() }

    fun deliveryStatusOf(value: String, direction: MessageDirection): DeliveryStatus =
        runCatching { DeliveryStatus.valueOf(value.uppercase()) }.getOrElse {
            if (direction == MessageDirection.RECEIVED) DeliveryStatus.READ else DeliveryStatus.SENT
        }

    fun decryptRows(rows: List<com.maunkavach.network.ServerMessageRow>): List<ChatMessage> {
        val replayProtection = ReplayProtection()
        return rows.mapNotNull { row ->
            val pkg = row.toPackage()
            val direction = if (pkg.senderUuid == session.username) MessageDirection.SENT else MessageDirection.RECEIVED
            val key = if (direction == MessageDirection.RECEIVED) {
                ownReceiveKey ?: contactKey
            } else {
                contactKey
            } ?: return@mapNotNull null
            val text = when (val result = MessagePipeline.decryptMessage(pkg, key, replayProtection)) {
                is MessagePipeline.DecryptResult.Success -> result.plaintext
                MessagePipeline.DecryptResult.HmacVerificationFailed -> "[HMAC verification failed]"
                MessagePipeline.DecryptResult.ReplayRejected -> "[Replay rejected]"
                MessagePipeline.DecryptResult.DecryptionFailed -> "[Decryption failed]"
            }
            ChatMessage(
                id = pkg.messageId,
                contactId = contactId,
                direction = direction,
                plainTextCache = text,
                encryptedPayload = pkg.ciphertextBase64,
                timestamp = pkg.timestampMillis,
                deliveryStatus = deliveryStatusOf(row.deliveryStatus, direction)
            )
        }
    }

    fun loadMessages(showBusy: Boolean = false) {
        if (contactKey == null && ownReceiveKey == null) return
        if (!showBusy && syncing) return
        if (showBusy) busy = true else syncing = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    ApiClient.fetchMessages(session.token, contactId)
                }
            }.onSuccess { rows ->
                rows.forEach { row ->
                    val pkg = row.toPackage()
                    val direction = if (pkg.senderUuid == session.username) MessageDirection.SENT else MessageDirection.RECEIVED
                    messageDao.insert(pkg, contactId, direction, deliveryStatus = row.deliveryStatus)
                }
                messageDao.markThreadRead(contactId)
                val decrypted = decryptRows(rows)
                messages.clear()
                messages.addAll(decrypted)
                sendCounter = rows
                    .filter { it.senderUuid == session.username }
                    .maxOfOrNull { it.counter + 1 }
                    ?: 0L
            }.onFailure {
                status = it.message ?: "Could not load messages."
            }
            if (showBusy) busy = false else syncing = false
        }
    }

    LaunchedEffect(contactId, contactKey) {
        ownReceiveKey = vaultKeyManager.getContactKey(session.username)
        loadMessages(showBusy = true)
    }

    LaunchedEffect(contactId, contactKey, ownReceiveKey) {
        while (isActive) {
            delay(if (realtimeConnected) 15_000 else 3_000)
            loadMessages(showBusy = false)
        }
    }

    DisposableEffect(session.token, contactId) {
        val client = ApiClient.realtimeClient(session.token)
        val thread = Thread {
            runCatching {
                client.connect { frame ->
                    val json = runCatching { JSONObject(frame) }.getOrNull()
                    val type = json?.optString("type")
                    if (type == "connected") {
                        scope.launch { realtimeConnected = true }
                    }
                    if (type == "new_message") {
                        val sender = json.optString("sender_uuid")
                        if (sender == contactId) {
                            scope.launch { loadMessages(showBusy = false) }
                        }
                    }
                    if (type == "read_receipt") {
                        val reader = json.optString("reader_uuid")
                        if (reader == contactId) {
                            scope.launch { loadMessages(showBusy = false) }
                        }
                    }
                }
            }.onFailure {
                scope.launch { realtimeConnected = false }
            }
        }
        thread.isDaemon = true
        thread.start()
        onDispose {
            realtimeConnected = false
            client.close()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(contactId) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { showClearDialog = true }, enabled = messages.isNotEmpty()) {
                        Icon(Icons.Filled.Delete, contentDescription = "Clear chat")
                    }
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
                if (!realtimeConnected) {
                    Surface(tonalElevation = 1.dp) {
                        Text(
                            "Realtime reconnecting...",
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input, onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(if (contactKey == null) "Scan this contact's QR before sending" else "Message") },
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
                                    messageDao.insert(pkg, contactId, MessageDirection.SENT)
                                    messages.add(
                                        ChatMessage(
                                            id = pkg.messageId,
                                            contactId = contactId,
                                            direction = MessageDirection.SENT,
                                            plainTextCache = plain,
                                            encryptedPayload = pkg.ciphertextBase64,
                                            timestamp = pkg.timestampMillis,
                                            deliveryStatus = DeliveryStatus.SENT
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
        if (contactKey == null && ownReceiveKey == null) {
            Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
                Text("No chat key for $contactId", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Scan or import this contact's QR before sending messages. Incoming messages can be read after your own QR key exists on this device.")
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    contactKey = vaultKeyManager.getContactKey(contactId)
                    ownReceiveKey = vaultKeyManager.getContactKey(session.username)
                    if (contactKey == null) onOpenContactKeyMgmt()
                }) {
                    Text("Manage contact key")
                }
            }
        } else {
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                if (contactKey == null) {
                    Surface(tonalElevation = 1.dp) {
                        Text(
                            "Scan $contactId's QR to reply.",
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                    items(messages) { msg ->
                        com.maunkavach.ui.components.MessageBubble(
                            text = msg.plainTextCache ?: "",
                            direction = msg.direction,
                            deliveryStatus = msg.deliveryStatus
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear chat history?") },
            text = { Text("This clears encrypted message history from this device only. It does not delete messages from the server or the other phone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ids = messages.map { it.id }
                        busy = true
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    ids.forEach { ApiClient.deleteMessage(session.token, it) }
                                }
                            }.onFailure {
                                status = it.message ?: "Could not clear chat from server."
                            }
                            messageDao.deleteThread(contactId)
                            messages.clear()
                            busy = false
                        }
                        showClearDialog = false
                    }
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }
}
