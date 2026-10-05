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
private const val KEY_DIALPAD_SOUND = "dialpad_sound"
private const val KEY_REDIAL_AUTO = "redial_automatically"
private const val KEY_MISSED_CALL_REMINDER = "missed_call_reminder"
private const val KEY_VIBRATE_ON_ANSWER = "vibrate_on_answer"
private const val KEY_CALL_WAITING_NOTIFICATION = "call_waiting_notification"
private const val KEY_QUICK_RESPONSES = "quick_responses_enabled"
private const val KEY_QUICK_RESPONSE_1 = "quick_response_1"
private const val KEY_QUICK_RESPONSE_2 = "quick_response_2"
private const val KEY_QUICK_RESPONSE_3 = "quick_response_3"
private const val KEY_QUICK_RESPONSE_4 = "quick_response_4"
private const val KEY_DTMF_TONE_LENGTH = "dtmf_tone_length"
private const val KEY_FLIP_SILENCE = "flip_to_silence"
private const val KEY_QUIET_LIFT = "quiet_ringer_lifted"
private const val KEY_INCREASING_RING = "increasing_ringtone"
private const val KEY_FLASH_RING = "flash_when_ringing"
private const val KEY_PROXIMITY = "proximity_sensor"
private const val KEY_MUTE_FIRST_RING = "mute_first_ring"
private const val KEY_FLAGGED = "flagged_numbers"
private const val KEY_CALL_NOTES = "call_notes"

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

    // Call-screen backgrounds: a pool of photos/videos - fet
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

    // Dialpad key-press tone -- on by default, toggle lives in Settings.
    fun dialpadSoundEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DIALPAD_SOUND, true)

    fun setDialpadSound(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_DIALPAD_SOUND, value).apply()
    }

    // Advanced settings additions below
    fun redialAutomatically(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REDIAL_AUTO, false)

    fun setRedialAutomatically(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_REDIAL_AUTO, value).apply()
    }

    fun missedCallReminder(context: Context): String =
        prefs(context).getString(KEY_MISSED_CALL_REMINDER, "No reminder") ?: "No reminder"

    fun setMissedCallReminder(context: Context, value: String) {
        prefs(context).edit().putString(KEY_MISSED_CALL_REMINDER, value).apply()
    }

    fun vibrateOnAnswer(context: Context): String =
        prefs(context).getString(KEY_VIBRATE_ON_ANSWER, "Normal") ?: "Normal"

    fun setVibrateOnAnswer(context: Context, value: String) {
        prefs(context).edit().putString(KEY_VIBRATE_ON_ANSWER, value).apply()
    }

    fun callWaitingNotification(context: Context): String =
        prefs(context).getString(KEY_CALL_WAITING_NOTIFICATION, "Play notification sound continuously")
            ?: "Play notification sound continuously"

    fun setCallWaitingNotification(context: Context, value: String) {
        prefs(context).edit().putString(KEY_CALL_WAITING_NOTIFICATION, value).apply()
    }

    fun quickResponsesEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_QUICK_RESPONSES, true)

    fun setQuickResponsesEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_QUICK_RESPONSES, value).apply()
    }

    // Stock default four -- also what "Restore defaults" resets to.
    private val QUICK_RESPONSE_DEFAULTS = listOf(
        "Can't talk now. Call me later?",
        "I'll call you right back.",
        "I'll call you later.",
        "Can't talk now. What's up?"
    )
    private val QUICK_RESPONSE_KEYS = listOf(KEY_QUICK_RESPONSE_1, KEY_QUICK_RESPONSE_2, KEY_QUICK_RESPONSE_3, KEY_QUICK_RESPONSE_4)

    fun quickResponses(context: Context): List<String> =
        QUICK_RESPONSE_KEYS.mapIndexed { i, key -> prefs(context).getString(key, QUICK_RESPONSE_DEFAULTS[i]) ?: QUICK_RESPONSE_DEFAULTS[i] }

    fun setQuickResponse(context: Context, index: Int, value: String) {
        prefs(context).edit().putString(QUICK_RESPONSE_KEYS[index], value).apply()
    }

    fun restoreQuickResponseDefaults(context: Context) {
        val editor = prefs(context).edit()
        QUICK_RESPONSE_KEYS.forEachIndexed { i, key -> editor.putString(key, QUICK_RESPONSE_DEFAULTS[i]) }
        editor.apply()
    }

    // Controls the DTMF tone duration in RecentsScreen's dialpad
    fun dtmfToneLength(context: Context): String =
        prefs(context).getString(KEY_DTMF_TONE_LENGTH, "Normal") ?: "Normal"

    fun setDtmfToneLength(context: Context, value: String) {
        prefs(context).edit().putString(KEY_DTMF_TONE_LENGTH, value).apply()
    }

    fun flipToSilence(context: Context): Boolean = prefs(context).getBoolean(KEY_FLIP_SILENCE, false)
    fun setFlipToSilence(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_FLIP_SILENCE, value).apply()

    fun quietRingerWhenLifted(context: Context): Boolean = prefs(context).getBoolean(KEY_QUIET_LIFT, false)
    fun setQuietRingerWhenLifted(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_QUIET_LIFT, value).apply()

    fun increasingRingtone(context: Context): Boolean = prefs(context).getBoolean(KEY_INCREASING_RING, false)
    fun setIncreasingRingtone(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_INCREASING_RING, value).apply()

    fun flashWhenRinging(context: Context): Boolean = prefs(context).getBoolean(KEY_FLASH_RING, false)
    fun setFlashWhenRinging(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_FLASH_RING, value).apply()

    fun proximitySensor(context: Context): Boolean = prefs(context).getBoolean(KEY_PROXIMITY, true)
    fun setProximitySensor(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_PROXIMITY, value).apply()

    fun muteFirstRing(context: Context): Boolean = prefs(context).getBoolean(KEY_MUTE_FIRST_RING, false)
    fun setMuteFirstRing(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_MUTE_FIRST_RING, value).apply()

    fun flaggedNumbers(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_FLAGGED, emptySet()) ?: emptySet()

    fun toggleFlag(context: Context, number: String): Boolean {
        val updated = flaggedNumbers(context).toMutableSet()
        val nowFlagged = updated.add(number)
        if (!nowFlagged) updated.remove(number)
        prefs(context).edit().putStringSet(KEY_FLAGGED, updated).apply()
        return nowFlagged
    }

    // Notes taken during calls: one record per note, newest last - needs improvements.
    fun callNotes(context: Context, number: String): List<Pair<Long, String>> {
        val raw = prefs(context).getString(KEY_CALL_NOTES, null) ?: return emptyList()
        return raw.split(ITEM_SEP).mapNotNull { entry ->
            val parts = entry.split(FIELD_SEP)
            if (parts.size != 3 || parts[0] != number) return@mapNotNull null
            val time = parts[1].toLongOrNull() ?: return@mapNotNull null
            time to parts[2]
        }
    }

    fun addCallNote(context: Context, number: String, text: String) {
        val clean = text.trim().replace(FIELD_SEP, " ").replace(ITEM_SEP, " ")
        if (clean.isEmpty()) return
        val existing = prefs(context).getString(KEY_CALL_NOTES, null)
        val record = listOf(number, System.currentTimeMillis().toString(), clean).joinToString(FIELD_SEP)
        val merged = if (existing.isNullOrEmpty()) record else existing + ITEM_SEP + record
        prefs(context).edit().putString(KEY_CALL_NOTES, merged).apply()
    }
}


// BANKAI 😎

// Strange 🥀
