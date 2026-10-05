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
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat

data class CallLogEntry(
    val number: String,
    val name: String?,
    val type: Int,
    val date: Long,
    val duration: Long = 0,
    val id: Long = 0
)

fun hasCallLogPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
        PackageManager.PERMISSION_GRANTED

fun hasContactsPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
        PackageManager.PERMISSION_GRANTED

// Call LOG entry type
fun callTypeLabel(type: Int): String = when (type) {
    CallLog.Calls.INCOMING_TYPE -> "Incoming"
    CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
    CallLog.Calls.MISSED_TYPE -> "Missed"
    CallLog.Calls.REJECTED_TYPE -> "Rejected"
    CallLog.Calls.BLOCKED_TYPE -> "Blocked"
    else -> "Call"
}

// LIVE call state (Call.STATE_*) 
fun deleteCallLogEntries(context: Context, ids: List<Long>) {
    if (ids.isEmpty()) return
    try {
        val where = CallLog.Calls._ID + " IN (" + ids.joinToString(",") + ")"
        context.contentResolver.delete(CallLog.Calls.CONTENT_URI, where, null)
    } catch (e: SecurityException) {
        android.util.Log.e("CallLogDelete", "no WRITE_CALL_LOG", e)
    }
}

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


// !!! NOTE !!!
// Requests bidirectional video; whether it actually becomes a video call
// depends on VoLTE-video support on both the device/carrier and the far
// end. That negotiation happens at the platform/carrier level, not here --
// unsupported just means the call proceeds as audio, which is the fallback.

// please don't Report anything regarding this X


fun placeVideoCall(context: Context, number: String) {
    val telecomManager = context.getSystemService(TelecomManager::class.java)
    val extras = Bundle().apply {
        putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, VideoProfile.STATE_BIDIRECTIONAL)
    }
    telecomManager?.placeCall(Uri.fromParts("tel", number, null), extras)
}

fun callTypeVisual(type: Int): Pair<ImageVector, Color> = when (type) {
    CallLog.Calls.INCOMING_TYPE -> Icons.Filled.CallReceived to AccentTeal
    CallLog.Calls.OUTGOING_TYPE -> Icons.Filled.CallMade to AccentIndigo
    CallLog.Calls.MISSED_TYPE -> Icons.Filled.CallMissed to CallRed
    CallLog.Calls.REJECTED_TYPE -> Icons.Filled.PhoneDisabled to AccentOrange
    CallLog.Calls.BLOCKED_TYPE -> Icons.Filled.Block to CallRed
    else -> Icons.Filled.Phone to AccentViolet
}

fun describeCallHistory(entry: CallLogEntry): String {
    val durationText = if (entry.duration >= 60) {
        val m = entry.duration / 60
        val s = entry.duration % 60
        if (s == 0L) "${m}m" else "${m}m ${s}s"
    } else {
        "${entry.duration} sec"
    }
    return when (entry.type) {
        CallLog.Calls.INCOMING_TYPE -> "Incoming: $durationText"
        CallLog.Calls.OUTGOING_TYPE -> "Outgoing: $durationText"
        CallLog.Calls.MISSED_TYPE -> "Missed call"
        CallLog.Calls.REJECTED_TYPE -> "Declined"
        CallLog.Calls.BLOCKED_TYPE -> "Blocked"
        else -> callTypeLabel(entry.type)
    }
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
