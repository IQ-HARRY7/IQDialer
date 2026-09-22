//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later).
// *
//**************************************************

// 1st screen.
// working fine, but need to make it more smooth & animated.

package com.iqstudio.dialer

import android.media.AudioManager
import android.media.ToneGenerator
import android.provider.CallLog
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

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

// Which "screen" this composable is currently showing. Kept separate from
// the actual state (showSettings/selectedNumber, unchanged below) purely so
// AnimatedContent has a single value to diff -- doesn't change what
// triggers navigation, only how the transition between panes is rendered.
// NestedPane/nestedPaneTransitionSpec (UiComponents.kt) are shared by every
// nested-screen switch in the app so they all read as one consistent
// push/pop system instead of a per-screen one-off.
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

    val nested = showSettings || selectedNumber != null
    LaunchedEffect(nested) {
        onNestedScreenChange(nested)
        // Leaving the list pane (Settings, a contact) always closes the
        // dialpad -- it used to just sit open-but-buried and pop straight
        // back with no animation when you returned. The typed number
        // itself lives in DialpadState below, not here, so it survives
        // this close.
        if (nested) showDialpad = false
    }
    DisposableEffect(Unit) { onDispose { onNestedScreenChange(false) } }
    BackHandler(enabled = showDialpad) { showDialpad = false }

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
                        Text("Recents", fontSize = 26.sp)
                        GlassIconButton(
                            icon = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            onClick = { showSettings = true }
                        )
                    }

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search recents") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // The list's own text (names, dates) is what was showing
                    // through the dialpad and making it unreadable -- not a
                    // tintAlpha problem, since Liquid Glass at its normal
                    // settings reads fine over the plain background photo
                    // everywhere else in the app. It's hidden (not just
                    // covered) while the dialpad's open; the list isn't
                    // usable underneath it anyway, and this is what actually
                    // fixes legibility instead of guessing at opacity.
                    if (!showDialpad) {
                        val listState = when {
                            !loaded -> "loading"
                            filtered.isEmpty() -> "empty"
                            else -> "list"
                        }
                        Crossfade(targetState = listState, label = "recentsListState") { state ->
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
                                        GlassRow(
                                            onClick = { selectedNumber = entry.number },
                                            modifier = Modifier.animateItem()
                                        ) {
                                            ContactAvatar(name = entry.name)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    entry.name ?: entry.number,
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
                                    }
                                }
                            }
                        }
                    }
                }

                // FAB only exists to open the dialpad -- once it's open, the
                // panel's own close button (bottom-right inside it) is the
                // way out, so the FAB steps aside instead of sitting
                // uselessly underneath the (now much bigger) sheet.
                if (!showDialpad) {
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
                    // Same lesson as the tab-switch spec in MainActivity:
                    // StiffnessMediumLow reads as lag, not a spring.
                    // StiffnessMedium on both enter and exit keeps the
                    // up-when-opening/down-when-closing motion but settles
                    // fast instead of feeling buffered.
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
    listOf(DialKey("*", ""), DialKey("0", "+"), DialKey("#", ""))
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

private const val DTMF_TONE_MS = 100

// Keeps whatever's been typed on the dialpad across screen and tab
// switches -- RecentsScreen (and everything inside it) gets fully disposed
// and recomposed on every tab change, so a plain remember{} inside
// EmbeddedDialpad was wiped every time. This is a plain object, so it
// outlives that. Cleared once a call is actually placed.
private object DialpadState {
    var number by mutableStateOf("")
}

@Composable
private fun EmbeddedDialpad(onCall: (String) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val soundEnabled = remember { AppPrefs.dialpadSoundEnabled(context) }
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_DTMF, ToneGenerator.MAX_VOLUME) }
    DisposableEffect(Unit) { onDispose { toneGenerator.release() } }

    fun press(key: DialKey) {
        DialpadState.number += key.digit
        if (soundEnabled) {
            dtmfToneFor(key.digit)?.let { toneGenerator.startTone(it, DTMF_TONE_MS) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.55f)
            // Plain default Liquid Glass -- same as every other surface in
            // the app. The custom tint/opaque-base attempts from the last
            // two rounds were fighting the wrong problem: it's fine at
            // default settings once there's nothing text-heavy rendering
            // behind it (see the list-hiding change above).
            .liquidGlass(shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            // Absorbs any tap that lands in the gaps between keys instead of
            // letting it fall through to whatever's underneath (the Recents
            // list was catching those taps and opening a contact).
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        // Backspace lives next to the number itself, like the stock dialer --
        // not in the bottom control row -- and only shows once there's
        // something to delete.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                DialpadState.number.ifEmpty { " " },
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                if (DialpadState.number.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .pressScale { DialpadState.number = DialpadState.number.dropLast(1) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("\u232B", fontSize = 18.sp, color = TextSecondary)
                    }
                }
            }
        }
        DIAL_ROWS.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { key ->
                    // Flat, no button chip -- matches the reference photos
                    // exactly (plain text on black, no circular outline).
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .pressScale { press(key) }
                    ) {
                        Text(key.digit, fontSize = 30.sp, color = Color.White)
                        if (key.sub.isNotEmpty()) {
                            Text(key.sub, fontSize = 11.sp, color = TextSecondary, letterSpacing = 1.sp)
                        }
                    }
                }
            }
        }
        // Left slot stays empty and reserved so the call button lands
        // dead centre. Right: close -- lives here instead of only on the
        // outer FAB so it's always reachable no matter how tall this
        // panel is.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.size(44.dp))
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .liquidGlass(shape = CircleShape, tint = CallGreen, tintAlpha = 0.75f)
                    .pressScale(onClick = {
                        if (DialpadState.number.isNotEmpty()) {
                            onCall(DialpadState.number)
                            DialpadState.number = ""
                        }
                    }),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Call, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .pressScale(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close dialpad", tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
