package com.maunkavach.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.maunkavach.crypto.FileCrypto
import com.maunkavach.crypto.KeyExpiry
import com.maunkavach.crypto.VaultKeyManager
import java.io.File

/**
 * Screen 4 from the spec — attach & send a file. Demo flow: write a small sample file to the
 * app's cache dir, run it through FileCrypto.encryptFile (substitution + rotation + AES-256-GCM
 * + HMAC + encrypted metadata), then show the resulting opaque ciphertext blob + wrapped key,
 * proving the server-bound payload contains no original bytes, filename, or mimetype in the clear.
 */
@Composable
fun FileAttachmentScreen(contactId: String) {
    val context = LocalContext.current
    val vaultKeyManager = remember { VaultKeyManager(context) }
    val demoContact = remember {
        vaultKeyManager.getContactKey(contactId) ?: vaultKeyManager.generateContactKeyAuto(contactId, KeyExpiry.THIRTY_DAYS)
    }
    var resultSummary by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Send a file to $contactId", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))

        Button(onClick = {
            val sample = File(context.cacheDir, "demo_invoice.pdf").apply {
                writeText("This is a sample file's plaintext content — never sent as-is.")
            }
            val outputDir = File(context.cacheDir, "encrypted_outbox")
            val pkg = FileCrypto.encryptFile(
                sourceFile = sample,
                originalName = "demo_invoice.pdf",
                mimeType = "application/pdf",
                contact = demoContact,
                senderUuid = "me",
                receiverUuid = contactId,
                outputDir = outputDir
            )

            // Sanity round-trip, proving HMAC verify + decrypt + metadata recovery all work.
            val blobBytes = File(pkg.encryptedBlobLocalPath).readBytes()
            val decrypted = FileCrypto.decryptFile(pkg, blobBytes, demoContact)
            val roundTripOk = decrypted is FileCrypto.DecryptResult.Success &&
                decrypted.originalName == "demo_invoice.pdf"

            resultSummary = buildString {
                appendLine("Original file bytes: substituted + rotated + AES-256-GCM encrypted, never uploaded as-is.")
                appendLine("Opaque blob on disk: ${File(pkg.encryptedBlobLocalPath).name}")
                appendLine("Padded size bucket: ${pkg.paddedSizeBucket} bytes (true size hidden from server)")
                appendLine("HMAC tag: ${pkg.hmacBase64.take(24)}…")
                appendLine()
                appendLine("Encrypted metadata blob (server sees this, never \"demo_invoice.pdf\" or \"application/pdf\"):")
                appendLine(pkg.encryptedMetadataBase64.take(50) + "…")
                appendLine()
                append("Round-trip decrypt sanity check: ${if (roundTripOk) "PASSED ✓" else "FAILED ✗"}")
            }
        }) {
            Text("Pick & encrypt a demo file")
        }

        resultSummary?.let {
            Spacer(Modifier.height(16.dp))
            Text("Server cannot read this:", style = MaterialTheme.typography.labelMedium)
            Text(it, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
}
