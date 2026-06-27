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
import com.maunkavach.network.ApiClient
import com.maunkavach.network.AuthSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(onLoggedIn: (AuthSession) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit(register: Boolean) {
        if (username.isBlank() || password.isBlank()) {
            error = "Username and password are required."
            return
        }
        busy = true
        error = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (register) ApiClient.register(username.trim(), password) else ApiClient.login(username.trim(), password)
                }
            }.onSuccess { session ->
                password = ""
                onLoggedIn(session)
            }.onFailure { throwable ->
                error = throwable.message ?: "Authentication failed."
            }
            busy = false
        }
    }

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
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { submit(register = false) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "Please wait..." else "Login")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { submit(register = true) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
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
