//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later). 
// * 
//**************************************************

// information about call log.
package com.iqstudio.dialer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

data class CallLogEntry(
    val number: String,
    val name: String?,
    val type: Int,
    val date: Long
)

fun hasCallLogPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
        PackageManager.PERMISSION_GRANTED

fun hasContactsPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
        PackageManager.PERMISSION_GRANTED

// Call LOG entry type (history record) -- Incoming/Outgoing/Missed/etc.
fun callTypeLabel(type: Int): String = when (type) {
    CallLog.Calls.INCOMING_TYPE -> "Incoming"
    CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
    CallLog.Calls.MISSED_TYPE -> "Missed"
    CallLog.Calls.REJECTED_TYPE -> "Rejected"
    CallLog.Calls.BLOCKED_TYPE -> "Blocked"
    else -> "Call"
}

// LIVE call state (Call.STATE_*) -- used by both the notification and the
// in-call screen so the two stay in sync.
fun callStateLabel(state: Int): String = when (state) {
    Call.STATE_RINGING -> "Incoming call"
    Call.STATE_DIALING -> "Calling..."
    Call.STATE_CONNECTING -> "Connecting..."
    Call.STATE_ACTIVE -> "In call"
    Call.STATE_HOLDING -> "On hold"
    Call.STATE_DISCONNECTED -> "Call ended"
    else -> "..."
}

fun placeCall(context: Context, number: String) {
    val telecomManager = context.getSystemService(TelecomManager::class.java)
    telecomManager?.placeCall(Uri.fromParts("tel", number, null), null)
}

// Lookup is frustrating. 
fun lookupContactName(context: Context, number: String): String? {
    if (!hasContactsPermission(context)) return null
    val lookupUri = Uri.withAppendedPath(
        ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
        Uri.encode(number)
    )
    return try {
        context.contentResolver.query(
            lookupUri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else {
                null
            }
        }
    } catch (e: Exception) {
        null
    }
}

// wen give star? 