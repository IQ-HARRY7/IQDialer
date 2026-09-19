//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later). 
// * 
//**************************************************

// In call service for UX. 

package com.iqstudio.dialer

import android.app.KeyguardManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.PowerManager
import android.provider.Settings
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

// proximity & in call <>.
class TurboInCallService : InCallService() {

    companion object {
        var instance: TurboInCallService? = null
            private set
        private const val TAG = "TurboInCallService"
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            presentCall(call, state)
        }
    }

    private var proximityWakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        val powerManager = getSystemService(POWER_SERVICE) as? PowerManager
        if (powerManager?.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) == true) {
            proximityWakeLock = powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "$TAG:proximity"
            )
        }
    }

    override fun onDestroy() {
        releaseProximity()
        instance = null
        super.onDestroy()
    }

    private fun acquireProximity() {
        try {
            val lock = proximityWakeLock ?: return
            if (!lock.isHeld) lock.acquire()
        } catch (e: Exception) {
            Log.e(TAG, "acquireProximity failed", e)
        }
    }

    private fun releaseProximity() {
        try {
            val lock = proximityWakeLock ?: return
            if (lock.isHeld) lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
        } catch (e: Exception) {
            Log.e(TAG, "releaseProximity failed", e)
        }
    }

    override fun onCallAdded(call: Call) {
        try {
            CallStateHolder.setCall(call)

            // TurboCallScreeningService already rolled one (for incoming calls) so the
            // ringer-silence decision and the on-screen background agree; outgoing calls
            // never hit screening, so fall back to rolling one here.
            val chosen = CallStateHolder.takePendingBackground() ?: AppPrefs.randomBackground(this)
            CallStateHolder.setActiveBackground(chosen)

            call.registerCallback(callCallback)
            presentCall(call, call.state)
        } catch (e: Exception) {
            Log.e(TAG, "onCallAdded failed", e)
        }
    }

    override fun onCallRemoved(call: Call) {
        try {
            call.unregisterCallback(callCallback)
            releaseProximity()
            endForegroundPresentation()
            stopService(Intent(this, CallBubbleService::class.java))
            CallStateHolder.setCall(null)
        } catch (e: Exception) {
            Log.e(TAG, "onCallRemoved failed", e)
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        try {
            CallStateHolder.setAudioState(audioState)
        } catch (e: Exception) {
            Log.e(TAG, "onCallAudioStateChanged failed", e)
        }
    }

    private fun presentCall(call: Call, state: Int) {
        try {
            val number = call.details?.handle?.schemeSpecificPart ?: "Unknown"

            if (state == Call.STATE_ACTIVE) {
                acquireProximity()
            } else {
                releaseProximity()
            }

            if (state == Call.STATE_RINGING) {
                // Locked/screen-off is the one case the full-screen intent
                // actually matters for -- unlocked with another app in front,
                // Android only ever uses it as a tap target anyway, and the
                // bubble is already the "notice me" affordance, so a second
                // loud banner on top of it is just noise.
                val locked = (getSystemService(KEYGUARD_SERVICE) as? KeyguardManager)?.isKeyguardLocked == true
                val bubbleWillShow = Settings.canDrawOverlays(this)
                val quiet = !locked && bubbleWillShow
                startForeground(
                    INCOMING_CALL_NOTIFICATION_ID,
                    buildIncomingCallNotification(number, quiet),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                )
                if (!InCallActivity.isVisible) {
                    ContextCompat.startForegroundService(this, Intent(this, CallBubbleService::class.java))
                }
                return
            }

            startForeground(
                INCOMING_CALL_NOTIFICATION_ID,
                buildOngoingCallNotification(number, state),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            )

            if (InCallActivity.isVisible) {
                // Already on screen -- its own Crossfade handles ringing-to-active.
                return
            }

            if (state == Call.STATE_ACTIVE || state == Call.STATE_DIALING || state == Call.STATE_CONNECTING) {
                ContextCompat.startForegroundService(this, Intent(this, CallBubbleService::class.java))
            }
        } catch (e: Exception) {
            Log.e(TAG, "presentCall failed, call continues without custom UI", e)
        }
    }

    private fun buildIncomingCallNotification(number: String, quiet: Boolean): Notification {
        val displayName = ContactCache.nameFor(this, number) ?: number
        val builder = NotificationCompat.Builder(this, if (quiet) BUBBLE_CHANNEL_ID else INCOMING_CALL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(displayName)
            .setContentText("Incoming call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)

        if (quiet) {
            builder.setPriority(NotificationCompat.PRIORITY_LOW)
        } else {
            val fullScreenIntent = PendingIntent.getActivity(
                this, 100,
                Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setFullScreenIntent(fullScreenIntent, true)
            builder.setPriority(NotificationCompat.PRIORITY_HIGH)
        }

        return builder.build()
    }

    private fun buildOngoingCallNotification(number: String, state: Int): Notification {
        val displayName = ContactCache.nameFor(this, number) ?: number
        val contentIntent = PendingIntent.getActivity(
            this, 103,
            Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Always the quiet channel here -- once dialing/connecting/active there's
        // no "wake the phone" need, this is just a way back into the call.
        return NotificationCompat.Builder(this, BUBBLE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(displayName)
            .setContentText(callStateLabel(state))
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setColorized(true)
            .setColor(0xFF4ADE80.toInt())
            .setOngoing(true)
            .build()
    }

    private fun endForegroundPresentation() {
        try {
            NotificationManagerCompat.from(this).cancel(INCOMING_CALL_NOTIFICATION_ID)
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.e(TAG, "endForegroundPresentation failed", e)
        }
    }
}

// need to be fixed many things ig. 
