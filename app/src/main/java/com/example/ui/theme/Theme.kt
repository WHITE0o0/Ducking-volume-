package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MinimalColorScheme = lightColorScheme(
    primary = MinimalTextDarkGray,
    onPrimary = MinimalBackgroundWhite,
    primaryContainer = MinimalCardLightGray,
    onPrimaryContainer = MinimalTextDarkGray,
    secondary = MinimalAccentLightGray,
    onSecondary = MinimalBackgroundWhite,
    secondaryContainer = MinimalCardLightGray,
    onSecondaryContainer = MinimalTextDarkGray,
    tertiary = MinimalThresholdRed,
    onTertiary = MinimalBackgroundWhite,
    background = MinimalBackgroundWhite,
    onBackground = MinimalTextDarkGray,
    surface = MinimalCardLightGray,
    onSurface = MinimalTextDarkGray,
    surfaceVariant = Color(0xFFEBEBEB),
    onSurfaceVariant = MinimalAccentLightGray,
    outline = MinimalAccentLightGray,
    error = MinimalThresholdRed
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = MinimalColorScheme,
        typography = Typography,
        content = content
    )
}
