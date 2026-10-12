//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later).
// *
//**************************************************

package com.iqstudio.dialer

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Writes each saved call note to Documents/IQDialer/Notes as a plain text file.
object CallNoteExporter {
    private const val TAG = "CallNoteExporter"

    fun export(context: Context, number: String, note: String): Boolean {
        if (note.isBlank()) return false
        return try {
            val now = Date()
            val name = ContactCache.nameFor(context, number)
            val body = buildString {
                append("IQ Dialer call note\n")
                append("Contact: ").append(name ?: number).append('\n')
                append("Number: ").append(number).append('\n')
                append("Saved: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(now)).append("\n\n")
                append(note.trim()).append('\n')
            }
            val safeNumber = number.replace(Regex("[^0-9A-Za-z+]"), "")
            val fileName = "note_" + safeNumber + "_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now) + ".txt"

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/IQDialer/Notes")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return false
            resolver.openOutputStream(uri)?.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not export note", e)
            false
        }
    }
}
