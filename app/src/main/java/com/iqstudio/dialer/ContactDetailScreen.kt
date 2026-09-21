//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later).
// *
//**************************************************

package com.iqstudio.dialer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.BlockedNumberContract
import android.provider.CallLog
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val projection = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION)
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

private fun addToContacts(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)
    intent.putExtra(ContactsContract.Intents.Insert.PHONE, number)
    context.startActivity(intent)
}

// ACTION_DIAL, not ACTION_CALL -- the shortcut intent fires from the
// launcher's process, which doesn't hold CALL_PHONE. DIAL just opens our
// own MainActivity pre-filled (it already has the intent-filter for this),
// one more tap to actually call, no cross-app permission problem.
private fun pinToHomeScreen(context: Context, name: String, number: String) {
    val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
    if (!shortcutManager.isRequestPinShortcutSupported) return
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
    val shortcut = ShortcutInfo.Builder(context, "call_$number")
        .setShortLabel(name)
        .setIcon(android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_launcher_foreground))
        .setIntent(intent)
        .build()
    shortcutManager.requestPinShortcut(shortcut, null)
}

// System already shows its own "Copied" confirmation from API 33 on -- ours
// would just be a second, redundant one.
private fun copyNumber(context: Context, number: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Phone number", number))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }
}

private fun shareNumber(context: Context, name: String?, number: String) {
    val intent = Intent(Intent.ACTION_SEND)
    intent.type = "text/plain"
    intent.putExtra(Intent.EXTRA_TEXT, (name ?: "Contact") + ": " + number)
    context.startActivity(Intent.createChooser(intent, "Share"))
}

private fun setContactRingtone(context: Context, contactId: Long, ringtoneUri: Uri?) {
    val values = ContentValues()
    values.put(ContactsContract.Contacts.CUSTOM_RINGTONE, ringtoneUri?.toString())
    val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId.toString())
    context.contentResolver.update(uri, values, null, null)
}

private fun deleteContact(context: Context, contactId: Long) {
    val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId.toString())
    context.contentResolver.delete(uri, null, null)
}

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

// See RecentsScreen.kt for the same pattern -- NestedPane and
// nestedPaneTransitionSpec (UiComponents.kt) are shared so every
// nested-screen switch in the app reads as one consistent push/pop system.
private sealed interface ContactDetailPane : NestedPane {
    object Main : ContactDetailPane { override val paneDepth = 0 }
    object FullHistory : ContactDetailPane { override val paneDepth = 1 }
    data class Edit(val contactId: Long) : ContactDetailPane { override val paneDepth = 1 }
}

@Composable
fun ContactDetailScreen(phoneNumber: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var lookup by remember { mutableStateOf(ContactLookupResult(null, null)) }
    var history by remember { mutableStateOf<List<CallLogEntry>>(emptyList()) }
    var menuExpanded by remember { mutableStateOf(false) }
    var isBlocked by remember { mutableStateOf(false) }
    var showFullHistory by remember { mutableStateOf(false) }
    var editingContactId by remember { mutableStateOf<Long?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    val formatter = java.text.SimpleDateFormat("MMM d, " + AppPrefs.timePattern(context), java.util.Locale.getDefault())

    LaunchedEffect(phoneNumber, refreshKey) {
        lookup = withContext(Dispatchers.IO) { lookupContactByNumber(context, phoneNumber) }
        history = withContext(Dispatchers.IO) { loadHistoryForNumber(context, phoneNumber) }
        isBlocked = withContext(Dispatchers.IO) { BlockedNumberContract.isBlocked(context, phoneNumber) }
    }

    val pane: ContactDetailPane = when {
        editingContactId != null -> ContactDetailPane.Edit(editingContactId!!)
        showFullHistory -> ContactDetailPane.FullHistory
        else -> ContactDetailPane.Main
    }

    AnimatedContent(
        targetState = pane,
        transitionSpec = { nestedPaneTransitionSpec() },
        label = "contactDetailPane"
    ) { currentPane ->
        when (currentPane) {
            ContactDetailPane.FullHistory -> CallHistoryDetailScreen(phoneNumber = phoneNumber, onBack = { showFullHistory = false })
            is ContactDetailPane.Edit -> EditContactScreen(
                contactId = currentPane.contactId,
                onBack = { editingContactId = null },
                onSaved = { editingContactId = null; refreshKey++ }
            )
            ContactDetailPane.Main -> {
                BackHandler(onBack = onBack)

                val ringtonePickerLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    val pickedUri = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) as? Uri
                    lookup.contactId?.let { id -> setContactRingtone(context, id, pickedUri) }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassIconButton(icon = Icons.Filled.ArrowBack, contentDescription = "Back", onClick = onBack)
                        Box {
                            GlassIconButton(
                                icon = Icons.Filled.MoreVert,
                                contentDescription = "More options",
                                onClick = { menuExpanded = true }
                )
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Copy number") },
                        onClick = {
                            menuExpanded = false
                            copyNumber(context, phoneNumber)
                        }
                    )
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
                            text = { Text("Edit contact") },
                            onClick = {
                                menuExpanded = false
                                lookup.contactId?.let { editingContactId = it }
                            }
                        )
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
                            text = { Text("Place on Home screen") },
                            onClick = {
                                menuExpanded = false
                                pinToHomeScreen(context, lookup.name ?: phoneNumber, phoneNumber)
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

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        phoneNumber,
                        fontSize = 16.sp,
                        color = TextPrimary,
                        modifier = Modifier.clickable { copyNumber(context, phoneNumber) }
                    )
                    Row {
                        GlassIconButton(
                            icon = Icons.Filled.Videocam,
                            contentDescription = "Video call",
                            tint = CallBlue,
                            onClick = { placeVideoCall(context, phoneNumber) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        GlassIconButton(
                            icon = Icons.Filled.Call,
                            contentDescription = "Call",
                            tint = CallGreen,
                            onClick = { placeCall(context, phoneNumber) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Call history", fontSize = 14.sp, color = TextSecondary)
                    if (history.isNotEmpty()) {
                        Text(
                            "See all",
                            fontSize = 13.sp,
                            color = CallBlue,
                            modifier = Modifier.clickable { showFullHistory = true }
                        )
                    }
                }
                if (history.isEmpty()) {
                    Text(
                        "No calls with this number yet",
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                } else {
                    history.take(5).forEach { entry ->
                        val missed = entry.type == CallLog.Calls.MISSED_TYPE || entry.type == CallLog.Calls.REJECTED_TYPE
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                            Text(
                                formatter.format(java.util.Date(entry.date)),
                                fontSize = 15.sp,
                                color = if (missed) CallRed else TextPrimary
                            )
                            Text(describeCallHistory(entry), fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
            }
        }
    }
}
