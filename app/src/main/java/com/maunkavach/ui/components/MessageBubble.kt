package com.maunkavach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maunkavach.data.model.MessageDirection

@Composable
fun MessageBubble(text: String, direction: MessageDirection, isFile: Boolean = false, decrypted: Boolean = true) {
    val isSent = direction == MessageDirection.SENT
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = if (isSent) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                if (isFile && !decrypted) {
                    Text("🔒 Encrypted file — tap to decrypt", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(text)
                }
            }
        }
    }
}
