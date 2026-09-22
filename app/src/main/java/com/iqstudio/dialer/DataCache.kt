//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later).
// *
//**************************************************

// simply - the whole cache service. 
package com.iqstudio.dialer

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private fun loadContactsFromProvider(context: Context): List<ContactEntry> {
    if (!hasContactsPermission(context)) return emptyList()
    val entries = mutableListOf<ContactEntry>()
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER
    )
    context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        projection,
        null, null,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
    )?.use { cursor ->
        val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
        val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numberIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val seenIds = HashSet<Long>()
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idIdx)
            if (seenIds.add(id)) {
                entries.add(
                    ContactEntry(
                        id = id,
                        name = cursor.getString(nameIdx) ?: "Unknown",
                        number = cursor.getString(numberIdx)
                    )
                )
            }
        }
    }
    return entries
}

private fun loadCallLogFromProvider(context: Context): List<CallLogEntry> {
    if (!hasCallLogPermission(context)) return emptyList()
    val entries = mutableListOf<CallLogEntry>()
    context.contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION),
        null, null,
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
                    number = cursor.getString(numberIdx) ?: "",
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

data class GroupedCallLogEntry(
    val number: String,
    val name: String?,
    val type: Int,
    val date: Long,
    val count: Int
)

private fun groupConsecutive(entries: List<CallLogEntry>): List<GroupedCallLogEntry> {
    val result = mutableListOf<GroupedCallLogEntry>()
    for (entry in entries) {
        val last = result.lastOrNull()
        if (last != null && last.number == entry.number) {
            result[result.size - 1] = last.copy(count = last.count + 1)
        } else {
            result.add(GroupedCallLogEntry(entry.number, entry.name, entry.type, entry.date, 1))
        }
    }
    return result
}

object DataCache {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observersRegistered = false
    private var contactsLoadStarted = false
    private var callLogLoadStarted = false

    private val _contacts = MutableStateFlow<List<ContactEntry>?>(null)
    val contacts: StateFlow<List<ContactEntry>?> = _contacts

    private val _callLog = MutableStateFlow<List<CallLogEntry>?>(null)
    val callLog: StateFlow<List<CallLogEntry>?> = _callLog
    
    private val _groupedCallLog = MutableStateFlow<List<GroupedCallLogEntry>?>(null)
    val groupedCallLog: StateFlow<List<GroupedCallLogEntry>?> = _groupedCallLog

    fun ensureLoaded(context: Context) {
        ensureObservers(context)
        val appContext = context.applicationContext
        if (!contactsLoadStarted) {
            contactsLoadStarted = true
            scope.launch { _contacts.value = loadContactsFromProvider(appContext) }
        }
        if (!callLogLoadStarted) {
            callLogLoadStarted = true
            scope.launch { refreshCallLog(appContext) }
        }
    }

    private suspend fun refreshCallLog(context: Context) {
        val entries = loadCallLogFromProvider(context)
        _callLog.value = entries
        _groupedCallLog.value = groupConsecutive(entries)
    }
    
    private fun ensureObservers(context: Context) {
        if (observersRegistered) return
        observersRegistered = true
        val appContext = context.applicationContext
        val handler = Handler(Looper.getMainLooper())
        appContext.contentResolver.registerContentObserver(
            ContactsContract.Contacts.CONTENT_URI, true,
            object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    scope.launch { _contacts.value = loadContactsFromProvider(appContext) }
                }
            }
        )
        appContext.contentResolver.registerContentObserver(
            CallLog.Calls.CONTENT_URI, true,
            object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    scope.launch { refreshCallLog(appContext) }
                }
            }
        )
    }
}

// end