//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later).
// *
//**************************************************

// Full call history for one number -- reached from ContactDetailScreen's
// "See all" link. The inline list there stays capped at a quick preview;
// this one is the actual unlimited log.
package com.iqstudio.dialer

import android.content.Context
import android.provider.CallLog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun loadFullHistory(context: Context, number: String): List<CallLogEntry> {
    if (!hasCallLogPermission(context)) return emptyList()
    val entries = mutableListOf<CallLogEntry>()
    context.contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION),
        CallLog.Calls.NUMBER + " = ?",
        arrayOf(number),
        CallLog.Calls.DATE + " DESC"
    )?.use { cursor ->
        val numberIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
        val nameIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
        val typeIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
        val dateIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
        val durationIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
        while (cursor.moveToNext()) {
            entries.add(
                CallLogEntry(
                    number = cursor.getString(numberIdx) ?: number,
                    name = cursor.getString(nameIdx),
                    type = cursor.getInt(typeIdx),
                    date = cursor.getLong(dateIdx),
                    duration = cursor.getLong(durationIdx)
                )
            )
        }
    }
    return entries
}

@Composable
fun CallHistoryDetailScreen(phoneNumber: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var history by remember { mutableStateOf<List<CallLogEntry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    val formatter = java.text.SimpleDateFormat("MMM d, " + AppPrefs.timePattern(context), java.util.Locale.getDefault())

    BackHandler(onBack = onBack)

    LaunchedEffect(phoneNumber) {
        history = withContext(Dispatchers.IO) { loadFullHistory(context, phoneNumber) }
        loaded = true
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(icon = Icons.Filled.ArrowBack, contentDescription = "Back", onClick = onBack)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Call history details", fontSize = 20.sp, color = TextPrimary)
        }

        val listState = when {
            !loaded -> "loading"
            history.isEmpty() -> "empty"
            else -> "list"
        }
        Crossfade(targetState = listState, label = "callHistoryState") { state ->
            when (state) {
                "loading" -> Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                "empty" -> Box(modifier = Modifier.fillMaxSize()) {
                    Text("No call history", color = TextSecondary, modifier = Modifier.align(Alignment.Center))
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(history, key = { "${it.date}" }) { entry ->
                        val missed = entry.type == CallLog.Calls.MISSED_TYPE || entry.type == CallLog.Calls.REJECTED_TYPE
                        GlassCard(modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp).animateItem()) {
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(
                                    formatter.format(java.util.Date(entry.date)),
                                    fontSize = 15.sp,
                                    color = if (missed) CallRed else TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(describeCallHistory(entry), fontSize = 13.sp, color = TextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Pro me. i created a different screen & file for this 🤡