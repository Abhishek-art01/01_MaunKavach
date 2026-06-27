package com.maunkavach.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maunkavach.crypto.VaultKeyManager
import com.maunkavach.data.db.ChatPreviewRow
import com.maunkavach.data.db.ContactDao
import com.maunkavach.data.db.MaunKavachDbHelper
import com.maunkavach.data.db.MessageDao
import com.maunkavach.data.model.Contact
import com.maunkavach.data.model.MessageDirection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ChatTab(val label: String) { ALL("All"), UNREAD("Unread"), ARCHIVED("Archived") }

private data class ChatHomeRow(
    val contact: Contact,
    val preview: ChatPreviewRow?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    username: String,
    onOpenChat: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenContactKeyMgmt: (String) -> Unit,
    onScanQr: () -> Unit,
    onGenerateQr: () -> Unit
) {
    val context = LocalContext.current
    val vaultKeyManager = remember { VaultKeyManager(context) }
    val contactDao = remember { ContactDao(MaunKavachDbHelper(context)) }
    val messageDao = remember { MessageDao(MaunKavachDbHelper(context)) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddMenu by remember { mutableStateOf(false) }
    var rows by remember { mutableStateOf<List<ChatHomeRow>>(emptyList()) }

    fun loadRows() {
        vaultKeyManager.allContacts().forEach { bundle ->
            contactDao.upsert(
                Contact(
                    id = bundle.contactId,
                    displayName = bundle.contactId,
                    keyFingerprint = bundle.fingerprint,
                    keyExpiresAtMillis = bundle.expiresAtMillis
                )
            )
        }
        val previews = messageDao.latestRowsByContact()
        rows = contactDao.all()
            .map { ChatHomeRow(contact = it, preview = previews[it.id]) }
            .sortedWith(compareByDescending<ChatHomeRow> { it.preview?.timestampMillis ?: 0L }.thenBy { it.contact.displayName.lowercase() })
    }

    LaunchedEffect(Unit) {
        loadRows()
    }

    Scaffold(
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { showAddMenu = true },
                    containerColor = Color(0xFF7C3AED),
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add contact")
                }
                DropdownMenu(
                    expanded = showAddMenu,
                    onDismissRequest = { showAddMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Scan QR") },
                        leadingIcon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null) },
                        onClick = {
                            showAddMenu = false
                            onScanQr()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Generate QR") },
                        leadingIcon = { Icon(Icons.Filled.QrCode, contentDescription = null) },
                        onClick = {
                            showAddMenu = false
                            onGenerateQr()
                        }
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Chats") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { },
                    icon = { Icon(Icons.Filled.Archive, contentDescription = null) },
                    label = { Text("Archive") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onOpenSettings,
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Settings") }
                )
            }
        },
        containerColor = Color(0xFFF6F1FF)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Chats", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Signed in as $username", style = MaterialTheme.typography.labelMedium, color = Color(0xFF6B5B80))
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color(0xFF4C1D95))
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = Color.White,
                tonalElevation = 2.dp
            ) {
                Column {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFF6D28D9)
                    ) {
                        ChatTab.entries.forEachIndexed { index, tab ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(tab.label, fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }

                    val visibleRows = when (ChatTab.entries[selectedTab]) {
                        ChatTab.ALL -> rows
                        ChatTab.UNREAD -> rows.filter { (it.preview?.unreadCount ?: 0) > 0 }
                        ChatTab.ARCHIVED -> emptyList()
                    }

                    if (visibleRows.isEmpty()) {
                        EmptyChatState(selectedTab = ChatTab.entries[selectedTab])
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(visibleRows, key = { it.contact.id }) { row ->
                                ChatCard(
                                    row = row,
                                    onOpenChat = { onOpenChat(row.contact.id) },
                                    onOpenKey = { onOpenContactKeyMgmt(row.contact.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChatState(selectedTab: ChatTab) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.ChatBubble, contentDescription = null, tint = Color(0xFF7C3AED))
        Spacer(Modifier.height(10.dp))
        Text(
            when (selectedTab) {
                ChatTab.ALL -> "No contacts yet"
                ChatTab.UNREAD -> "No unread chats"
                ChatTab.ARCHIVED -> "No archived chats"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Use the plus button to scan or generate a contact QR.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF6B5B80)
        )
    }
}

@Composable
private fun ChatCard(row: ChatHomeRow, onOpenChat: () -> Unit, onOpenKey: () -> Unit) {
    val preview = row.preview
    val unread = preview?.unreadCount ?: 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (unread > 0) Color(0xFFF1E8FF) else Color.White)
            .border(1.dp, Color(0xFFE8DAFF), RoundedCornerShape(22.dp))
            .clickable(onClick = onOpenChat)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(name = row.contact.displayName, online = row.contact.keyFingerprint != "pending-key-exchange")
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.contact.displayName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    preview?.timestampMillis?.let { chatTime(it) } ?: "New",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF7E6D96)
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (preview?.direction == MessageDirection.SENT) {
                    Icon(
                        Icons.Filled.DoneAll,
                        contentDescription = "Sent",
                        tint = if (preview.deliveryStatus == "READ") Color(0xFF7C3AED) else Color(0xFF8E83A3),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    when {
                        preview == null -> "Contact added. Create/import Vault Key before messaging."
                        preview.isFile -> "Encrypted file"
                        else -> "Encrypted message"
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B5B80),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (unread > 0) {
                    UnreadBadge(unread)
                } else if (preview == null) {
                    Text(
                        "Key",
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onOpenKey)
                            .background(Color(0xFFEDE4FF))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        color = Color(0xFF5B21B6),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun Avatar(name: String, online: Boolean) {
    Box(modifier = Modifier.size(54.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color(0xFFDDD6FE)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name.take(1).uppercase(Locale.getDefault()),
                color = Color(0xFF4C1D95),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(14.dp)
                .clip(CircleShape)
                .background(if (online) Color(0xFF22C55E) else Color(0xFFA1A1AA))
                .border(2.dp, Color.White, CircleShape)
        )
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color(0xFF7C3AED))
            .size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (count > 9) "9+" else count.toString(),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun chatTime(timestampMillis: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestampMillis))
