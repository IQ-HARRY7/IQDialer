//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later).
// *
//**************************************************

package com.iqstudio.dialer

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeChoice(val label: String) {
    System("Follow system"),
    Light("Light"),
    Dark("Dark")
}

// Light and dark themes apply to the minimal interface only; the advanced interface is always dark.
object AppTheme {
    var choice by mutableStateOf(ThemeChoice.System)
        private set

    var systemDark by mutableStateOf(true)
        private set

    private var darkLocks by mutableIntStateOf(0)

    val isLight: Boolean
        get() = !VisualMode.advanced && darkLocks == 0 &&
            (choice == ThemeChoice.Light || (choice == ThemeChoice.System && !systemDark))

    fun load(context: Context) {
        choice = AppPrefs.themeChoice(context)
        systemDark = isNight(context)
    }

    fun set(context: Context, value: ThemeChoice) {
        AppPrefs.setThemeChoice(context, value)
        choice = value
    }

    fun updateSystem(dark: Boolean) {
        if (systemDark != dark) systemDark = dark
    }

    // Full-screen dark screens (calls, developer screen) keep the dark theme while they are open.
    fun pushDark() {
        darkLocks++
    }

    fun popDark() {
        if (darkLocks > 0) darkLocks--
    }

    private fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}
