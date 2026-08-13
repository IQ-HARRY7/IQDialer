//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later). 
// * 
//**************************************************

// this is the preference file, uses cache & system block. many improvements needed @IQ_HARRY_07
package com.iqstudio.dialer

import android.content.Context
import android.net.Uri

private const val PREFS_NAME = "iq_dialer_prefs"
private const val KEY_24_HOUR = "use_24_hour"
private const val KEY_BACKGROUNDS = "call_backgrounds"
private const val KEY_RINGTONE_URI = "manual_ringtone_uri"

// NoEscape logic. 
private const val FIELD_SEP = "\u001F"
private const val ITEM_SEP = "\u001E"

// Video background preference - working, but need improvements. 
data class BackgroundItem(
    val uri: Uri,
    val isVideo: Boolean,
    val hasSound: Boolean = false,
    val muted: Boolean = false,
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
)

object AppPrefs {
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun is24Hour(context: Context): Boolean =
        prefs(context).getBoolean(KEY_24_HOUR, android.text.format.DateFormat.is24HourFormat(context))

    fun set24Hour(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_24_HOUR, value).apply()
    }

    // Used everywhere a call time gets formatted, so the whole app agrees.
    fun timePattern(context: Context): String = if (is24Hour(context)) "HH:mm" else "h:mm a"

    // Call-screen backgrounds: a pool of photos/videos, one picked at random - it's random & this is how it's planned. you can modify for selecting etc. 
    fun backgrounds(context: Context): List<BackgroundItem> {
        val raw = prefs(context).getString(KEY_BACKGROUNDS, null)
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(ITEM_SEP).mapNotNull(::decodeItem)
    }

    fun setBackgrounds(context: Context, items: List<BackgroundItem>) {
        val raw = items.joinToString(ITEM_SEP, transform = ::encodeItem)
        prefs(context).edit().putString(KEY_BACKGROUNDS, raw).apply()
    }

    fun addBackground(context: Context, item: BackgroundItem) {
        setBackgrounds(context, backgrounds(context) + item)
    }

    fun removeBackground(context: Context, uri: Uri) {
        setBackgrounds(context, backgrounds(context).filterNot { it.uri == uri })
    }

    fun updateBackground(context: Context, updated: BackgroundItem) {
        setBackgrounds(context, backgrounds(context).map { if (it.uri == updated.uri) updated else it })
    }

    fun randomBackground(context: Context): BackgroundItem? = backgrounds(context).randomOrNull()

    private fun encodeItem(item: BackgroundItem): String = listOf(
        item.uri.toString(), item.isVideo, item.hasSound, item.muted, item.scale, item.offsetX, item.offsetY
    ).joinToString(FIELD_SEP)

    private fun decodeItem(entry: String): BackgroundItem? {
        val parts = entry.split(FIELD_SEP)
        if (parts.size != 7) return null
        return try {
            BackgroundItem(
                uri = Uri.parse(parts[0]),
                isVideo = parts[1].toBoolean(),
                hasSound = parts[2].toBoolean(),
                muted = parts[3].toBoolean(),
                scale = parts[4].toFloat(),
                offsetX = parts[5].toFloat(),
                offsetY = parts[6].toFloat()
            )
        } catch (e: Exception) {
            null
        }
    }

// uri & fun. 

    fun ringtoneUri(context: Context): Uri? =
        prefs(context).getString(KEY_RINGTONE_URI, null)?.let { Uri.parse(it) }

    fun setRingtoneUri(context: Context, uri: Uri?) {
        prefs(context).edit().putString(KEY_RINGTONE_URI, uri?.toString()).apply()
    }
}


// BANKAI 😎