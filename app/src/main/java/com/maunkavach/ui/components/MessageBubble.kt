package com.maunkavach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maunkavach.data.model.DeliveryStatus
import com.maunkavach.data.model.MessageDirection

@Composable
fun MessageBubble(
    text: String,
    direction: MessageDirection,
    isFile: Boolean = false,
    decrypted: Boolean = true,
    deliveryStatus: DeliveryStatus = DeliveryStatus.SENT
) {
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
                    Text("Encrypted file - tap to decrypt", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(text)
                }
                if (isSent) {
                    Row(
                        modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DeliveryCheck(deliveryStatus)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeliveryCheck(status: DeliveryStatus) {
    val tint = when (status) {
        DeliveryStatus.READ -> Color(0xFF34B7F1)
        DeliveryStatus.FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
    }
    val icon = when (status) {
        DeliveryStatus.PENDING,
        DeliveryStatus.SENT,
        DeliveryStatus.FAILED -> Icons.Filled.Done
        DeliveryStatus.DELIVERED,
        DeliveryStatus.READ -> Icons.Filled.DoneAll
    }
    Icon(
        imageVector = icon,
        contentDescription = status.name.lowercase(),
        tint = tint,
        modifier = Modifier.size(16.dp)
    )
}
