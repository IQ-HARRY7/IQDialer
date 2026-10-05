//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later).
// *
//**************************************************

// 1st screen.
// working fine.

package com.iqstudio.dialer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.provider.CallLog
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private fun callDirectionGlyph(type: Int): String = when (type) {
    CallLog.Calls.OUTGOING_TYPE -> "\u2197"
    CallLog.Calls.MISSED_TYPE -> "\u2199"
    CallLog.Calls.REJECTED_TYPE -> "\u2298"
    CallLog.Calls.BLOCKED_TYPE -> "\u2298"
    CallLog.Calls.INCOMING_TYPE -> "\u2199"
    else -> "\u2022"
}

private fun callDirectionColor(type: Int): Color = when (type) {
    CallLog.Calls.OUTGOING_TYPE -> Color(0xFFADC6FF)
    CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE, CallLog.Calls.BLOCKED_TYPE -> CallRed
    else -> TextSecondary
}

// Nah
private sealed interface RecentsPane : NestedPane {
    object List : RecentsPane { override val paneDepth = 0 }
    object Settings : RecentsPane { override val paneDepth = 1 }
    data class Contact(val number: String) : RecentsPane { override val paneDepth = 1 }
}

@Composable
fun RecentsScreen(onNestedScreenChange: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val groupedOrNull by DataCache.groupedCallLog.collectAsState()
    LaunchedEffect(Unit) { DataCache.ensureLoaded(context) }
    val grouped = groupedOrNull ?: emptyList()
    val loaded = groupedOrNull != null
    var query by remember { mutableStateOf("") }
    var selectedNumber by remember { mutableStateOf<String?>(null) }
    var showDialpad by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var menuKey by remember { mutableStateOf<String?>(null) }
    var selectMode by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var flagged by remember { mutableStateOf(AppPrefs.flaggedNumbers(context)) }
    val haptics = LocalHapticFeedback.current
    val ioScope = rememberCoroutineScope()

    LaunchedEffect(DialpadState.openRequested) {
        if (DialpadState.openRequested) {
            showDialpad = true
            DialpadState.openRequested = false
        }
    }

    val nested = showSettings || selectedNumber != null
    LaunchedEffect(nested) {
        onNestedScreenChange(nested)
        if (nested) showDialpad = false
    }
    DisposableEffect(Unit) { onDispose { onNestedScreenChange(false) } }
    BackHandler(enabled = showDialpad) { showDialpad = false }
    BackHandler(enabled = selectMode && !showDialpad) {
        selectMode = false
        selectedKeys = emptySet()
    }

    val filtered = remember(grouped, query) {
        if (query.isBlank()) grouped
        else grouped.filter {
            (it.name?.contains(query, ignoreCase = true) == true) ||
                it.number.contains(query, ignoreCase = true)
        }
    }
    val formatter = java.text.SimpleDateFormat("MMM d, " + AppPrefs.timePattern(context), java.util.Locale.getDefault())

    val pane: RecentsPane = when {
        showSettings -> RecentsPane.Settings
        selectedNumber != null -> RecentsPane.Contact(selectedNumber!!)
        else -> RecentsPane.List
    }

    AnimatedContent(
        targetState = pane,
        transitionSpec = { nestedPaneTransitionSpec() },
        label = "recentsPane"
    ) { currentPane ->
        when (currentPane) {
            RecentsPane.Settings -> SettingsScreen(onBack = { showSettings = false })
            is RecentsPane.Contact -> ContactDetailScreen(phoneNumber = currentPane.number, onBack = { selectedNumber = null })
            RecentsPane.List -> Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectMode) {
                            Text(selectedKeys.size.toString() + " selected", fontSize = 22.sp, color = TextPrimary)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (selectedKeys.size == filtered.size) "Clear" else "Select all",
                                    color = AccentIndigo,
                                    fontSize = 15.sp,
                                    modifier = Modifier
                                        .pressScale(onClick = {
                                            selectedKeys = if (selectedKeys.size == filtered.size) emptySet()
                                            else filtered.map { it.number + "_" + it.date }.toSet()
                                        })
                                        .padding(8.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                GlassIconButton(
                                    icon = Icons.Filled.Close,
                                    contentDescription = "Cancel",
                                    onClick = {
                                        selectMode = false
                                        selectedKeys = emptySet()
                                    }
                                )
                            }
                        } else {
                            Text("Recents", fontSize = 26.sp)
                            GlassIconButton(
                                icon = Icons.Filled.Settings,
                                contentDescription = "Settings",
                                onClick = { showSettings = true }
                            )
                        }
                    }

                    SearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Search recents",
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val listState = when {
                        !loaded -> "loading"
                        filtered.isEmpty() -> "empty"
                        else -> "list"
                    }
                    Crossfade(
                        targetState = listState,
                        label = "recentsListState"
                    ) { state ->
                        when (state) {
                            "loading" -> Box(modifier = Modifier.fillMaxSize()) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            }
                            "empty" -> Box(modifier = Modifier.fillMaxSize()) {
                                Text(
                                    if (grouped.isEmpty()) "No call history yet" else "No matches",
                                    color = TextSecondary,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                            else -> LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 88.dp)
                            ) {
                                items(filtered, key = { it.number + "_" + it.date }) { entry ->
                                    val missed = entry.type == CallLog.Calls.MISSED_TYPE || entry.type == CallLog.Calls.REJECTED_TYPE
                                    val rowKey = entry.number + "_" + entry.date
                                    val isFlagged = entry.number in flagged
                                    Box(modifier = Modifier.animateItem()) {
                                    GlassRow(
                                        onClick = {
                                            if (selectMode) {
                                                selectedKeys = if (rowKey in selectedKeys) selectedKeys - rowKey else selectedKeys + rowKey
                                            } else {
                                                selectedNumber = entry.number
                                            }
                                        },
                                        onLongClick = {
                                            if (!selectMode) {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuKey = rowKey
                                            }
                                        }
                                    ) {
                                        if (selectMode) {
                                            Icon(
                                                if (rowKey in selectedKeys) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                                contentDescription = null,
                                                tint = if (rowKey in selectedKeys) AccentIndigo else TextSecondary,
                                                modifier = Modifier.padding(end = 10.dp).size(24.dp)
                                            )
                                        }
                                        ContactAvatar(name = entry.name)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                (entry.name ?: entry.number) + (if (isFlagged) "  \u2691" else ""),
                                                fontSize = 16.sp,
                                                color = if (missed) CallRed else TextPrimary
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    callDirectionGlyph(entry.type),
                                                    fontSize = 12.sp,
                                                    color = callDirectionColor(entry.type)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    callTypeLabel(entry.type) +
                                                        (if (entry.count > 1) " (" + entry.count + ")" else ""),
                                                    fontSize = 13.sp,
                                                    color = TextSecondary
                                                )
                                            }
                                        }
                                        Text(
                                            formatter.format(java.util.Date(entry.date)),
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }
                                    GlassDropdownMenu(expanded = menuKey == rowKey, onDismissRequest = { menuKey = null }) {
                                        GlassDropdownMenuItem("Send a message", icon = Icons.AutoMirrored.Filled.Message, accent = AccentIndigo) {
                                            menuKey = null
                                            try {
                                                context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + entry.number)))
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "No messaging app found", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        GlassDropdownMenuItem("Edit", icon = Icons.Filled.Edit, accent = AccentAmber) {
                                            menuKey = null
                                            DialpadState.open(entry.number)
                                        }
                                        GlassDropdownMenuItem("Copy", icon = Icons.Filled.ContentCopy, accent = AccentTeal) {
                                            menuKey = null
                                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                                            clipboard?.setPrimaryClip(ClipData.newPlainText("Phone number", entry.number))
                                            Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
                                        }
                                        GlassDropdownMenuItem(if (isFlagged) "Unflag" else "Flag", icon = Icons.Filled.Flag, accent = AccentOrange) {
                                            menuKey = null
                                            AppPrefs.toggleFlag(context, entry.number)
                                            flagged = AppPrefs.flaggedNumbers(context)
                                        }
                                        GlassDropdownMenuItem("Delete", icon = Icons.Filled.Delete, accent = CallRed) {
                                            menuKey = null
                                            val ids = entry.ids
                                            ioScope.launch(Dispatchers.IO) { deleteCallLogEntries(context, ids) }
                                        }
                                        GlassDropdownMenuItem("Select", icon = Icons.Filled.CheckCircle, accent = AccentViolet) {
                                            menuKey = null
                                            selectMode = true
                                            selectedKeys = setOf(rowKey)
                                        }
                                    }
                                    }
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = selectMode && selectedKeys.isNotEmpty(),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    Row(
                        modifier = Modifier
                            .liquidGlass(shape = RoundedCornerShape(50), tint = CallRed, tintAlpha = 0.6f)
                            .pressScale(onClick = {
                                val ids = filtered.filter { (it.number + "_" + it.date) in selectedKeys }.flatMap { it.ids }
                                ioScope.launch(Dispatchers.IO) { deleteCallLogEntries(context, ids) }
                                selectMode = false
                                selectedKeys = emptySet()
                            })
                            .padding(horizontal = 28.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete", color = Color.White, fontSize = 16.sp)
                    }
                }

                if (!showDialpad && !selectMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(20.dp)
                            .size(56.dp)
                            .liquidGlass(shape = CircleShape, tint = CallGreen, tintAlpha = 0.75f)
                            .pressScale(onClick = { showDialpad = true }),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = "Dialpad", tint = Color.White)
                    }
                }

                AnimatedVisibility(
                    visible = showDialpad,
                    enter = slideInVertically(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                        initialOffsetY = { it }
                    ) + fadeIn(),
                    exit = slideOutVertically(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                        targetOffsetY = { it }
                    ) + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    EmbeddedDialpad(
                        onCall = { number ->
                            placeCall(context, number)
                            showDialpad = false
                        },
                        onClose = { showDialpad = false }
                    )
                }
            }
        }
    }
}

private data class DialKey(val digit: String, val sub: String)

private val DIAL_ROWS = listOf(
    listOf(DialKey("1", ""), DialKey("2", "ABC"), DialKey("3", "DEF")),
    listOf(DialKey("4", "GHI"), DialKey("5", "JKL"), DialKey("6", "MNO")),
    listOf(DialKey("7", "PQRS"), DialKey("8", "TUV"), DialKey("9", "WXYZ")),
    listOf(DialKey("*", " "), DialKey("0", "+"), DialKey("#", " "))
)

// Digit -> DTMF tone. Only the 12 real keys produce a tone.
private fun dtmfToneFor(digit: String): Int? = when (digit) {
    "0" -> ToneGenerator.TONE_DTMF_0
    "1" -> ToneGenerator.TONE_DTMF_1
    "2" -> ToneGenerator.TONE_DTMF_2
    "3" -> ToneGenerator.TONE_DTMF_3
    "4" -> ToneGenerator.TONE_DTMF_4
    "5" -> ToneGenerator.TONE_DTMF_5
    "6" -> ToneGenerator.TONE_DTMF_6
    "7" -> ToneGenerator.TONE_DTMF_7
    "8" -> ToneGenerator.TONE_DTMF_8
    "9" -> ToneGenerator.TONE_DTMF_9
    "*" -> ToneGenerator.TONE_DTMF_S
    "#" -> ToneGenerator.TONE_DTMF_P
    else -> null
}

private const val DTMF_TONE_MS_NORMAL = 100
private const val DTMF_TONE_MS_LONG = 300

object DialpadState {
    var value by mutableStateOf(TextFieldValue(""))
    var openRequested by mutableStateOf(false)

    fun open(number: String = "") {
        if (number.isNotEmpty()) value = TextFieldValue(number, TextRange(number.length))
        openRequested = true
    }
}

@Composable
private fun EmbeddedDialpad(onCall: (String) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val soundEnabled = remember { AppPrefs.dialpadSoundEnabled(context) }
    val toneMs = remember { if (AppPrefs.dtmfToneLength(context) == "Long") DTMF_TONE_MS_LONG else DTMF_TONE_MS_NORMAL }
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_DTMF, ToneGenerator.MAX_VOLUME) }
    DisposableEffect(Unit) { onDispose { toneGenerator.release() } }

    val hasNumber = DialpadState.value.text.isNotEmpty()
    val panelShape = RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp)
    val columnTints = listOf(AccentTeal, AccentIndigo, AccentPink)
    val underlineAlpha by animateFloatAsState(if (hasNumber) 1f else 0.25f, label = "dialpadUnderline")
    val callAlpha by animateFloatAsState(if (hasNumber) 1f else 0.55f, label = "dialpadCall")

    fun press(key: DialKey) {
        val v = DialpadState.value
        val newText = v.text.replaceRange(v.selection.start, v.selection.end, key.digit)
        DialpadState.value = TextFieldValue(newText, TextRange(v.selection.start + 1))
        if (soundEnabled) {
            dtmfToneFor(key.digit)?.let { toneGenerator.startTone(it, toneMs) }
        }
    }

    fun backspace() {
        val v = DialpadState.value
        if (v.selection.start != v.selection.end) {
            DialpadState.value = TextFieldValue(v.text.removeRange(v.selection.start, v.selection.end), TextRange(v.selection.start))
        } else if (v.selection.start > 0) {
            DialpadState.value = TextFieldValue(v.text.removeRange(v.selection.start - 1, v.selection.start), TextRange(v.selection.start - 1))
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.55f)
            .clip(panelShape)
            .background(Brush.verticalGradient(listOf(AccentViolet.copy(alpha = 0.14f), Color.Transparent)))
            .liquidGlass(shape = panelShape, tintAlpha = 0.32f)
            .border(
                2.dp,
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.7f), AccentIndigo.copy(alpha = 0.45f), AccentPink.copy(alpha = 0.55f))
                ),
                panelShape
            )
            // Swallows taps in the gaps between keys so they don't reach the list underneath.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 2.dp, bottom = 6.dp)
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.4f))
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.size(36.dp))
            BasicTextField(
                value = DialpadState.value,
                onValueChange = { new -> DialpadState.value = DialpadState.value.copy(selection = new.selection) },
                readOnly = true,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraLight,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.sp
                ),
                cursorBrush = SolidColor(AccentSoft),
                decorationBox = { inner ->
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (!hasNumber) {
                            Text("Enter number", color = Color.White.copy(alpha = 0.3f), fontSize = 20.sp, fontWeight = FontWeight.Light)
                        }
                        inner()
                    }
                },
                modifier = Modifier.weight(1f)
            )
            Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                if (hasNumber) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .liquidGlass(shape = CircleShape, tintAlpha = 0.2f)
                            .pressScale { backspace() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .padding(horizontal = 36.dp, vertical = 4.dp)
                .fillMaxWidth()
                .height(2.dp)
                .graphicsLayer { alpha = underlineAlpha }
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, AccentIndigo, AccentPink, Color.Transparent)))
        )
        DIAL_ROWS.forEach { row ->
            // Rows split whatever height is left; keys size off the row (capped),
            // so the layout always fits instead of clipping the bottom row.
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEachIndexed { column, key ->
                    val tint = columnTints[column]
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .heightIn(max = 64.dp)
                            .fillMaxHeight(0.94f)
                            .aspectRatio(1f, matchHeightConstraintsFirst = true)
                            .liquidGlass(shape = CircleShape, tint = tint, tintAlpha = 0.2f)
                            .border(
                                1.2.dp,
                                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.6f), tint.copy(alpha = 0.4f))),
                                CircleShape
                            )
                            .pressScale { press(key) }
                    ) {
                        Text(
                            key.digit,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Light,
                            color = Color.White,
                            modifier = if (key.digit == "*") Modifier.offset(y = 6.dp) else Modifier
                        )
                        Text(key.sub, fontSize = 10.sp, color = AccentSoft, letterSpacing = 1.sp)
                    }
                }
            }
        }
        // Left spacer mirrors the close chip so the call button sits dead centre.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.size(48.dp))
            Row(
                modifier = Modifier
                    .graphicsLayer { alpha = callAlpha }
                    .drawBehind {
                        if (hasNumber) {
                            val reach = size.maxDimension * 0.75f
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(CallGreen.copy(alpha = 0.40f), Color.Transparent),
                                    center = center,
                                    radius = reach
                                ),
                                radius = reach
                            )
                        }
                    }
                    .height(60.dp)
                    .widthIn(min = 150.dp)
                    .liquidGlass(shape = RoundedCornerShape(30.dp), tint = CallGreen, tintAlpha = 0.8f)
                    .pressScale(onClick = {
                        if (hasNumber) {
                            onCall(DialpadState.value.text)
                            DialpadState.value = TextFieldValue("")
                        }
                    })
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Call", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .liquidGlass(shape = CircleShape, tintAlpha = 0.35f)
                    .pressScale(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close dialpad", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}

// LI
