package com.maunkavach.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VaultGreen = Color(0xFF1B8A5A)
private val VaultDark = Color(0xFF0B141A)
private val VaultAccent = Color(0xFF53BDEB)

private val DarkColors = darkColorScheme(
    primary = VaultGreen,
    secondary = VaultAccent,
    background = VaultDark,
    surface = Color(0xFF1F2C34)
)

private val LightColors = lightColorScheme(
    primary = VaultGreen,
    secondary = VaultAccent,
    background = Color(0xFFF7F7F7),
    surface = Color.White
)

@Composable
fun MaunKavachTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
