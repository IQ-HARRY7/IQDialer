//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later). 
// * 
//**************************************************

// App.kt safety (debugging!)!

package com.iqstudio.dialer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log

const val INCOMING_CALL_CHANNEL_ID = "incoming_calls"
const val INCOMING_CALL_NOTIFICATION_ID = 4001

class IQDialerApplication : Application() {
    override fun onCreate() {
        super.onCreate()

// Logcat for app crashes (developer use only!) not a release stuff - @IQ_HARRY_07
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("IQDialerCrash", "FATAL on thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

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

        // Fire-and-forget warm-up so the JPEG is likely already decoded by
        // the time the first screen composes UniversalBackground, instead
        // of blocking that first composition on the decode. Plain Thread,
        // not a coroutine scope -- this is a one-shot, app-lifetime task
        // with nothing to cancel, GlobalScope would just be the same thing
        // with an extra dependency on structured-concurrency machinery it
        // doesn't need.
        Thread { BackgroundImageCache.get(applicationContext) }.start()
    }
}

// Dead! 
