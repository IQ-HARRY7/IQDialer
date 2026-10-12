#!/data/data/com.termux/files/usr/bin/bash
# Script 67: fix bubble service start/stop crash, silence system ringer for video sound, export call notes
set -e

cat > ~/IQDialer/app/src/main/java/com/iqstudio/dialer/CallBubbleService.kt << 'EOF'
//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later). 
// * 
//**************************************************

// This whole file's purpose is to create a floating bubble, to prevent from full screen overwrites. (eg- MIUI has this by default)

package com.iqstudio.dialer

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import android.util.Log
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.abs

const val BUBBLE_CHANNEL_ID = "call_bubble"
private const val BUBBLE_FOREGROUND_NOTIFICATION_ID = 4002
private const val TAG = "CallBubbleService"

// ✨✨✨✨ i need your attention: keep this simple, because adding new <event> or action can impact the bubble's smoothness. bubble must be smooth. 

// Call back register - & so on.
//
// drag - to drag the bubble across screen. (it's a bubble bruh 🤦)
//
// Answe button on bubble.
class CallBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var rightButton: View? = null
    private var registeredCall: Call? = null
    private var speakerSwitchPending = false
    private var ringtonePlayer: ExoPlayer? = null
    private var bubbleHidden = false

    // Start, hide and stop requests are routed through the service itself, so a stop can
    // never arrive before startForeground() has run. All calls happen on the main thread.
    companion object {
        private var instance: CallBubbleService? = null
        private var startPending = false
        private var stopWhenReady = false
        private var hideWhenReady = false

        fun show(context: Context) {
            val service = instance
            if (service != null) {
                service.revealBubble()
                return
            }
            stopWhenReady = false
            hideWhenReady = false
            if (startPending) return
            startPending = true
            try {
                val appContext = context.applicationContext
                ContextCompat.startForegroundService(appContext, Intent(appContext, CallBubbleService::class.java))
            } catch (e: Exception) {
                startPending = false
                Log.e(TAG, "Could not start the bubble service", e)
            }
        }

        fun hide() {
            val service = instance
            if (service != null) service.concealBubble() else if (startPending) hideWhenReady = true
        }

        fun stop() {
            val service = instance
            if (service != null) service.stopSelf() else if (startPending) stopWhenReady = true
        }
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            applyState(state)
            if (state == Call.STATE_ACTIVE && speakerSwitchPending) {
                speakerSwitchPending = false
                TurboInCallService.instance?.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            startForeground(BUBBLE_FOREGROUND_NOTIFICATION_ID, buildForegroundNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } catch (e: Exception) {
            Log.e(TAG, "onCreate failed, stopping bubble service", e)
            startPending = false
            stopSelf()
            return
        }
        instance = this
        startPending = false
        bubbleHidden = hideWhenReady || stopWhenReady
        val stopNow = stopWhenReady
        stopWhenReady = false
        hideWhenReady = false
        if (stopNow) {
            stopSelf()
        } else if (!bubbleHidden) {
            showBubble()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every startForegroundService() call has to be answered with startForeground().
        try {
            startForeground(BUBBLE_FOREGROUND_NOTIFICATION_ID, buildForegroundNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
        }
        if (bubbleView == null && !bubbleHidden) {
            showBubble()
        }
        return START_NOT_STICKY
    }

    private fun revealBubble() {
        bubbleHidden = false
        if (bubbleView == null) showBubble()
    }

    private fun concealBubble() {
        bubbleHidden = true
        removeBubbleView()
    }

    private fun removeBubbleView() {
        registeredCall?.unregisterCallback(callCallback)
        registeredCall = null
        stopRingtoneAudio()
        bubbleView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // view already gone -- fine
            }
        }
        bubbleView = null
    }

    private fun buildForegroundNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 200,
            Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, BUBBLE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("Call in progress")
            .setContentText("Tap to return to the call")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private fun circleButton(glyph: String, bgColorHex: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = glyph
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(bgColorHex))
            }
            setOnClickListener { onClick() }
        }
    }

    private fun showBubble() {
        if (bubbleView != null) return

        try {
            val call = CallStateHolder.activeCall.value
            val number = call?.details?.handle?.schemeSpecificPart ?: "Call"
            val displayName = ContactCache.nameFor(this, number) ?: number

            registeredCall = call
            call?.registerCallback(callCallback)

            val pill = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(28).toFloat()
                setColor(Color.parseColor("#1D2022"))
            }

            val nameView = TextView(this).apply {
                text = displayName
                setTextColor(Color.parseColor("#E0E3E5"))
                textSize = 13f
                maxLines = 1
            }

           // Right button of bubble. 
            val left = circleButton("\u2715", "#EF4444") {
                val current = CallStateHolder.activeCall.value
                if (current?.state == Call.STATE_RINGING) {
                    current.reject(false, null)
                } else {
                    current?.disconnect()
                }
                stopSelf()
            }

            // Right: Answer, only while ringing (applyState hides it otherwise).
            // Speaker switch is deferred to callCallback.onStateChanged.
            val right = circleButton("\u2713", "#4ADE80") {
                speakerSwitchPending = true
                CallStateHolder.activeCall.value?.answer(VideoProfile.STATE_AUDIO_ONLY)
            }
            rightButton = right

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                background = pill
                setPadding(dp(10), dp(8), dp(10), dp(8))
                gravity = Gravity.CENTER_VERTICAL
                addView(left, LinearLayout.LayoutParams(dp(40), dp(40)))
                val textParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dp(10)
                    marginEnd = dp(10)
                }
                addView(nameView, textParams)
                addView(right, LinearLayout.LayoutParams(dp(40), dp(40)))
                alpha = 0f
                scaleX = 0.8f
                scaleY = 0.8f
            }

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = dp(16)
                y = dp(80)
            }

            setupDrag(container, layoutParams)
            windowManager.addView(container, layoutParams)
            bubbleView = container

            container.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start()

            applyState(call?.state ?: Call.STATE_ACTIVE)
        } catch (e: Exception) {
            Log.e(TAG, "showBubble failed (overlay permission missing?), stopping", e)
            stopSelf()
        }
    }

    // Answer only makes sense before the call is picked up -- once active,
    // the right side just disappears instead of showing a dead action. may cause issues in some ROM - debug
    
    private fun applyState(state: Int) {
        rightButton?.visibility = if (state == Call.STATE_RINGING) View.VISIBLE else View.GONE
        if (state == Call.STATE_RINGING) {
            startRingtoneAudio()
        } else {
            stopRingtoneAudio()
        }
    }
// T
    private fun startRingtoneAudio() {
        if (ringtonePlayer != null || InCallActivity.isVisible) return
        val item = CallStateHolder.activeBackground.value ?: return
        if (!item.isVideo || !item.hasSound || item.muted) return
        try {
            ringtonePlayer = ExoPlayer.Builder(this).build().apply {
                setMediaItem(MediaItem.fromUri(item.uri))
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 1f
                prepare()
                playWhenReady = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "startRingtoneAudio failed", e)
            ringtonePlayer = null
        }
    }

    private fun stopRingtoneAudio() {
        try {
            ringtonePlayer?.release()
        } catch (e: Exception) {
        }
        ringtonePlayer = null
    }

    private fun setupDrag(view: View, layoutParams: WindowManager.LayoutParams) {
        val choreographer = Choreographer.getInstance()
        var initialX = 0
        var initialY = 0
        var touchDownX = 0f
        var touchDownY = 0f
        var pendingX = 0
        var pendingY = 0
        var frameScheduled = false

        val frameCallback = Choreographer.FrameCallback {
            frameScheduled = false
            layoutParams.x = pendingX
            layoutParams.y = pendingY
            try {
                windowManager.updateViewLayout(view, layoutParams)
            } catch (e: Exception) {
                // view may already be detached
            }
        }

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    touchDownX = event.rawX
                    touchDownY = event.rawY
                    view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
// Frame Rate & Touch. 
                    pendingX = initialX + (event.rawX - touchDownX).toInt()
                    pendingY = initialY + (event.rawY - touchDownY).toInt()
                    if (!frameScheduled) {
                        frameScheduled = true
                        choreographer.postFrameCallback(frameCallback)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    val moved = abs(event.rawX - touchDownX) + abs(event.rawY - touchDownY)
                    if (moved < dp(12)) {
                        openFullCallScreen()
                    } else {
                        snapToEdge(view, layoutParams)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEdge(view: View, layoutParams: WindowManager.LayoutParams) {
        val screenWidth = resources.displayMetrics.widthPixels
        val bubbleWidth = view.width.takeIf { it > 0 } ?: dp(140)
        val targetX = if (layoutParams.x + bubbleWidth / 2 < screenWidth / 2) dp(16) else screenWidth - bubbleWidth - dp(16)

        val startX = layoutParams.x
        val animator = android.animation.ValueAnimator.ofInt(startX, targetX)
        animator.duration = 220
        animator.interpolator = android.view.animation.DecelerateInterpolator()
        animator.addUpdateListener { anim ->
            layoutParams.x = anim.animatedValue as Int
            windowManager.updateViewLayout(view, layoutParams)
        }
        animator.start()
    }

    private fun openFullCallScreen() {
        val intent = Intent(this, InCallActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        stopSelf()
    }

    override fun onDestroy() {
        removeBubbleView()
        if (instance === this) instance = null
        super.onDestroy()
    }
}

// end bruh, what else do you expect? 😐
EOF

cat > ~/IQDialer/app/src/main/java/com/iqstudio/dialer/CallNoteExporter.kt << 'EOF'
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
EOF

cat > ~/IQDialer/app/src/main/java/com/iqstudio/dialer/InCallActivity.kt << 'EOF'
//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later). 
// * 
//**************************************************

package com.iqstudio.dialer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call as CallIconVector
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InCallActivity : ComponentActivity() {

    companion object {
        var isVisible = false
            private set
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppTheme.pushDark()
        setContent {
            IQDialerTheme {
                InCallScreen()
            }
        }
    }

    override fun onDestroy() {
        AppTheme.popDark()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        isVisible = true
        // We're the one on screen now -- the bubble (if it was up) shouldn't be.
        CallBubbleService.hide()
    }

    override fun onPause() {
        super.onPause()
        isVisible = false
        // leaving the bubble...
        val stillOngoing = CallStateHolder.activeCall.value != null
        if (stillOngoing && !isFinishing) {
            CallBubbleService.show(this)
        }
    }
}

// unemployed feature 😆.
private fun loadContactPhotoBitmap(context: Context, number: String): Bitmap? {
    if (!hasContactsPermission(context)) return null
    val lookupUri = Uri.withAppendedPath(
        ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
        Uri.encode(number)
    )
    var photoUriString: String? = null
    context.contentResolver.query(
        lookupUri,
        arrayOf(ContactsContract.PhoneLookup.PHOTO_URI),
        null, null, null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
            if (idx >= 0) photoUriString = cursor.getString(idx)
        }
    }
    val uriString = photoUriString ?: return null
    return try {
        context.contentResolver.openInputStream(Uri.parse(uriString))?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    } catch (e: Exception) {
        null
    }
}

//  Contact & bitmap; pool pick wins when there is one, contact photo is only the fallback for an empty pool.
private fun loadCallBackgroundBitmap(context: Context, number: String, chosen: BackgroundItem?): Bitmap? {
    if (chosen != null && !chosen.isVideo) {
        val poolBitmap = try {
            context.contentResolver.openInputStream(chosen.uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            null
        }
        if (poolBitmap != null) return poolBitmap
    }
    return loadContactPhotoBitmap(context, number)
}

private fun formatCallDuration(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

private fun cycleAudioRoute(state: CallAudioState?): Int {
    val mask = state?.supportedRouteMask ?: 0
    return when (state?.route) {
        CallAudioState.ROUTE_SPEAKER ->
            if (mask and CallAudioState.ROUTE_BLUETOOTH != 0) CallAudioState.ROUTE_BLUETOOTH else CallAudioState.ROUTE_WIRED_OR_EARPIECE
        CallAudioState.ROUTE_BLUETOOTH -> CallAudioState.ROUTE_WIRED_OR_EARPIECE
        else -> CallAudioState.ROUTE_SPEAKER
    }
}

private fun mergeCalls(candidate: CallInfo) {
    val c = candidate.call
    if (c.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE)) {
        c.mergeConference()
    } else {
        c.conferenceableCalls.firstOrNull()?.let { c.conference(it) }
    }
}

@Composable
private fun rememberName(context: Context, number: String): String? {
    var name by remember(number) { mutableStateOf<String?>(null) }
    LaunchedEffect(number) {
        name = withContext(Dispatchers.IO) { ContactCache.nameFor(context, number) }
    }
    return name
}

@Composable
fun InCallScreen() {
    val context = LocalContext.current
    val calls by CallStateHolder.calls.collectAsState()
    val audioState by CallStateHolder.audioState.collectAsState()
    val activeBackground by CallStateHolder.activeBackground.collectAsState()
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    val isRecording by CallRecordingManager.isRecording.collectAsState()
    val recordingScope = rememberCoroutineScope()
    val quickResponsesOn = remember { AppPrefs.quickResponsesEnabled(context) }
    val quickResponses = remember { AppPrefs.quickResponses(context).filter { it.isNotBlank() } }
    var showResponses by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }

    // Keeps the last primary around so "Call ended" still has a name once the list empties.
    val livePrimary = pickPrimary(calls)
    var lastPrimary by remember { mutableStateOf<CallInfo?>(null) }
    SideEffect { if (livePrimary != null) lastPrimary = livePrimary }
    val primary = livePrimary ?: lastPrimary

    val callState = if (livePrimary == null) Call.STATE_DISCONNECTED else livePrimary.state
    val number = primary?.number ?: "Unknown"
    val isConference = primary?.isConference == true
    val isVideoCall = VideoProfile.isVideo(primary?.videoState ?: VideoProfile.STATE_AUDIO_ONLY)
    val waiting = calls.firstOrNull { it.state == Call.STATE_RINGING && it.call !== primary?.call }
    val held = calls.firstOrNull { it.state == Call.STATE_HOLDING && it.call !== primary?.call }
    val mergeCandidate = calls.firstOrNull { it.canMerge && it.state != Call.STATE_RINGING }
    val contactName = rememberName(context, number)

    var elapsedSeconds by remember { mutableIntStateOf(0) }
    val connectedAt = primary?.connectedAtElapsed
    LaunchedEffect(connectedAt) {
        val startedAt = connectedAt ?: return@LaunchedEffect
        while (true) {
            elapsedSeconds = ((SystemClock.elapsedRealtime() - startedAt) / 1000).toInt()
            delay(1000)
        }
    }

    LaunchedEffect(number, activeBackground) {
        photo = if (activeBackground?.isVideo == true) {
            null
        } else {
            withContext(Dispatchers.IO) { loadCallBackgroundBitmap(context, number, activeBackground) }
        }
    }

    val activity = LocalContext.current as? android.app.Activity
    LaunchedEffect(calls.isEmpty()) {
        if (calls.isEmpty()) {
            delay(if (lastPrimary != null) 700 else 0)
            activity?.finish()
        }
    }

    val isRinging = callState == Call.STATE_RINGING
    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val videoBackground = activeBackground?.takeIf { it.isVideo }
    val hasVisualBackground = videoBackground != null || photo != null
    val advanced = VisualMode.advanced

    if (showNotes) {
        NotesDialog(number = number, onDismiss = { showNotes = false })
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF101415))) {
        if (videoBackground != null) {
            VideoBackgroundPlayer(
                item = videoBackground,
                playAudio = isRinging && videoBackground.hasSound && !videoBackground.muted,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val bitmap = photo
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        if (advanced || hasVisualBackground) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = if (hasVisualBackground) 0.45f else 0.85f),
                                Color(0xFF101415).copy(alpha = if (hasVisualBackground) 0.35f else 0.9f),
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                AnimatedVisibility(
                    visible = waiting != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    if (waiting != null) {
                        WaitingCallBanner(
                            info = waiting,
                            onDecline = { waiting.call.reject(false, null) },
                            onAnswer = { waiting.call.answer(waiting.videoState) }
                        )
                    }
                }
                AnimatedVisibility(
                    visible = held != null && waiting == null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    if (held != null) {
                        HeldCallRow(info = held, onSwap = { held.call.unhold() })
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    if (isConference) "Conference call" else (contactName ?: number),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Light,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.8f), blurRadius = 16f))
                )
                AnimatedVisibility(
                    visible = isVideoCall,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .liquidGlass(shape = RoundedCornerShape(50), tint = CallBlue, tintAlpha = 0.45f)
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Video call", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    if (callState == Call.STATE_ACTIVE) formatCallDuration(elapsedSeconds) else callStateLabel(callState),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                if (isConference && primary != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        primary.children.forEach { child -> ParticipantRow(child) }
                    }
                } else {
                    if (advanced) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isRinging) {
                                Box(
                                    modifier = Modifier
                                        .size(96.dp)
                                        .scale(pulseScale)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = pulseAlpha * 0.3f))
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .liquidGlass(shape = CircleShape)
                                    .blur(if (hasVisualBackground) 8.dp else 0.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (hasVisualBackground) Icons.Filled.CallIconVector else Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    } else {
                        ContactAvatar(name = contactName, size = 88.dp)
                    }
                }
            }

            Crossfade(targetState = isRinging, label = "callControls") { ringing ->
                if (ringing) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        AnimatedVisibility(
                            visible = showResponses && quickResponsesOn,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            GlassCard(modifier = Modifier.padding(bottom = 20.dp)) {
                                quickResponses.forEach { text ->
                                    Text(
                                        text,
                                        fontSize = 15.sp,
                                        color = TextPrimary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pressScale(onClick = { primary?.call?.reject(true, text) })
                                            .padding(horizontal = 20.dp, vertical = 14.dp)
                                    )
                                }
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CallActionButton(
                                icon = Icons.Filled.CallEnd,
                                label = "DECLINE",
                                color = CallRed,
                                onClick = { primary?.call?.reject(false, null) }
                            )
                            if (quickResponsesOn) {
                                CallActionButton(
                                    icon = Icons.AutoMirrored.Filled.Message,
                                    label = "MESSAGE",
                                    color = CallBlue,
                                    onClick = { showResponses = !showResponses }
                                )
                            }
                            CallActionButton(
                                icon = Icons.Filled.CallIconVector,
                                label = "ACCEPT",
                                color = CallGreen,
                                onClick = {
                                    // Forcing STATE_AUDIO_ONLY here would decline the video half of
                                    // an incoming video call even when the caller offered it -- answer
                                    // with whatever was actually requested instead.
                                    val requestedState = primary?.call?.details?.videoState ?: VideoProfile.STATE_AUDIO_ONLY
                                    primary?.call?.answer(requestedState)
                                }
                            )
                        }
                    }
                } else {
                    val route = audioState?.route
                    val isSpeakerOn = route == CallAudioState.ROUTE_SPEAKER
                    val isBluetooth = route == CallAudioState.ROUTE_BLUETOOTH
                    val isMuted = audioState?.isMuted == true
                    val isHolding = callState == Call.STATE_HOLDING
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        ) {
                            ControlButton(
                                icon = if (isBluetooth) Icons.Filled.Bluetooth else Icons.Filled.VolumeUp,
                                label = if (isBluetooth) "Bluetooth" else "Speaker",
                                active = isSpeakerOn || isBluetooth,
                                accent = CallBlue,
                                onClick = { TurboInCallService.instance?.setAudioRoute(cycleAudioRoute(audioState)) }
                            )
                            ControlButton(
                                icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                label = "Mute",
                                active = isMuted,
                                accent = AccentAmber,
                                onClick = { TurboInCallService.instance?.setMuted(!isMuted) }
                            )
                            ControlButton(
                                icon = if (isHolding) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                label = if (isHolding) "Resume" else "Hold",
                                active = isHolding,
                                accent = AccentViolet,
                                onClick = { if (isHolding) primary?.call?.unhold() else primary?.call?.hold() }
                            )
                            ControlButton(
                                icon = null,
                                label = "Record",
                                active = isRecording,
                                recordDot = true,
                                onClick = {
                                    recordingScope.launch(Dispatchers.IO) {
                                        if (isRecording) CallRecordingManager.stop()
                                        else CallRecordingManager.start(context, if (isConference) "conference" else number, primary?.call)
                                    }
                                }
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
                        ) {
                            ControlButton(
                                icon = Icons.Filled.NoteAdd,
                                label = "Notes",
                                active = false,
                                onClick = { showNotes = true }
                            )
                            ControlButton(
                                icon = Icons.Filled.PersonAdd,
                                label = "Add call",
                                active = false,
                                onClick = {
                                    context.startActivity(
                                        Intent(context, MainActivity::class.java)
                                            .setAction(Intent.ACTION_DIAL)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            )
                            if (held != null || primary?.canSwapConference == true) {
                                ControlButton(
                                    icon = Icons.Filled.SwapCalls,
                                    label = "Swap",
                                    active = false,
                                    onClick = {
                                        if (held != null) held.call.unhold() else primary?.call?.swapConference()
                                    }
                                )
                            }
                            if (mergeCandidate != null) {
                                ControlButton(
                                    icon = Icons.Filled.CallMerge,
                                    label = "Merge",
                                    active = false,
                                    onClick = { mergeCalls(mergeCandidate) }
                                )
                            }
                        }
                        CallActionButton(
                            icon = Icons.Filled.CallEnd,
                            label = "END CALL",
                            color = CallRed,
                            onClick = { primary?.call?.disconnect() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WaitingCallBanner(info: CallInfo, onDecline: () -> Unit, onAnswer: () -> Unit) {
    val context = LocalContext.current
    val name = rememberName(context, info.number)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(shape = RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name ?: info.number, fontSize = 16.sp, color = Color.White, maxLines = 1)
            Text("Call waiting", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
        }
        MiniRoundButton(Icons.Filled.CallEnd, CallRed, onDecline)
        Spacer(modifier = Modifier.width(10.dp))
        MiniRoundButton(Icons.Filled.CallIconVector, CallGreen, onAnswer)
    }
}

@Composable
private fun HeldCallRow(info: CallInfo, onSwap: () -> Unit) {
    val context = LocalContext.current
    val name = rememberName(context, info.number)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(shape = RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(if (info.isConference) "Conference call" else (name ?: info.number), fontSize = 16.sp, color = Color.White, maxLines = 1)
            Text("On hold", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
        }
        MiniRoundButton(Icons.Filled.SwapCalls, CallBlue, onSwap)
    }
}

@Composable
private fun ParticipantRow(info: CallInfo) {
    val context = LocalContext.current
    val name = rememberName(context, info.number)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(shape = RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name ?: info.number, fontSize = 15.sp, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
        if (info.canSplit) {
            MiniRoundButton(Icons.Filled.CallSplit, CallBlue) { info.call.splitFromConference() }
            Spacer(modifier = Modifier.width(8.dp))
        }
        MiniRoundButton(Icons.Filled.CallEnd, CallRed) { info.call.disconnect() }
    }
}

@Composable
private fun MiniRoundButton(icon: ImageVector, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .liquidGlass(shape = CircleShape, tint = color, tintAlpha = 0.75f)
            .pressScale(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun NotesDialog(number: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    val existing = remember(number) { AppPrefs.callNotes(context, number) }
    val formatter = remember { java.text.SimpleDateFormat("MMM d, " + AppPrefs.timePattern(context), java.util.Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = { Text("Call notes", color = TextPrimary) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Write a note...", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentIndigo,
                        unfocusedBorderColor = OutlineFaint
                    )
                )
                if (existing.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(modifier = Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState())) {
                        existing.reversed().forEach { (time, note) ->
                            Text(formatter.format(java.util.Date(time)), fontSize = 11.sp, color = TextSecondary)
                            Text(note, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.padding(bottom = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val note = text
                AppPrefs.addCallNote(context, number, note)
                val appContext = context.applicationContext
                Thread { CallNoteExporter.export(appContext, number, note) }.start()
                onDismiss()
            }) { Text("Save", color = AccentIndigo) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextPrimary) }
        }
    )
}

@Composable
private fun CallActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .liquidGlass(shape = CircleShape, tint = color, tintAlpha = 0.75f)
                .pressScale(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector?,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    recordDot: Boolean = false,
    accent: Color = CallBlue
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .liquidGlass(
                    shape = CircleShape,
                    tint = if (recordDot && active) CallRed else if (active) accent else GlassTint,
                    tintAlpha = if (active) 0.8f else 0.18f
                )
                .pressScale(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (recordDot) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (active) Color.White else CallRed)
                )
            } else if (icon != null) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
    }
}

// ?
EOF

cat > ~/IQDialer/app/src/main/java/com/iqstudio/dialer/TurboInCallService.kt << 'EOF'
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
import android.telecom.TelecomManager
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
    private var silencedCall: Call? = null

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
                silenceRingerForVideoSound(ringing.call)
            } else {
                IncomingRinger.stop()
                CallStateHolder.customRingerArmed = false
                silencedCall = null
            }

            updateProximity(list)

            if (tracked.isEmpty()) {
                releaseProximity()
                endForegroundPresentation()
                CallBubbleService.stop()
                CallStateHolder.setCalls(emptyList())
                return
            }
            present(ringing, pickPrimary(live))
        } catch (e: Exception) {
            Log.e(TAG, "refresh failed", e)
        }
    }

    // A video background with sound plays its own audio, so the system ringtone must not play as well.
    // Call screening normally silences it first; this covers the case where that did not take effect.
    private fun silenceRingerForVideoSound(call: Call) {
        if (silencedCall === call) return
        val background = CallStateHolder.activeBackground.value ?: return
        if (!background.isVideo || !background.hasSound || background.muted) return
        silencedCall = call
        try {
            getSystemService(TelecomManager::class.java)?.silenceRinger()
        } catch (e: Exception) {
            Log.e(TAG, "silenceRinger failed", e)
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
                    CallBubbleService.show(this)
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
                CallBubbleService.show(this)
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
EOF

echo "Script 67 done."
