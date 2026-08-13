//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later). 
// * 
//**************************************************

// theme & colours etc. 
package com.iqstudio.dialer

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Colour 
val CallGreen = Color(0xFF4ADE80)
val CallRed = Color(0xFFEF4444)

val SurfaceCard = Color(0xFF1D2022)
val SurfaceCardHigh = Color(0xFF272A2C)
val TextPrimary = Color(0xFFE0E3E5)
val TextSecondary = Color(0xFFC2C6D6)
val OutlineFaint = Color(0xFF424754)

private val IQDialerColors = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF002E6A),
    primaryContainer = Color(0xFF4D8EFF),
    onPrimaryContainer = Color(0xFF00285D),
    background = Color(0xFF101415),
    onBackground = TextPrimary,
    surface = Color(0xFF101415),
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF8C909F),
    outlineVariant = OutlineFaint,
    error = CallRed,
    onError = Color.White
)

@Composable
fun IQDialerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = IQDialerColors, content = content)
}

// will be added new things here. soon. 