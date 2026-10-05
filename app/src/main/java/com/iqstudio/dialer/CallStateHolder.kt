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

// Snapshot of one top-level call.

data class CallInfo(
    val call: Call,
    val state: Int,
    val number: String,
    val isConference: Boolean,
    val children: List<CallInfo>,
    val canMerge: Boolean,
    val canSwapConference: Boolean,
    val canSplit: Boolean,
    val canDisconnectFromConference: Boolean,
    val videoState: Int,
    val connectedAtElapsed: Long?
)

fun pickPrimary(list: List<CallInfo>): CallInfo? =
    list.firstOrNull {
        it.state == Call.STATE_ACTIVE || it.state == Call.STATE_DIALING ||
            it.state == Call.STATE_CONNECTING || it.state == Call.STATE_PULLING_CALL
    } ?: list.firstOrNull { it.state == Call.STATE_RINGING }
        ?: list.firstOrNull { it.state == Call.STATE_HOLDING }
        ?: list.firstOrNull()

// object reference. 
object CallStateHolder {
    private val _calls = MutableStateFlow<List<CallInfo>>(emptyList())
    val calls: StateFlow<List<CallInfo>> = _calls

    // The call the UI treats as "the" call (bubble, receiver fallback) -- always the primary of calls.
    private val _activeCall = MutableStateFlow<Call?>(null)
    val activeCall: StateFlow<Call?> = _activeCall

    private val _audioState = MutableStateFlow<CallAudioState?>(null)
    val audioState: StateFlow<CallAudioState?> = _audioState

    // background - managed by different service. (default - black, else the picture user has set ✌️)
    
    private val _activeBackground = MutableStateFlow<BackgroundItem?>(null)
    val activeBackground: StateFlow<BackgroundItem?> = _activeBackground

    // in call background...
    private var pendingBackground: BackgroundItem? = null

    // Screening silenced the system ringer because IncomingRinger will ring instead.
    @Volatile
    var customRingerArmed = false

    fun setCalls(list: List<CallInfo>) {
        _calls.value = list
        _activeCall.value = pickPrimary(list)?.call
        if (list.isEmpty()) _activeBackground.value = null
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

// needs huge improvements. 
// khi khi 😁

