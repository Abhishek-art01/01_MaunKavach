package com.maunkavach.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Local-only auth stub for the demo: in a real deployment this would hit the backend's
 * register/login endpoint over TLS (ApiClient), with the server storing only a salted
 * password hash — never anything related to the Vault Key, which is generated/stored
 * entirely on-device after login and never derived from the account password.
 */
@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("MaunKavach", style = MaterialTheme.typography.headlineMedium)
        Text("Encrypted before it leaves your device", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = username, onValueChange = { username = it },
            label = { Text("Username") }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onLoggedIn, modifier = Modifier.fillMaxWidth()) {
            Text("Login")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onLoggedIn, modifier = Modifier.fillMaxWidth()) {
            Text("Register")
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Your Vault Key is generated after login, lives only on this device, " +
                "and is never sent to our server.",
            style = MaterialTheme.typography.labelSmall
        )
    }
}
