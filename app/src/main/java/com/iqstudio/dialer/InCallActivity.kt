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
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call as CallIconVector
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
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
        setContent {
            IQDialerTheme {
                InCallScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isVisible = true
        // We're the one on screen now -- the bubble (if it was up) shouldn't be.
        stopService(Intent(this, CallBubbleService::class.java))
    }

    override fun onPause() {
        super.onPause()
        isVisible = false
        // leaving the bubble...
        val stillOngoing = CallStateHolder.activeCall.value != null
        if (stillOngoing && !isFinishing) {
            ContextCompat.startForegroundService(this, Intent(this, CallBubbleService::class.java))
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

//  Contact & bitmap; 
private fun loadCallBackgroundBitmap(context: Context, number: String, chosen: BackgroundItem?): Bitmap? {
    loadContactPhotoBitmap(context, number)?.let { return it }
    if (chosen == null || chosen.isVideo) return null
    return try {
        context.contentResolver.openInputStream(chosen.uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
fun InCallScreen() {
    val context = LocalContext.current
    val call by CallStateHolder.activeCall.collectAsState()
    val audioState by CallStateHolder.audioState.collectAsState()
    val activeBackground by CallStateHolder.activeBackground.collectAsState()
    var callState by remember { mutableStateOf(Call.STATE_DISCONNECTED) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var contactName by remember { mutableStateOf<String?>(null) }
    val isRecording by CallRecordingManager.isRecording.collectAsState()
    val recordingScope = rememberCoroutineScope()

    val number = call?.details?.handle?.schemeSpecificPart ?: "Unknown"

    DisposableEffect(call) {
        val current = call
        val callback = object : Call.Callback() {
            override fun onStateChanged(c: Call, state: Int) {
                callState = state
            }
        }
        current?.registerCallback(callback)
        callState = current?.state ?: Call.STATE_DISCONNECTED
        onDispose { current?.unregisterCallback(callback) }
    }

    // Call effect & processing. 
    LaunchedEffect(number) {
        contactName = withContext(Dispatchers.IO) { lookupContactName(context, number) }
    }

    LaunchedEffect(number, activeBackground) {
        photo = if (activeBackground?.isVideo == true) {
            null
        } else {
            withContext(Dispatchers.IO) { loadCallBackgroundBitmap(context, number, activeBackground) }
        }
    }

    val activity = LocalContext.current as? android.app.Activity
    LaunchedEffect(callState) {
        if (callState == Call.STATE_DISCONNECTED) {
            if (CallRecordingManager.isRecording.value) CallRecordingManager.stop()
            delay(500)
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

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(56.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    contactName ?: number,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Light,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.8f), blurRadius = 16f))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    callStateLabel(callState),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.height(28.dp))
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
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
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
            }

            Crossfade(targetState = isRinging, label = "callControls") { ringing ->
                if (ringing) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                    ) {
                        CallActionButton(
                            icon = Icons.Filled.CallEnd,
                            label = "DECLINE",
                            color = CallRed,
                            onClick = { call?.reject(false, null) }
                        )
                        CallActionButton(
                            icon = Icons.Filled.CallIconVector,
                            label = "ACCEPT",
                            color = CallGreen,
                            onClick = { call?.answer(VideoProfile.STATE_AUDIO_ONLY) }
                        )
                    }
                } else {
                    val isSpeakerOn = audioState?.route == CallAudioState.ROUTE_SPEAKER
                    val isMuted = audioState?.isMuted == true
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
                        ) {
                            SmallToggleButton(
                                icon = Icons.Filled.VolumeUp,
                                active = isSpeakerOn,
                                onClick = {
                                    val target = if (isSpeakerOn) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER
                                    TurboInCallService.instance?.setAudioRoute(target)
                                }
                            )
                            SmallToggleButton(
                                icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                active = isMuted,
                                onClick = { TurboInCallService.instance?.setMuted(!isMuted) }
                            )
                            RecordToggleButton(
                                active = isRecording,
                                onClick = {
                                    recordingScope.launch(Dispatchers.IO) {
                                        if (isRecording) CallRecordingManager.stop()
                                        else CallRecordingManager.start(context, number)
                                    }
                                }
                            )
                        }
                        CallActionButton(
                            icon = Icons.Filled.CallEnd,
                            label = "END CALL",
                            color = CallRed,
                            onClick = { call?.disconnect() }
                        )
                    }
                }
            }
        }
    }
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
                .clip(CircleShape)
                .background(color)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
private fun SmallToggleButton(
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (active) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.15f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) Color(0xFF101415) else Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

// Plain dot rather than an icon on purpose -- keeps this independent of any
// specific icon existing in whatever material-icons-core version is on the
// classpath. Red ring + white center while recording, the reverse otherwise.
@Composable
private fun RecordToggleButton(active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (active) CallRed else Color.White.copy(alpha = 0.15f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(if (active) Color.White else CallRed)
        )
    }
}
