//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later). 
// * 
//**************************************************

package com.iqstudio.dialer

import android.telecom.Call
import android.telecom.CallAudioState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// object reference. 
object CallStateHolder {
    private val _activeCall = MutableStateFlow<Call?>(null)
    val activeCall: StateFlow<Call?> = _activeCall

    private val _audioState = MutableStateFlow<CallAudioState?>(null)
    val audioState: StateFlow<CallAudioState?> = _audioState

    // background - managed by different service. 
    private val _activeBackground = MutableStateFlow<BackgroundItem?>(null)
    val activeBackground: StateFlow<BackgroundItem?> = _activeBackground

    // set by TurboCallScreeningService (picked early so it can also decide whether to
    // silence the ringer), consumed once by TurboInCallService so both agree on the
    // same item instead of each rolling their own random pick.
    private var pendingBackground: BackgroundItem? = null

    fun setCall(call: Call?) {
        _activeCall.value = call
        if (call == null) _activeBackground.value = null
    }

    fun setAudioState(state: CallAudioState?) {
        _audioState.value = state
    }

    fun setActiveBackground(item: BackgroundItem?) {
        _activeBackground.value = item
    }

    fun setPendingBackground(item: BackgroundItem?) {
        pendingBackground = item
    }

    fun takePendingBackground(): BackgroundItem? {
        val item = pendingBackground
        pendingBackground = null
        return item
    }
}

// khi khi 😁
