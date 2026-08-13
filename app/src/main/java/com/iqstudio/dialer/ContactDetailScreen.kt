//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later). 
// * 
//**************************************************

package com.iqstudio.dialer

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.BlockedNumberContract
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ContactLookupResult(val name: String?, val contactId: Long?)

private fun lookupContactByNumber(context: Context, number: String): ContactLookupResult {
    if (!hasContactsPermission(context)) return ContactLookupResult(null, null)
    val uri = Uri.withAppendedPath(
        ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
        Uri.encode(number)
    )
    context.contentResolver.query(
        uri,
        arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup._ID),
        null, null, null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME)
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup._ID)
            return ContactLookupResult(cursor.getString(nameIdx), cursor.getLong(idIdx))
        }
    }
    return ContactLookupResult(null, null)
}

private fun loadHistoryForNumber(context: Context, number: String): List<CallLogEntry> {
    if (!hasCallLogPermission(context)) return emptyList()
    val entries = mutableListOf<CallLogEntry>()
    val projection = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE)
    context.contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        projection,
        CallLog.Calls.NUMBER + " = ?",
        arrayOf(number),
        CallLog.Calls.DATE + " DESC LIMIT 50"
    )?.use { cursor ->
        val numberIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
        val nameIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
        val typeIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
        val dateIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
        while (cursor.moveToNext()) {
            entries.add(
                CallLogEntry(
                    number = cursor.getString(numberIdx) ?: number,
                    name = cursor.getString(nameIdx),
                    type = cursor.getInt(typeIdx),
                    date = cursor.getLong(dateIdx)
                )
            )
        }
    }
    return entries
}

private fun addToContacts(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)
    intent.putExtra(ContactsContract.Intents.Insert.PHONE, number)
    context.startActivity(intent)
}

private fun shareNumber(context: Context, name: String?, number: String) {
    val intent = Intent(Intent.ACTION_SEND)
    intent.type = "text/plain"
    intent.putExtra(Intent.EXTRA_TEXT, (name ?: "Contact") + ": " + number)
    context.startActivity(Intent.createChooser(intent, "Share"))
}

// wish. 
private fun setContactRingtone(context: Context, contactId: Long, ringtoneUri: Uri?) {
    val values = ContentValues()
    values.put(ContactsContract.Contacts.CUSTOM_RINGTONE, ringtoneUri?.toString())
    val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId.toString())
    context.contentResolver.update(uri, values, null, null)
}

// WRITE_ACCESS manage - obv
private fun deleteContact(context: Context, contactId: Long) {
    val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId.toString())
    context.contentResolver.delete(uri, null, null)
}

// requires BlockedNumberContract.canCurrentUserBlockNumbers(context)
private fun blockNumber(context: Context, number: String) {
    if (!BlockedNumberContract.canCurrentUserBlockNumbers(context)) return
    val values = ContentValues()
    values.put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number)
    context.contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values)
}

private fun unblockNumber(context: Context, number: String) {
    context.contentResolver.delete(
        BlockedNumberContract.BlockedNumbers.CONTENT_URI,
        BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER + " = ?",
        arrayOf(number)
    )
}

@Composable
fun ContactDetailScreen(phoneNumber: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var lookup by remember { mutableStateOf(ContactLookupResult(null, null)) }
    var history by remember { mutableStateOf<List<CallLogEntry>>(emptyList()) }
    var menuExpanded by remember { mutableStateOf(false) }
    var isBlocked by remember { mutableStateOf(false) }
    val formatter = java.text.SimpleDateFormat("MMM d, " + AppPrefs.timePattern(context), java.util.Locale.getDefault())

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val pickedUri = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) as? Uri
        lookup.contactId?.let { id -> setContactRingtone(context, id, pickedUri) }
    }

    LaunchedEffect(phoneNumber) {
        lookup = withContext(Dispatchers.IO) { lookupContactByNumber(context, phoneNumber) }
        history = withContext(Dispatchers.IO) { loadHistoryForNumber(context, phoneNumber) }
        isBlocked = withContext(Dispatchers.IO) { BlockedNumberContract.isBlocked(context, phoneNumber) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    if (lookup.contactId == null) {
                        DropdownMenuItem(
                            text = { Text("Add to contacts") },
                            onClick = {
                                menuExpanded = false
                                addToContacts(context, phoneNumber)
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Set ringtone") },
                            onClick = {
                                menuExpanded = false
                                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                }
                                ringtonePickerLauncher.launch(intent)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            onClick = {
                                menuExpanded = false
                                shareNumber(context, lookup.name, phoneNumber)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete contact") },
                            onClick = {
                                menuExpanded = false
                                lookup.contactId?.let { deleteContact(context, it) }
                                onBack()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (isBlocked) "Unblock" else "Block") },
                        onClick = {
                            menuExpanded = false
                            if (isBlocked) {
                                unblockNumber(context, phoneNumber)
                            } else {
                                blockNumber(context, phoneNumber)
                            }
                            isBlocked = !isBlocked
                        }
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(SurfaceCardHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(lookup.name ?: "Unknown contact", fontSize = 24.sp, color = TextPrimary)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(phoneNumber, fontSize = 16.sp, color = TextPrimary)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(CallGreen)
                    .clickable { placeCall(context, phoneNumber) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Call, contentDescription = "Call", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider()

        Text(
            "Call history",
            fontSize = 14.sp,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )

        if (history.isEmpty()) {
            Text("No calls with this number yet", color = TextSecondary, modifier = Modifier.padding(horizontal = 20.dp))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(history) { entry ->
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Text(callTypeLabel(entry.type), fontSize = 15.sp, color = TextPrimary)
                        Text(formatter.format(java.util.Date(entry.date)), fontSize = 12.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}

// YAY! Finally! 