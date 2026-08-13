//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later). 
// * 
//**************************************************

// 1st screen. 
// working fine, but need to make it more smooth & animated. 

package com.iqstudio.dialer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GroupedCallLogEntry(
    val number: String,
    val name: String?,
    val type: Int,
    val date: Long,
    val count: Int
)

private fun loadCallLog(context: Context): List<CallLogEntry> {
    if (!hasCallLogPermission(context)) return emptyList()
    val entries = mutableListOf<CallLogEntry>()
    val projection = arrayOf(
        CallLog.Calls.NUMBER,
        CallLog.Calls.CACHED_NAME,
        CallLog.Calls.TYPE,
        CallLog.Calls.DATE
    )
    context.contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        projection,
        null, null,
        CallLog.Calls.DATE + " DESC LIMIT 200"
    )?.use { cursor ->
        val numberIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
        val nameIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
        val typeIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
        val dateIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
        while (cursor.moveToNext()) {
            entries.add(
                CallLogEntry(
                    number = cursor.getString(numberIdx) ?: "",
                    name = cursor.getString(nameIdx),
                    type = cursor.getInt(typeIdx),
                    date = cursor.getLong(dateIdx)
                )
            )
        }
    }
    return entries
}

private fun groupConsecutive(entries: List<CallLogEntry>): List<GroupedCallLogEntry> {
    val grouped = mutableListOf<GroupedCallLogEntry>()
    for (entry in entries) {
        val last = grouped.lastOrNull()
        if (last != null && last.number == entry.number) {
            grouped[grouped.lastIndex] = last.copy(count = last.count + 1)
        } else {
            grouped.add(GroupedCallLogEntry(entry.number, entry.name, entry.type, entry.date, 1))
        }
    }
    return grouped
}

private fun callDirectionGlyph(type: Int): String = when (type) {
    CallLog.Calls.OUTGOING_TYPE -> "\u2197"
    CallLog.Calls.MISSED_TYPE -> "\u2199"
    CallLog.Calls.REJECTED_TYPE -> "\u2298"
    CallLog.Calls.BLOCKED_TYPE -> "\u2298"
    CallLog.Calls.INCOMING_TYPE -> "\u2199"
    else -> "\u2022"
}

private fun callDirectionColor(type: Int): Color = when (type) {
    CallLog.Calls.OUTGOING_TYPE -> Color(0xFFADC6FF)
    CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE, CallLog.Calls.BLOCKED_TYPE -> CallRed
    else -> TextSecondary
}

@Composable
fun RecentsScreen(refreshKey: Int) {
    val context = LocalContext.current
    var rawEntries by remember { mutableStateOf<List<CallLogEntry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selectedNumber by remember { mutableStateOf<String?>(null) }
    var showDialpad by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        rawEntries = withContext(Dispatchers.IO) { loadCallLog(context) }
        loaded = true
    }

    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
        return
    }

    selectedNumber?.let { number ->
        ContactDetailScreen(phoneNumber = number, onBack = { selectedNumber = null })
        return
    }

    val grouped = remember(rawEntries) { groupConsecutive(rawEntries) }
    val filtered = remember(grouped, query) {
        if (query.isBlank()) grouped
        else grouped.filter {
            (it.name?.contains(query, ignoreCase = true) == true) ||
                it.number.contains(query, ignoreCase = true)
        }
    }
    val formatter = java.text.SimpleDateFormat("MMM d, " + AppPrefs.timePattern(context), java.util.Locale.getDefault())

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recents", fontSize = 26.sp)
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search recents") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (!loaded) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            } else if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        if (rawEntries.isEmpty()) "No call history yet" else "No matches",
                        color = TextSecondary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filtered) { entry ->
                        val missed = entry.type == CallLog.Calls.MISSED_TYPE || entry.type == CallLog.Calls.REJECTED_TYPE
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 3.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { selectedNumber = entry.number }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ContactAvatar(name = entry.name)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    entry.name ?: entry.number,
                                    fontSize = 16.sp,
                                    color = if (missed) CallRed else TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        callDirectionGlyph(entry.type),
                                        fontSize = 12.sp,
                                        color = callDirectionColor(entry.type)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        callTypeLabel(entry.type) +
                                            (if (entry.count > 1) " (" + entry.count + ")" else ""),
                                        fontSize = 13.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                            Text(
                                formatter.format(java.util.Date(entry.date)),
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        HorizontalDivider(color = OutlineFaint.copy(alpha = 0.3f))
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showDialpad = !showDialpad },
            containerColor = CallGreen,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) {
            Icon(Icons.Filled.Call, contentDescription = "Dialpad")
        }

        if (showDialpad) {
            EmbeddedDialpad(
                onCall = { number ->
                    placeCall(context, number)
                    showDialpad = false
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

private data class DialKey(val digit: String, val sub: String)

private val DIAL_ROWS = listOf(
    listOf(DialKey("1", ""), DialKey("2", "ABC"), DialKey("3", "DEF")),
    listOf(DialKey("4", "GHI"), DialKey("5", "JKL"), DialKey("6", "MNO")),
    listOf(DialKey("7", "PQRS"), DialKey("8", "TUV"), DialKey("9", "WXYZ")),
    listOf(DialKey("*", ""), DialKey("0", "+"), DialKey("#", ""))
)

@Composable
private fun EmbeddedDialpad(onCall: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var number by remember { mutableStateOf("") }

    val requestCallPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) onCall(number) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceCard)
            .padding(top = 16.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(number.ifEmpty { " " }, fontSize = 28.sp, color = TextPrimary, modifier = Modifier.padding(bottom = 12.dp))

        DIAL_ROWS.forEach { row ->
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .padding(6.dp)
                            .size(70.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceCardHigh)
                            .pressScale { number += key.digit },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(key.digit, fontSize = 24.sp, color = TextPrimary)
                            if (key.sub.isNotEmpty()) {
                                Text(key.sub, fontSize = 9.sp, color = TextSecondary)
                            }
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(CallGreen)
                    .clickable {
                        if (number.isNotEmpty()) {
                            val granted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.CALL_PHONE
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) onCall(number)
                            else requestCallPermission.launch(Manifest.permission.CALL_PHONE)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Call, contentDescription = "Call", tint = Color.White)
            }
            if (number.isNotEmpty()) {
                Spacer(modifier = Modifier.width(24.dp))
                Text(
                    "Delete",
                    color = TextSecondary,
                    modifier = Modifier.clickable { number = number.dropLast(1) }
                )
            }
        }
    }
}

// 1st screen. 