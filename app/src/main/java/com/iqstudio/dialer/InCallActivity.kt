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
                AppPrefs.addCallNote(context, number, text)
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
