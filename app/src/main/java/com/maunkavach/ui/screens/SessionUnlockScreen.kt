package com.maunkavach.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.maunkavach.network.AuthSession
import com.maunkavach.security.BiometricHelper
import com.maunkavach.security.SessionStore

@Composable
fun SessionUnlockScreen(
    activity: FragmentActivity,
    sessionStore: SessionStore,
    onUnlocked: (AuthSession) -> Unit,
    onPasswordLogin: () -> Unit,
    onForgetSession: () -> Unit
) {
    var error by remember { mutableStateOf<String?>(null) }

    fun unlockSavedSession() {
        if (!BiometricHelper.canUseBiometrics(activity)) {
            error = "Set a device PIN, password, fingerprint, or face unlock to use quick unlock."
            return
        }

        error = null
        BiometricHelper.promptUnlock(
            activity = activity,
            title = "Unlock MaunKavach",
            subtitle = "Use your device PIN, fingerprint, or face unlock",
            onSuccess = {
                val session = sessionStore.load()
                if (session == null) {
                    error = "Saved login could not be opened. Please log in with your password."
                } else {
                    onUnlocked(session)
                }
            },
            onError = { error = it }
        )
    }

    LaunchedEffect(Unit) {
        unlockSavedSession()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null)
        Spacer(Modifier.height(16.dp))
        Text("Unlock MaunKavach", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Use your device unlock to continue.", style = MaterialTheme.typography.bodyMedium)
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = { unlockSavedSession() }, modifier = Modifier.fillMaxWidth()) {
            Text("Unlock")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onPasswordLogin, modifier = Modifier.fillMaxWidth()) {
            Text("Use password")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onForgetSession, modifier = Modifier.fillMaxWidth()) {
            Text("Forget saved login")
        }
    }
}
