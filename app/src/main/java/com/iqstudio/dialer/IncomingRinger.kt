//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later).
// *
//**************************************************

// Ringer service for incoming calls
package com.iqstudio.dialer

import android.app.NotificationManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.ContactsContract
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import kotlin.math.sqrt

object IncomingRinger {
    private const val TAG = "IncomingRinger"
    private const val RAMP_MS = 10000L
    private const val RAMP_STEP_MS = 400L
    private const val MIN_VOLUME = 0.12f
    private const val QUIET_FACTOR = 0.3f
    private const val FIRST_RING_MS = 3000L
    private const val FLASH_MS = 450L
    private const val LIFT_COS = 0.866f
    private const val FLIP_Z = -7f
    private const val FLAT_Z = 8.5f

    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var sensorManager: SensorManager? = null
    private var sensorListener: SensorEventListener? = null
    private var cameraManager: CameraManager? = null
    private var torchId: String? = null
    private var torchOn = false
    private var rampStart = 0L
    private var ramping = false
    private var quiet = false

    private fun isUnknown(context: Context, number: String?): Boolean =
        number == null || ContactCache.nameFor(context, number) == null

    // True when the system ringer must stay quiet because this object rings instead.
    // Only in plain NORMAL ringer mode with no DND, so it can never bypass either.
    
    fun shouldArm(context: Context, number: String?): Boolean {
        val wantsRamp = AppPrefs.increasingRingtone(context)
        val wantsQuiet = AppPrefs.quietRingerWhenLifted(context)
        val wantsMute = AppPrefs.muteFirstRing(context) && isUnknown(context, number)
        if (!wantsRamp && !wantsQuiet && !wantsMute) return false
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return false
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm != null && nm.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL) return false
        return true
    }

    // called on every call-state refresh while ringing.
    fun start(context: Context, number: String?) {
        if (active) return
        active = true
        val ctx = context.applicationContext
        try {
            if (CallStateHolder.customRingerArmed) startSound(ctx, number)
            if (AppPrefs.flashWhenRinging(ctx)) startFlash(ctx)
            if (AppPrefs.flipToSilence(ctx) || (AppPrefs.quietRingerWhenLifted(ctx) && CallStateHolder.customRingerArmed)) {
                startSensors(ctx)
            }
        } catch (e: Exception) {
            Log.e(TAG, "start failed", e)
        }
    }

    // Ringing ended (answered, declined, missed, removed).
    fun stop() {
        if (!active) return
        releaseAll()
        active = false
    }

    // User silenced it (volume key / flip) -- stays "active" so the same ring can't restart.
    fun silence() {
        if (!active) return
        releaseAll()
    }

    private fun startSound(ctx: Context, number: String?) {
        startVibration(ctx)
        val delay = if (AppPrefs.muteFirstRing(ctx) && isUnknown(ctx, number)) FIRST_RING_MS else 0L
        handler.postDelayed({ playRingtone(ctx, number) }, delay)
    }

    private fun playRingtone(ctx: Context, number: String?) {
        if (!active) return
        try {
            val uri = contactRingtone(ctx, number)
                ?: RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val r = RingtoneManager.getRingtone(ctx, uri) ?: return
            r.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            r.isLooping = true
            ramping = AppPrefs.increasingRingtone(ctx)
            rampStart = SystemClock.elapsedRealtime()
            r.volume = currentVolume()
            r.play()
            ringtone = r
            if (ramping) handler.postDelayed(rampTick, RAMP_STEP_MS)
        } catch (e: Exception) {
            Log.e(TAG, "playRingtone failed", e)
        }
    }

    private fun contactRingtone(ctx: Context, number: String?): Uri? {
        if (number == null || !hasContactsPermission(ctx)) return null
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.CUSTOM_RINGTONE), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { Uri.parse(it) } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun currentVolume(): Float {
        val base = if (ramping) {
            val t = ((SystemClock.elapsedRealtime() - rampStart).toFloat() / RAMP_MS).coerceIn(0f, 1f)
            MIN_VOLUME + (1f - MIN_VOLUME) * t
        } else {
            1f
        }
        return if (quiet) base * QUIET_FACTOR else base
    }

    private val rampTick = object : Runnable {
        override fun run() {
            val r = ringtone ?: return
            r.volume = currentVolume()
            if (SystemClock.elapsedRealtime() - rampStart < RAMP_MS) {
                handler.postDelayed(this, RAMP_STEP_MS)
            } else {
                ramping = false
                r.volume = currentVolume()
            }
        }
    }

    // Same rule the system ringer follows: only when "vibrate when ringing" is on.
    private fun startVibration(ctx: Context) {
        try {
            if (Settings.System.getInt(ctx.contentResolver, "vibrate_when_ringing", 0) != 1) return
            val v = ctx.getSystemService(Vibrator::class.java) ?: return
            vibrator = v
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 900, 900), 0))
        } catch (e: Exception) {
            Log.e(TAG, "startVibration failed", e)
        }
    }

    private fun startFlash(ctx: Context) {
        try {
            val cm = ctx.getSystemService(CameraManager::class.java) ?: return
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            cameraManager = cm
            torchId = id
            handler.post(flashTick)
        } catch (e: Exception) {
            Log.e(TAG, "startFlash failed", e)
        }
    }

    private val flashTick = object : Runnable {
        override fun run() {
            val cm = cameraManager ?: return
            val id = torchId ?: return
            try {
                torchOn = !torchOn
                cm.setTorchMode(id, torchOn)
            } catch (e: Exception) {
                torchOn = false
                return
            }
            handler.postDelayed(this, FLASH_MS)
        }
    }

    private fun stopFlash() {
        handler.removeCallbacks(flashTick)
        try {
            val id = torchId
            if (torchOn && id != null) cameraManager?.setTorchMode(id, false)
        } catch (e: Exception) {
            Log.e(TAG, "stopFlash failed", e)
        }
        torchOn = false
        cameraManager = null
        torchId = null
    }

    private fun startSensors(ctx: Context) {
        val sm = ctx.getSystemService(SensorManager::class.java) ?: return
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        val wantsFlip = AppPrefs.flipToSilence(ctx)
        val wantsLift = AppPrefs.quietRingerWhenLifted(ctx) && CallStateHolder.customRingerArmed
        var baseline: FloatArray? = null
        var startedFaceUp = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val base = baseline
                if (base == null) {
                    baseline = floatArrayOf(x, y, z)
                    startedFaceUp = z > FLAT_Z
                    return
                }
                if (wantsFlip && startedFaceUp && z < FLIP_Z) {
                    flipSilence(ctx)
                    return
                }
                if (wantsLift && !quiet) {
                    val dot = x * base[0] + y * base[1] + z * base[2]
                    val mag = sqrt(x * x + y * y + z * z) * sqrt(base[0] * base[0] + base[1] * base[1] + base[2] * base[2])
                    if (mag > 0f && dot / mag < LIFT_COS) {
                        quiet = true
                        ringtone?.volume = currentVolume()
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager = sm
        sensorListener = listener
    }

    private fun flipSilence(ctx: Context) {
        try {
            ctx.getSystemService(TelecomManager::class.java)?.silenceRinger()
        } catch (e: Exception) {
            Log.e(TAG, "silenceRinger failed", e)
        }
        silence()
    }

    private fun releaseAll() {
        handler.removeCallbacksAndMessages(null)
        try {
            ringtone?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "ringtone stop failed", e)
        }
        ringtone = null
        ramping = false
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "vibrator cancel failed", e)
        }
        vibrator = null
        stopFlash()
        sensorListener?.let { sensorManager?.unregisterListener(it) }
        sensorListener = null
        sensorManager = null
        quiet = false
    }
}

//!