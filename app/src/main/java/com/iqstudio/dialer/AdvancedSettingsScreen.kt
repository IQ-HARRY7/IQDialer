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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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

@Composable
fun AdvancedSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var backgrounds by remember { mutableStateOf(AppPrefs.backgrounds(context)) }
    var editingItem by remember { mutableStateOf<BackgroundItem?>(null) }

    val editing = editingItem
    if (editing != null) {
        BackgroundFitEditor(
            item = editing,
            onSave = { updated ->
                AppPrefs.updateBackground(context, updated)
                backgrounds = AppPrefs.backgrounds(context)
                editingItem = null
            },
            onCancel = { editingItem = null }
        )
        return
    }

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

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
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
