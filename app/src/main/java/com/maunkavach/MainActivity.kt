package com.maunkavach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import com.maunkavach.navigation.MaunKavachNavHost
import com.maunkavach.security.DeviceIntegrity
import com.maunkavach.ui.theme.MaunKavachTheme

/**
 * Extends FragmentActivity (not plain ComponentActivity) because BiometricPrompt requires it.
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Block screenshots/screen recording/recents thumbnail app-wide. Per spec this should
        // arguably be screen-by-screen (e.g. relaxed on Login), but defaulting to secure-everywhere
        // is the safer posture for a messenger; relax selectively if a future screen needs it.
        DeviceIntegrity.enableScreenshotProtection(this)

        setContent {
            MaunKavachTheme {
                MaunKavachNavHost(activity = this)
            }
        }
    }
}
