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
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.VideoProfile
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
        private const val REDIAL_MAX = 3
        private const val REDIAL_DELAY_MS = 4000L
        private var redialAttempts = 0
        private var redialNumber: String? = null
        private val redialHandler = Handler(Looper.getMainLooper())
    }

    private val tracked = ArrayList<Call>()
    private val lastStates = HashMap<Call, Int>()
    private val connectedAt = HashMap<Call, Long>()

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            try {
                handleStateChange(call, state)
                refresh()
            } catch (e: Exception) {
                Log.e(TAG, "onStateChanged failed", e)
            }
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) = refresh()
        override fun onChildrenChanged(call: Call, children: MutableList<Call>) = refresh()
        override fun onParentChanged(call: Call, parent: Call?) = refresh()
        override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>) = refresh()
    }

    private fun handleStateChange(call: Call, state: Int) {
        val previous = lastStates[call] ?: Call.STATE_NEW
        lastStates[call] = state
        val outgoing = call.details.callDirection == Call.Details.DIRECTION_OUTGOING
        if (state == Call.STATE_ACTIVE) {
            if (connectedAt[call] == null) connectedAt[call] = SystemClock.elapsedRealtime()
            redialAttempts = 0
            val wasDialing = previous == Call.STATE_DIALING || previous == Call.STATE_CONNECTING
            if (outgoing && wasDialing) vibrateOnAnswer()
        } else if (state == Call.STATE_DISCONNECTED) {
            CallRecordingManager.stopFor(call)
            if (outgoing) maybeRedial(call)
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
        IncomingRinger.stop()
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
            val firstCall = tracked.none { it.parent == null }
            tracked.add(call)
            lastStates[call] = call.state
            if (call.state == Call.STATE_ACTIVE) connectedAt[call] = SystemClock.elapsedRealtime()

            // never hit screening, so fall back to rolling one here.
            if (firstCall && call.parent == null) {
                val chosen = CallStateHolder.takePendingBackground() ?: AppPrefs.randomBackground(this)
                CallStateHolder.setActiveBackground(chosen)
            }

            call.registerCallback(callCallback)
            refresh()
        } catch (e: Exception) {
            Log.e(TAG, "onCallAdded failed", e)
        }
    }

    override fun onCallRemoved(call: Call) {
        try {
            call.unregisterCallback(callCallback)
            CallRecordingManager.stopFor(call)
            tracked.remove(call)
            lastStates.remove(call)
            connectedAt.remove(call)
            refresh()
        } catch (e: Exception) {
            Log.e(TAG, "onCallRemoved failed", e)
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        try {
            CallStateHolder.setAudioState(audioState)
            updateProximity(CallStateHolder.calls.value)
        } catch (e: Exception) {
            Log.e(TAG, "onCallAudioStateChanged failed", e)
        }
    }

    override fun onSilenceRinger() {
        IncomingRinger.silence()
    }

    private fun infoOf(call: Call): CallInfo {
        val d = call.details
        return CallInfo(
            call = call,
            state = call.state,
            number = d.handle?.schemeSpecificPart ?: "Unknown",
            isConference = d.hasProperty(Call.Details.PROPERTY_CONFERENCE),
            children = call.children.map { infoOf(it) },
            canMerge = d.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) || call.conferenceableCalls.isNotEmpty(),
            canSwapConference = d.can(Call.Details.CAPABILITY_SWAP_CONFERENCE),
            canSplit = d.can(Call.Details.CAPABILITY_SEPARATE_FROM_CONFERENCE),
            canDisconnectFromConference = d.can(Call.Details.CAPABILITY_DISCONNECT_FROM_CONFERENCE),
            videoState = d.videoState,
            connectedAtElapsed = connectedAt[call]
        )
    }

    // Single place that republishes everything: call list, ringer, proximity, notification, bubble.
    private fun refresh() {
        try {
            val list = tracked.filter { it.parent == null }.map { infoOf(it) }
            val live = list.filter { it.state != Call.STATE_DISCONNECTED }
            CallStateHolder.setCalls(list)

            val ringing = live.firstOrNull { it.state == Call.STATE_RINGING }
            if (ringing != null) {
                IncomingRinger.start(this, ringing.number)
            } else {
                IncomingRinger.stop()
                CallStateHolder.customRingerArmed = false
            }

            updateProximity(list)

            if (tracked.isEmpty()) {
                releaseProximity()
                endForegroundPresentation()
                stopService(Intent(this, CallBubbleService::class.java))
                CallStateHolder.setCalls(emptyList())
                return
            }
            present(ringing, pickPrimary(live))
        } catch (e: Exception) {
            Log.e(TAG, "refresh failed", e)
        }
    }

    private fun updateProximity(list: List<CallInfo>) {
        val primary = pickPrimary(list)
        val onSpeaker = CallStateHolder.audioState.value?.route == CallAudioState.ROUTE_SPEAKER
        val wanted = AppPrefs.proximitySensor(this) && !onSpeaker && primary != null &&
            (primary.state == Call.STATE_ACTIVE || primary.state == Call.STATE_DIALING) &&
            !VideoProfile.isVideo(primary.videoState)
        if (wanted) acquireProximity() else releaseProximity()
    }

    @Suppress("DEPRECATION")
    private fun vibrateOnAnswer() {
        val (ms, amplitude) = when (AppPrefs.vibrateOnAnswer(this)) {
            "Normal" -> 200L to VibrationEffect.DEFAULT_AMPLITUDE
            "Light" -> 60L to 80
            else -> return
        }
        try {
            (getSystemService(VIBRATOR_SERVICE) as? Vibrator)?.vibrate(VibrationEffect.createOneShot(ms, amplitude))
        } catch (e: Exception) {
            Log.e(TAG, "vibrateOnAnswer failed", e)
        }
    }

    private fun maybeRedial(call: Call) {
        val number = call.details.handle?.schemeSpecificPart ?: return
        if (call.details.disconnectCause?.code != DisconnectCause.BUSY) return
        if (!AppPrefs.redialAutomatically(this)) return
        if (number != redialNumber) {
            redialNumber = number
            redialAttempts = 0
        }
        if (redialAttempts >= REDIAL_MAX) {
            redialAttempts = 0
            return
        }
        redialAttempts++
        val appContext = applicationContext
        redialHandler.postDelayed({ placeCall(appContext, number) }, REDIAL_DELAY_MS)
    }

    private fun present(ringing: CallInfo?, primary: CallInfo?) {
        try {
            if (ringing != null) {
            
                // Locked/screen-off is the one case the full-screen intent
                
                val locked = (getSystemService(KEYGUARD_SERVICE) as? KeyguardManager)?.isKeyguardLocked == true
                val bubbleWillShow = Settings.canDrawOverlays(this)
                val quiet = !locked && bubbleWillShow
                startForeground(
                    INCOMING_CALL_NOTIFICATION_ID,
                    buildIncomingCallNotification(ringing.number, quiet),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                )
                if (!InCallActivity.isVisible) {
                    ContextCompat.startForegroundService(this, Intent(this, CallBubbleService::class.java))
                }
                return
            }

            val shown = primary ?: return
            startForeground(
                INCOMING_CALL_NOTIFICATION_ID,
                buildOngoingCallNotification(shown.number, shown.state),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            )

            if (InCallActivity.isVisible) {
                // Already on screen
                return
            }

            if (shown.state == Call.STATE_ACTIVE || shown.state == Call.STATE_DIALING || shown.state == Call.STATE_CONNECTING) {
                ContextCompat.startForegroundService(this, Intent(this, CallBubbleService::class.java))
            }
        } catch (e: Exception) {
            Log.e(TAG, "present failed, call continues without custom UI", e)
        }
    }

    private fun buildIncomingCallNotification(number: String, quiet: Boolean): Notification {
        val displayName = ContactCache.nameFor(this, number) ?: number
        val answerIntent = PendingIntent.getBroadcast(
            this, 101,
            Intent(this, CallActionReceiver::class.java).setAction(ACTION_ANSWER_CALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val declineIntent = PendingIntent.getBroadcast(
            this, 102,
            Intent(this, CallActionReceiver::class.java).setAction(ACTION_DECLINE_CALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, if (quiet) BUBBLE_CHANNEL_ID else INCOMING_CALL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(displayName)
            .setContentText("Incoming call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declineIntent)
            .addAction(android.R.drawable.sym_call_incoming, "Answer", answerIntent)

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
// ~