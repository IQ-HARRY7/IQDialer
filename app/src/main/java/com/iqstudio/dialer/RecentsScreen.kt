//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later).
// *
//**************************************************

// 1st screen.
// working fine, but need to make it more smooth & animated.

package com.iqstudio.dialer

import android.provider.CallLog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.*

private data class GroupedEntry(
    val number: String,
    val name: String?,
    val type: Int,
    val date: Long,
    val count: Int
)

private fun groupConsecutive(entries: List<CallLogEntry>): List<GroupedEntry> {
    val result = mutableListOf<GroupedEntry>()
    for (entry in entries) {
        val last = result.lastOrNull()
        if (last != null && last.number == entry.number) {
            result[result.size - 1] = last.copy(count = last.count + 1)
        } else {
            result.add(GroupedEntry(entry.number, entry.name, entry.type, entry.date, 1))
        }
    }
    return result
}

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
    val rawEntriesOrNull by DataCache.callLog.collectAsState()
    LaunchedEffect(Unit) { DataCache.ensureLoaded(context) }
    val rawEntries = rawEntriesOrNull ?: emptyList()
    val loaded = rawEntriesOrNull != null
    var query by remember { mutableStateOf("") }
    var selectedNumber by remember { mutableStateOf<String?>(null) }
    var showDialpad by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val nested = showSettings || selectedNumber != null
    LaunchedEffect(nested) { onNestedScreenChange(nested) }
    DisposableEffect(Unit) { onDispose { onNestedScreenChange(false) } }

    val grouped = remember(rawEntries) { groupConsecutive(rawEntries) }
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
                                    if (rawEntries.isEmpty()) "No call history yet" else "No matches",
                                    color = TextSecondary,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                            else -> LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 88.dp)
                            ) {
                                items(filtered, key = { "${it.number}_${it.date}" }) { entry ->
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

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(20.dp)
                        .size(56.dp)
                        .liquidGlass(shape = CircleShape, tint = CallGreen, tintAlpha = 0.75f)
                        .pressScale(onClick = { showDialpad = !showDialpad }),
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(targetState = showDialpad, label = "fabIcon") { open ->
                        Icon(
                            if (open) Icons.Filled.Close else Icons.Filled.Call,
                            contentDescription = if (open) "Close dialpad" else "Dialpad",
                            tint = Color.White
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showDialpad,
                    enter = slideInVertically(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
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
                        }
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

@Composable
private fun EmbeddedDialpad(onCall: (String) -> Unit, modifier: Modifier = Modifier) {
    var number by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceCard, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            number.ifEmpty { " " },
            fontSize = 20.sp,
            color = TextPrimary,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
        )
        DIAL_ROWS.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { key ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(SurfaceCardHigh)
                            .pressScale { number += key.digit },
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(key.digit, fontSize = 19.sp, color = TextPrimary)
                        if (key.sub.isNotEmpty()) {
                            Text(key.sub, fontSize = 9.sp, color = TextSecondary)
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (number.isNotEmpty()) SurfaceCardHigh else Color.Transparent)
                    .then(
                        if (number.isNotEmpty()) Modifier.pressScale { number = number.dropLast(1) } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (number.isNotEmpty()) {
                    Text("\u232B", fontSize = 17.sp, color = TextSecondary)
                }
            }
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .liquidGlass(shape = CircleShape, tint = CallGreen, tintAlpha = 0.75f)
                    .pressScale(onClick = { if (number.isNotEmpty()) onCall(number) }),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Call, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

