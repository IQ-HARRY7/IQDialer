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
}

// khi khi 😁