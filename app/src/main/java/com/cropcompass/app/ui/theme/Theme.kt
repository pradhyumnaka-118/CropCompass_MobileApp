package com.cropcompass.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = lightColorScheme(
    primary = Color(0xFF2E7D32), onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F5E9), onPrimaryContainer = Color(0xFF1B5E20),
    secondary = Color(0xFF586B47), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F5E9), onSecondaryContainer = Color(0xFF1B5E20),
    tertiary = Color(0xFF406650), tertiaryContainer = Color(0xFFE8F5E9),
    background = Color(0xFFF8FAF8),
    surface = Color.White, onSurface = Color(0xFF1F2937), onBackground = Color(0xFF1F2937),
    surfaceVariant = Color(0xFFF0F4EE), outline = Color(0xFF6F7B6E),
    outlineVariant = Color(0xFFCDDACC), surfaceTint = Color(0xFF2E7D32),
    surfaceContainer = Color(0xFFF0F4EE), surfaceContainerHigh = Color(0xFFE8F5E9),
    surfaceContainerHighest = Color(0xFFE4ECE2), surfaceContainerLow = Color(0xFFF8FAF8),
    surfaceContainerLowest = Color.White
)
@Composable fun CropCompassTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = Colors, content = content) }
