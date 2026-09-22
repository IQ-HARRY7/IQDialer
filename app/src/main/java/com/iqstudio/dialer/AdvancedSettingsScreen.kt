//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later).
// *
//**************************************************
package com.iqstudio.dialer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// See RecentsScreen.kt for the same pattern -- NestedPane and
// nestedPaneTransitionSpec (UiComponents.kt) are shared so every
// nested-screen switch in the app reads as one consistent push/pop system.
private sealed interface AdvancedPane : NestedPane {
    object Main : AdvancedPane { override val paneDepth = 0 }
    data class Editing(val item: BackgroundItem) : AdvancedPane { override val paneDepth = 1 }
}

@Composable
fun AdvancedSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var backgrounds by remember { mutableStateOf(AppPrefs.backgrounds(context)) }
    var editingItem by remember { mutableStateOf<BackgroundItem?>(null) }
    var dialpadSound by remember { mutableStateOf(AppPrefs.dialpadSoundEnabled(context)) }
    var redialAuto by remember { mutableStateOf(AppPrefs.redialAutomatically(context)) }
    var missedCallReminder by remember { mutableStateOf(AppPrefs.missedCallReminder(context)) }
    var vibrateOnAnswer by remember { mutableStateOf(AppPrefs.vibrateOnAnswer(context)) }
    var callWaitingNotification by remember { mutableStateOf(AppPrefs.callWaitingNotification(context)) }
    var quickResponses by remember { mutableStateOf(AppPrefs.quickResponsesEnabled(context)) }

    val pane: AdvancedPane = editingItem?.let { AdvancedPane.Editing(it) } ?: AdvancedPane.Main

    AnimatedContent(
        targetState = pane,
        transitionSpec = { nestedPaneTransitionSpec() },
        label = "advancedPane"
    ) { currentPane ->
        when (currentPane) {
            is AdvancedPane.Editing -> BackgroundFitEditor(
                item = currentPane.item,
                onSave = { updated ->
                    AppPrefs.updateBackground(context, updated)
                    backgrounds = AppPrefs.backgrounds(context)
                    editingItem = null
                },
                onCancel = { editingItem = null }
            )
            AdvancedPane.Main -> {
                BackHandler(onBack = onBack)

                val switchColors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = GlassTint,
                    checkedBorderColor = GlassTint
                )

                val pickMedia = rememberLauncherForActivityResult(
                    ActivityResultContracts.PickMultipleVisualMedia(20)
                ) { uris ->
                    if (uris.isEmpty()) return@rememberLauncherForActivityResult
                    scope.launch(Dispatchers.IO) {
                        uris.forEach { uri ->
                            try {
                                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            } catch (e: Exception) {
                                // i also don't know how it works, still in Debug 😶
                            }
                            val isVideo = context.contentResolver.getType(uri)?.startsWith("video/") == true
                            val hasSound = isVideo && probeHasAudio(context, uri)
                            AppPrefs.addBackground(context, BackgroundItem(uri = uri, isVideo = isVideo, hasSound = hasSound))
                        }
                        backgrounds = AppPrefs.backgrounds(context)
                    }
                }

                val ringtoneLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                    }
                    AppPrefs.setRingtoneUri(context, uri)
                    try {
                        if (Settings.System.canWrite(context)) {
                            RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, uri)
                        }
                    } catch (e: Exception) {
                        // No WRITE_SETTINGS access -- the choice is still saved above - UX app would misbehave without this. -@IQ_HARRY_07
                    }
                }

                Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassIconButton(icon = Icons.Filled.ArrowBack, contentDescription = "Back", onClick = onBack)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Advanced settings", fontSize = 20.sp, color = TextPrimary)
                    }

                    Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                        GlassCard {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text("Call screen backgrounds", fontSize = 16.sp, color = TextPrimary)
                                Text(
                                    "Shown on the incoming/active call screen when the caller has no saved contact photo. Add photos or videos -- one is picked at random for each call. A video with sound can replace the ringtone entirely unless you mute it below.",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                                )

                                Column(modifier = Modifier.animateContentSize()) {
                                    backgrounds.forEach { item ->
                                        BackgroundRow(
                                            item = item,
                                            onToggleMute = { muted ->
                                                AppPrefs.updateBackground(context, item.copy(muted = muted))
                                                backgrounds = AppPrefs.backgrounds(context)
                                            },
                                            onEdit = { editingItem = item },
                                            onRemove = {
                                                AppPrefs.removeBackground(context, item.uri)
                                                backgrounds = AppPrefs.backgrounds(context)
                                            }
                                        )
                                        HorizontalDivider(color = OutlineFaint.copy(alpha = 0.3f))
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                GlassButton(onClick = {
                                    pickMedia.launch(
                                        PickVisualMediaRequest.Builder()
                                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                            .build()
                                    )
                                }) { Text(if (backgrounds.isEmpty()) "Add photos or videos" else "Add more", color = Color.White) }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        GlassCard {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text("Ringtone", fontSize = 16.sp, color = TextPrimary)
                                Text(
                                    "Applies system-wide when the device allows it; otherwise remembered by IQ Dialer only.",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                )
                                GlassOutlinedButton(onClick = {
                                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, AppPrefs.ringtoneUri(context))
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    }
                                    ringtoneLauncher.launch(intent)
                                }) { Text("Choose ringtone", color = Color.White) }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Stock-dialer style "Dialer" / "Answering and Calls" / "Other"
                        // groups James asked for -- basic scaffolding for now: the
                        // toggles and pickers here save a preference, but none of them
                        // are wired to real telephony behavior yet (that's follow-up
                        // work, one feature at a time). "Caller ID" isn't included --
                        // its rows weren't visible in the reference screenshot.
                        GlassCard {
                            Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                                Text("Dialer", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                            }
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text("Dial pad touch tones", color = TextPrimary) },
                                supportingContent = { Text("Play a tone when you tap a dialpad key", color = TextSecondary) },
                                trailingContent = {
                                    Switch(
                                        checked = dialpadSound,
                                        onCheckedChange = {
                                            dialpadSound = it
                                            AppPrefs.setDialpadSound(context, it)
                                        },
                                        colors = switchColors
                                    )
                                }
                            )
                            HorizontalDivider(color = OutlineFaint.copy(alpha = 0.3f))
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text("Quick dial", color = TextPrimary) },
                                supportingContent = { Text("Assign contacts to number keys -- coming soon", color = TextSecondary) }
                            )
                            HorizontalDivider(color = OutlineFaint.copy(alpha = 0.3f))
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text("Redial automatically", color = TextPrimary) },
                                supportingContent = { Text("Redial automatically if the line is busy", color = TextSecondary) },
                                trailingContent = {
                                    Switch(
                                        checked = redialAuto,
                                        onCheckedChange = {
                                            redialAuto = it
                                            AppPrefs.setRedialAutomatically(context, it)
                                        },
                                        colors = switchColors
                                    )
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        GlassCard {
                            Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                                Text("Answering and Calls", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                            }
                            PickerRow(
                                title = "Missed call reminders",
                                subtitle = "Reminders are separated by 5 minute intervals",
                                value = missedCallReminder,
                                options = listOf("No reminder", "Once", "Every 5 minutes"),
                                onSelect = {
                                    missedCallReminder = it
                                    AppPrefs.setMissedCallReminder(context, it)
                                }
                            )
                            HorizontalDivider(color = OutlineFaint.copy(alpha = 0.3f))
                            PickerRow(
                                title = "Vibrate when your call is answered",
                                value = vibrateOnAnswer,
                                options = listOf("Off", "Normal"),
                                onSelect = {
                                    vibrateOnAnswer = it
                                    AppPrefs.setVibrateOnAnswer(context, it)
                                }
                            )
                            HorizontalDivider(color = OutlineFaint.copy(alpha = 0.3f))
                            PickerRow(
                                title = "Call waiting notification",
                                value = callWaitingNotification,
                                options = listOf("Play notification sound once", "Play notification sound continuously"),
                                onSelect = {
                                    callWaitingNotification = it
                                    AppPrefs.setCallWaitingNotification(context, it)
                                }
                            )
                            HorizontalDivider(color = OutlineFaint.copy(alpha = 0.3f))
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text("Quick responses", color = TextPrimary) },
                                supportingContent = { Text("Preset texts for declining a call with a message", color = TextSecondary) },
                                trailingContent = {
                                    Switch(
                                        checked = quickResponses,
                                        onCheckedChange = {
                                            quickResponses = it
                                            AppPrefs.setQuickResponsesEnabled(context, it)
                                        },
                                        colors = switchColors
                                    )
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        GlassCard {
                            Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                                Text("Other", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                            }
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text("Call barring", color = TextPrimary) },
                                supportingContent = { Text("Voice call barring settings -- coming soon", color = TextSecondary) }
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

// Value + tap-to-pick-from-a-short-list row, shared by the three "Answering
// and Calls" pickers above. Nothing fancier than a DropdownMenu -- these
// aren't wired to real behavior yet, so a bigger picker UI isn't earned yet.
@Composable
private fun PickerRow(title: String, subtitle: String? = null, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { expanded = true },
        headlineContent = { Text(title, color = TextPrimary) },
        supportingContent = subtitle?.let { { Text(it, color = TextSecondary) } },
        trailingContent = {
            Box {
                Text(value, color = TextSecondary, fontSize = 13.sp)
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
                    }
                }
            }
        }
    )
}

@Composable
private fun BackgroundRow(
    item: BackgroundItem,
    onToggleMute: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    var thumb by remember(item.uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.uri) { thumb = loadThumbnail(context, item) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceCard),
            contentAlignment = Alignment.Center
        ) {
            val bmp = thumb
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (item.isVideo) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(if (item.isVideo) "Video" else "Photo", color = TextPrimary, fontSize = 14.sp)
            if (item.isVideo && item.hasSound) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("No sound", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(end = 4.dp))
                    Switch(
                        checked = item.muted,
                        onCheckedChange = onToggleMute,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = GlassTint),
                        modifier = Modifier.scale(0.7f)
                    )
                }
            }
        }
        GlassChip(text = "Edit fit", onClick = onEdit)
        Spacer(modifier = Modifier.width(6.dp))
        GlassIconButton(icon = Icons.Filled.Close, contentDescription = "Remove", tint = CallRed, onClick = onRemove)
    }
}

// ZoomIn/Zoomout feature not implemented yet properly, but soon, this is in development.
@Composable
private fun BackgroundFitEditor(
    item: BackgroundItem,
    onSave: (BackgroundItem) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(item.scale) }
    var offsetX by remember { mutableFloatStateOf(item.offsetX) }
    var offsetY by remember { mutableFloatStateOf(item.offsetY) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    BackHandler(onBack = onCancel)

    LaunchedEffect(item.uri) {
        if (!item.isVideo) {
            previewBitmap = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it) }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(icon = Icons.Filled.ArrowBack, contentDescription = "Cancel", onClick = onCancel)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Adjust fit", fontSize = 18.sp, color = Color.White)
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .pointerInput(item.uri) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
        ) {
            if (item.isVideo) {
                VideoBackgroundPlayer(
                    item = item.copy(muted = true),
                    scale = scale,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val bmp = previewBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
                    )
                }
            }
        }

        Text(
            "Pinch to zoom, drag to reposition",
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            GlassOutlinedButton(onClick = onCancel) { Text("Cancel", color = Color.White) }
            GlassButton(onClick = { onSave(item.copy(scale = scale, offsetX = offsetX, offsetY = offsetY)) }) {
                Text("Save", color = Color.White)
            }
        }
    }
}


/*
 ✨✨✨✨✨✨✨✨✨✨, NINJA TECHNIQUE TO GET ATTENTION - still in development, already implemented, need further improvements.


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
        stopRingtoneAudio()
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

*/

private suspend fun loadThumbnail(context: Context, item: BackgroundItem): Bitmap? = withContext(Dispatchers.IO) {
    if (item.isVideo) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, item.uri)
            retriever.getFrameAtTime(0)
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    } else {
        try {
            context.contentResolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        }
    }
}

private fun probeHasAudio(context: Context, uri: Uri): Boolean {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
    } catch (e: Exception) {
        false
    } finally {
        retriever.release()
    }
}

/*. TEMPORARILY DISABLED --> 


un lookupContactName(context: Context, number: String): String? {
    if (!hasContactsPermission(context)) return null
    val lookupUri = Uri.withAppendedPath(
        ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
        Uri.encode(number)
    )
    return try {
        context.contentResolver.query(
            lookupUri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else {
                null
            }
        }
    } catch (e: Exception) {
        null
    }

*/


// HOPE IS BEAUTIFUL ❤️

// Wen Give a star sar? 😭🥀