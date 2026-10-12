//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later).
// *
//**************************************************

package com.iqstudio.dialer

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// Interface mode. Minimal is the default; advanced enables the glass interface and effects.
object VisualMode {
    var advanced by mutableStateOf(false)
        private set

    fun load(context: Context) {
        advanced = AppPrefs.advancedVisuals(context)
    }

    fun set(context: Context, enabled: Boolean) {
        AppPrefs.setAdvancedVisuals(context, enabled)
        advanced = enabled
        if (enabled) {
            val appContext = context.applicationContext
            Thread { BackgroundImageCache.get(appContext) }.start()
        }
    }
}
