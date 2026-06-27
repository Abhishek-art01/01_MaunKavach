package com.maunkavach.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.maunkavach.crypto.VaultKeyManager
import com.maunkavach.data.ContactExchange
import com.maunkavach.data.db.ContactDao
import com.maunkavach.data.db.MaunKavachDbHelper
import com.maunkavach.security.BiometricHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanContactQrScreen(activity: FragmentActivity, onBack: () -> Unit, onContactReady: (String) -> Unit) {
    val context = LocalContext.current
    val contactDao = remember { ContactDao(MaunKavachDbHelper(context)) }
    val vaultKeyManager = remember { VaultKeyManager(context) }
    var payload by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun saveValidatedContact() {
        runCatching {
            val exchange = ContactExchange.parse(payload)
            val contact = ContactExchange.toContact(exchange)
            if (contactDao.get(contact.id) == null) {
                contactDao.upsert(contact)
            }
            val keyBundle = exchange.keyBundle
            if (keyBundle != null && vaultKeyManager.getContactKey(keyBundle.contactId) == null) {
                vaultKeyManager.importFromQr(ContactExchange.fullBundleJson(keyBundle))
            }
            contact.id
        }.onSuccess { contactId ->
            error = null
            onContactReady(contactId)
        }.onFailure {
            error = it.message ?: "Could not import contact."
        }
    }

    fun importContact() {
        if (!BiometricHelper.canUseBiometrics(activity)) {
            error = "Device PIN, fingerprint, or face unlock is required before adding a QR contact."
            return
        }
        BiometricHelper.promptUnlock(
            activity = activity,
            title = "Add Chat Contact",
            subtitle = "Authenticate before importing chat keys",
            onSuccess = { saveValidatedContact() },
            onError = { error = it }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan QR") },
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = RoundedCornerShape(28.dp), color = Color.White, tonalElevation = 2.dp) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = Color(0xFF7C3AED))
                    Spacer(Modifier.height(12.dp))
                    Text("Import MaunKavach contact QR", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Paste scanned contact data here. The app validates it and requires device authentication before saving chat keys.",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6B5B80)
                    )
                    Spacer(Modifier.height(8.dp))
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFF7C3AED))
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(
                        value = payload,
                        onValueChange = {
                            payload = it
                            error = null
                        },
                        label = { Text("Scanned QR data") },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        minLines = 5
                    )
                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { importContact() },
                        enabled = payload.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Create contact and open chat")
                    }
                }
            }
        }
    }
}
