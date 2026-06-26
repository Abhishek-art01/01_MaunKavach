package com.maunkavach.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.maunkavach.crypto.VaultKeyManager

/**
 * Per the spec: "If QR library is not allowed, use manual QR-like key transfer for demo
 * first." This renders a deterministic block matrix derived from a SHA-256 hash of the
 * transfer payload — visually QR-like and good enough to demo "scan this in person," but it
 * is NOT a real ISO/IEC 18004 QR code (no Reed-Solomon error correction, no real QR decoder
 * will read it). Swap in a from-scratch QR encoder, or get explicit sign-off to add a QR
 * library, before shipping a real version.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrKeySharingScreen(contactId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val vaultKeyManager = remember { VaultKeyManager(context) }
    val record = remember { vaultKeyManager.getContactKey(contactId) ?: vaultKeyManager.generateContactKeyAuto(contactId, com.maunkavach.crypto.KeyExpiry.THIRTY_DAYS) }
    val payload = remember { vaultKeyManager.exportForQr(record) }
    val matrix = remember(payload) { deriveDemoMatrix(payload, size = 21) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Share key — QR") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Text("Hold this screen up to your contact's camera — in person only.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))

            Canvas(modifier = Modifier.size(260.dp)) {
                val cell = size.minDimension / matrix.size
                matrix.forEachIndexed { row, cols ->
                    cols.forEachIndexed { col, filled ->
                        if (filled) {
                            drawRect(
                                color = Color.Black,
                                topLeft = androidx.compose.ui.geometry.Offset(col * cell, row * cell),
                                size = androidx.compose.ui.geometry.Size(cell, cell)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Fingerprint to verbally confirm: ${record.fingerprint}", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "This key is never transmitted over the network — it's only ever displayed " +
                    "for an in-person camera scan, then stored locally on the scanning device.",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/** Deterministic pseudo-QR matrix from SHA-256(payload) bits — visual demo only, not a real QR encoder. */
private fun deriveDemoMatrix(payload: String, size: Int): List<List<Boolean>> {
    val hash = java.security.MessageDigest.getInstance("SHA-256").digest(payload.toByteArray())
    val bits = hash.flatMap { byte -> (7 downTo 0).map { bit -> ((byte.toInt() shr bit) and 1) == 1 } }
    return (0 until size).map { row ->
        (0 until size).map { col ->
            val idx = (row * size + col) % bits.size
            // Force the three corner "finder pattern" blocks solid, like real QR codes, for visual realism.
            val isCorner = (row < 3 && col < 3) || (row < 3 && col > size - 4) || (row > size - 4 && col < 3)
            isCorner || bits[idx]
        }
    }
}
