//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later). 
// * 
//**************************************************

// Receiver service, only include the service that are needed. lese this can slow down the performance on Incoming call, you've been warned! - @IQ_HARRRY_07

package com.iqstudio.dialer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

const val ACTION_ANSWER_CALL = "com.iqstudio.dialer.ACTION_ANSWER_CALL"
const val ACTION_DECLINE_CALL = "com.iqstudio.dialer.ACTION_DECLINE_CALL"

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val call = CallStateHolder.calls.value.firstOrNull { it.state == android.telecom.Call.STATE_RINGING }?.call
            ?: CallStateHolder.activeCall.value
        when (intent.action) {
            ACTION_ANSWER_CALL -> call?.let { it.answer(it.details.videoState) }
            ACTION_DECLINE_CALL -> call?.reject(false, null)
        }
        NotificationManagerCompat.from(context).cancel(INCOMING_CALL_NOTIFICATION_ID)
    }
}

// what else do you expect? 😆
