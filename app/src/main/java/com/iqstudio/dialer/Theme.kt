//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later). 
// * 
//**************************************************

// theme & colours etc. 
package com.iqstudio.dialer

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Colour 
val CallGreen = Color(0xFF4ADE80)
val CallRed = Color(0xFFEF4444)
val CallBlue = Color(0xFF4D8EFF)

// Accent palette; slightly deeper tones on the light theme for contrast.
val AccentIndigo: Color get() = if (AppTheme.isLight) Color(0xFF4F5FE0) else Color(0xFF7C8CFF)
val AccentViolet: Color get() = if (AppTheme.isLight) Color(0xFF7C5CE0) else Color(0xFFA78BFA)
val AccentTeal: Color get() = if (AppTheme.isLight) Color(0xFF0F9E8E) else Color(0xFF2DD4BF)
val AccentAmber: Color get() = if (AppTheme.isLight) Color(0xFFC98500) else Color(0xFFFBBF24)
val AccentOrange: Color get() = if (AppTheme.isLight) Color(0xFFE06A12) else Color(0xFFFB923C)
val AccentPink: Color get() = if (AppTheme.isLight) Color(0xFFD6478B) else Color(0xFFF472B6)
val AccentSoft: Color get() = if (AppTheme.isLight) Color(0xFF4A56C9) else Color(0xFFB9C3FF)

// Minimal interface surfaces.
val MinimalBackground: Color get() = if (AppTheme.isLight) Color(0xFFF4F6F8) else Color(0xFF111416)
val MinimalSurface: Color get() = if (AppTheme.isLight) Color(0xFFFFFFFF) else Color(0xFF1C2023)
val MinimalSurfaceHigh: Color get() = if (AppTheme.isLight) Color(0xFFE9ECEF) else Color(0xFF262B2F)
val MinimalDivider: Color get() = if (AppTheme.isLight) Color(0xFFDADFE4) else Color(0xFF2E3438)

val SurfaceCard: Color get() = if (AppTheme.isLight) Color(0xFFFFFFFF) else Color(0xFF1D2022)
val SurfaceCardHigh: Color get() = if (AppTheme.isLight) Color(0xFFEDEFF2) else Color(0xFF272A2C)
val TextPrimary: Color get() = if (AppTheme.isLight) Color(0xFF14181B) else Color(0xFFE0E3E5)
val TextSecondary: Color get() = if (AppTheme.isLight) Color(0xFF5A6068) else Color(0xFFC2C6D6)
val OutlineFaint: Color get() = if (AppTheme.isLight) Color(0xFFC9CED5) else Color(0xFF424754)

// Icon and text colour for content drawn on glass or minimal surfaces.
val ContentPrimary: Color get() = if (AppTheme.isLight) TextPrimary else Color.White

private fun darkScheme() = darkColorScheme(
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

private fun lightScheme() = lightColorScheme(
    primary = Color(0xFF3D55D6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF001258),
    background = Color(0xFFF4F6F8),
    onBackground = TextPrimary,
    surface = Color(0xFFF4F6F8),
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF747985),
    outlineVariant = OutlineFaint,
    error = CallRed,
    onError = Color.White
)

@Composable
fun IQDialerTheme(content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    SideEffect { AppTheme.updateSystem(systemDark) }

    val light = AppTheme.isLight
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = light
                controller.isAppearanceLightNavigationBars = light
            }
        }
    }

    // The advanced interface keeps the original default content colour.
    val contentColor = if (VisualMode.advanced) LocalContentColor.current else TextPrimary
    MaterialTheme(colorScheme = if (light) lightScheme() else darkScheme()) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
    }
}

// will be added new things here. soon.
// MORE - MORE 
