//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later). 
// * 
//**************************************************

// Call screen - must be minimal! else it will lag/cause issues on some ROMS.  
package com.iqstudio.dialer

import android.provider.BlockedNumberContract
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.CallScreeningService.CallResponse
import android.util.Log

// fixed the default dialer highjack! should be fixed now. - @IQ_HARRY_07
class TurboCallScreeningService : CallScreeningService() {

    companion object {
        private const val TAG = "TurboCallScreening"
    }

    override fun onScreenCall(callDetails: Call.Details) {
        try {
            val number = callDetails.handle?.schemeSpecificPart
            val blocked = number != null && BlockedNumberContract.isBlocked(this, number)

            // Rolled here, once, and handed off via CallStateHolder so TurboInCallService
            var silenceRinger = false
            CallStateHolder.customRingerArmed = false
            if (!blocked) {
                val chosen = AppPrefs.randomBackground(this)
                CallStateHolder.setPendingBackground(chosen)
                
                val videoSound = chosen != null && chosen.isVideo && chosen.hasSound && !chosen.muted
                
                val custom = !videoSound && IncomingRinger.shouldArm(this, number)
                CallStateHolder.customRingerArmed = custom
                silenceRinger = videoSound || custom
            }

            val response = CallResponse.Builder()
                .setDisallowCall(blocked)
                .setRejectCall(blocked)
                .setSkipNotification(blocked)
                .setSkipCallLog(false)
                .setSilenceCall(silenceRinger)
                .build()
            respondToCall(callDetails, response)
        } catch (e: Exception) {
            Log.e(TAG, "onScreenCall failed, allowing call through", e)
            respondToCall(callDetails, CallResponse.Builder().build())
        }
    }
}

// Yare yare. 
