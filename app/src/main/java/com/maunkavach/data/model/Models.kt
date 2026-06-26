package com.maunkavach.data.model

data class Contact(
    val id: String,
    val displayName: String,
    val keyFingerprint: String,
    val keyExpiresAtMillis: Long?
)

enum class MessageDirection { SENT, RECEIVED }

data class ChatMessage(
    val id: String,
    val contactId: String,
    val direction: MessageDirection,
    val plainTextCache: String?, // populated only after local decrypt; never persisted in DB
    val encryptedPayload: String, // what actually gets stored + sent
    val timestamp: Long,
    val isFile: Boolean = false,
    val encryptedFileBlobUrl: String? = null,
    val encryptedFileName: String? = null,
    val selfDestructAtMillis: Long? = null,
    val deliveryStatus: DeliveryStatus = DeliveryStatus.SENT
)

enum class DeliveryStatus { PENDING, SENT, DELIVERED, READ, FAILED }
