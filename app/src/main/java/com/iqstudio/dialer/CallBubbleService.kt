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
            showBubble()
        } catch (e: Exception) {
            Log.e(TAG, "onCreate failed, stopping bubble service", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (bubbleView == null) {
            showBubble()
        }
        return START_NOT_STICKY
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
            val displayName = lookupContactName(this, number) ?: number

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
                // view may already be detached (call ended mid-drag) -- ignore
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
        registeredCall?.unregisterCallback(callCallback)
        bubbleView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // view already gone -- fine
            }
        }
        bubbleView = null
        super.onDestroy()
    }
}

// end bruh, what else do you expect? 😐