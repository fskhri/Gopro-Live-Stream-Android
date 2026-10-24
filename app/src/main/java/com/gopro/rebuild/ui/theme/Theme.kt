package com.gopro.rebuild.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GoProColorScheme = darkColorScheme(
    primary = Color(0xFF00A8E8),        // GoPro Blue
    onPrimary = Color.White,
    secondary = Color(0xFF00D4FF),
    onSecondary = Color.Black,
    tertiary = Color(0xFF7CFC00),       // Recording green
    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF1E1E1E),
    onSurface = Color.White,
    error = Color(0xFFCF6679),
    onError = Color.Black
)

@Composable
fun GoProTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = GoProColorScheme,
        content = content
    )
}
