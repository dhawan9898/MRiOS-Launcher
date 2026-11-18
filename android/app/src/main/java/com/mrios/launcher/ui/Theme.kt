package com.mrios.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object IOSColors {
    val Blue = Color(0xFF0A84FF)
    val Red = Color(0xFFFF3B30)
    val Green = Color(0xFF30D158)
    val Orange = Color(0xFFFF9F0A)
    val Yellow = Color(0xFFFFD60A)
    val Label = Color.White
    val Secondary = Color(0xB3EBEBF5)
    val Tertiary = Color(0x66EBEBF5)
    val Separator = Color(0x33FFFFFF)
}

@Composable
fun MRiOSTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = IOSColors.Blue,
            onPrimary = Color.White,
            secondary = IOSColors.Blue,
            background = Color.Black,
            surface = Color(0xFF1C1C1E),
            error = IOSColors.Red,
        ),
        content = content,
    )
}
