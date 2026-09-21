//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later).
// *
//**************************************************

// contact name cache -- lookupContactName() is a real ContentResolver query,
// and TurboInCallService calls it on the main thread while a call is
// ringing. cached in memory per process, cleared automatically whenever
// contacts change so a renamed/deleted contact can't show a stale name.
package com.iqstudio.dialer

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import java.util.concurrent.ConcurrentHashMap

object ContactCache {
    // ConcurrentHashMap can't hold null values, and no real contact has an
    // empty display name, so "" is the sentinel for "looked up, no contact".
    private val cache = ConcurrentHashMap<String, String>()
    private var observerRegistered = false

    fun nameFor(context: Context, number: String): String? {
        ensureObserver(context)
        cache[number]?.let { return it.ifEmpty { null } }
        val name = lookupContactName(context, number)
        cache[number] = name ?: ""
        return name
    }

    private fun ensureObserver(context: Context) {
        if (observerRegistered) return
        observerRegistered = true
        try {
            context.applicationContext.contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                true,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        cache.clear()
                    }
                }
            )
        } catch (e: Exception) {
        }
    }
}
