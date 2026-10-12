//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later). 
// * 
//**************************************************

// Application setup: interface mode, crash reporting and notification channels.

package com.iqstudio.dialer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

const val INCOMING_CALL_CHANNEL_ID = "incoming_calls"
const val INCOMING_CALL_NOTIFICATION_ID = 4001

class IQDialerApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        VisualMode.load(this)
        AppTheme.load(this)
        CrashReporter.install(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val callChannel = NotificationChannel(
                INCOMING_CALL_CHANNEL_ID,
                "Incoming calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Full-screen incoming call alert"
                
                setSound(null, null)
                enableVibration(false)
            }

            val bubbleChannel = NotificationChannel(
                BUBBLE_CHANNEL_ID,
                "Ongoing call",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shown while the call bubble is on screen"
                setSound(null, null)
                enableVibration(false)
            }

            manager?.createNotificationChannel(callChannel)
            manager?.createNotificationChannel(bubbleChannel)
        }

        if (VisualMode.advanced) {
            Thread { BackgroundImageCache.get(applicationContext) }.start()
        }
    }
}
