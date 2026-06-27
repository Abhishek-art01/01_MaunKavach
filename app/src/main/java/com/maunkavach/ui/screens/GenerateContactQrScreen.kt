package com.maunkavach.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.maunkavach.crypto.KeyExpiry
import com.maunkavach.crypto.VaultKeyManager
import com.maunkavach.data.ContactExchange
import com.maunkavach.security.BiometricHelper
import com.maunkavach.ui.util.QrCodeEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateContactQrScreen(activity: FragmentActivity, username: String, onBack: () -> Unit) {
    val vaultKeyManager = remember { VaultKeyManager(activity) }
    var payload by remember(username) { mutableStateOf<String?>(null) }
    var authError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(username) {
        if (!BiometricHelper.canUseBiometrics(activity)) {
            authError = "Device PIN, fingerprint, or face unlock is required before showing the full chat QR."
            return@LaunchedEffect
        }
        BiometricHelper.promptUnlock(
            activity = activity,
            title = "Generate Chat QR",
            subtitle = "Authenticate before exporting chat keys",
            onSuccess = {
                val bundle = vaultKeyManager.getContactKey(username)
                    ?: vaultKeyManager.generateContactKeyAuto(username, KeyExpiry.NO_EXPIRY)
                payload = ContactExchange.createFull(username = username, bundle = bundle)
            },
            onError = { authError = it }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Generate QR") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } }
            )
        },
        containerColor = Color(0xFFF6F1FF)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(shape = RoundedCornerShape(28.dp), color = Color.White, tonalElevation = 2.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val qrPayload = payload
                    if (qrPayload == null) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFF7C3AED))
                        Spacer(Modifier.height(16.dp))
                        Text("Authentication required", style = MaterialTheme.typography.titleLarge)
                        authError?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        return@Column
                    }
                    ContactQrPreview(payload = qrPayload)
                    Spacer(Modifier.height(20.dp))
                    Text(username, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "This QR contains username, chat key, substitution table, and rotation rule. It never includes password, session token, or account secret.",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6B5B80)
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = qrPayload,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Contact exchange data") },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        minLines = 4
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactQrPreview(payload: String) {
    val modules = remember(payload) { QrCodeEncoder.encode(payload) }
    Canvas(
        modifier = Modifier
            .size(240.dp)
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        val cells = modules.size
        val cell = size.minDimension / cells
        for (y in 0 until cells) {
            for (x in 0 until cells) {
                if (modules[y][x]) {
                    drawRect(
                        color = Color(0xFF2E1065),
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell, cell)
                    )
                }
            }
        }
    }
}
