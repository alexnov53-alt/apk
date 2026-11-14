package com.chewplay.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ChewPlayColors = darkColorScheme(
    primary = Color(0xFF9CCC65),
    onPrimary = Color(0xFF141414),
    secondary = Color(0xFFFFB74D),
    onSecondary = Color(0xFF141414),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF141414),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF222222),
    onSurfaceVariant = Color(0xFFCFCFCF),
)

@Composable
fun ChewPlayTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ChewPlayColors, content = content)
}
