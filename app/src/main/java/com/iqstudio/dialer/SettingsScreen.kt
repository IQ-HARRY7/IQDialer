//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later).
// *
//**************************************************

// in future releases we will add all sub features into one.
package com.iqstudio.dialer

import android.app.role.RoleManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

// See RecentsScreen.kt for the same pattern 
private sealed interface SettingsPane : NestedPane {
    object Main : SettingsPane { override val paneDepth = 0 }
    object Blocklist : SettingsPane { override val paneDepth = 1 }
    object Advanced : SettingsPane { override val paneDepth = 1 }
    object Incoming : SettingsPane { override val paneDepth = 1 }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val roleManager = remember { context.getSystemService(RoleManager::class.java) }
    val isDefault = roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
    var showBlocklist by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var showIncoming by remember { mutableStateOf(false) }
    var aboutTaps by remember { mutableIntStateOf(0) }
    var lastAboutTap by remember { mutableLongStateOf(0L) }
    var aboutToast by remember { mutableStateOf<Toast?>(null) }
    val haptics = LocalHapticFeedback.current
    var use24Hour by remember { mutableStateOf(AppPrefs.is24Hour(context)) }

    val pane: SettingsPane = when {
        showBlocklist -> SettingsPane.Blocklist
        showAdvanced -> SettingsPane.Advanced
        showIncoming -> SettingsPane.Incoming
        else -> SettingsPane.Main
    }

    AnimatedContent(
        targetState = pane,
        transitionSpec = { nestedPaneTransitionSpec() },
        label = "settingsPane"
    ) { currentPane ->
        when (currentPane) {
            SettingsPane.Blocklist -> BlocklistScreen(onBack = { showBlocklist = false })
            SettingsPane.Advanced -> AdvancedSettingsScreen(onBack = { showAdvanced = false })
            SettingsPane.Incoming -> IncomingCallSettingsScreen(onBack = { showIncoming = false })
            SettingsPane.Main -> {
                BackHandler(onBack = onBack)

                val switchColors = glassSwitchColors()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassIconButton(icon = Icons.Filled.ArrowBack, contentDescription = "Back", onClick = onBack)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Settings", fontSize = 20.sp, color = TextPrimary)
                    }

                    Column(modifier = Modifier.padding(16.dp)) {
                        GlassCard {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                leadingContent = { IconChip(Icons.Filled.Phone, AccentTeal) },
                                headlineContent = { Text("Default phone app", color = TextPrimary) },
                                supportingContent = { Text(if (isDefault) "IQ Dialer" else "Not set", color = TextSecondary) }
                            )
                            HorizontalDivider(color = OutlineFaint.copy(alpha = 0.3f))
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable { showBlocklist = true },
                                leadingContent = { IconChip(Icons.Filled.Block, CallRed) },
                                headlineContent = { Text("Blocked numbers", color = TextPrimary) },
                                supportingContent = { Text("Blocked numbers are logged but never ring", color = TextSecondary) }
                            )
                            HorizontalDivider(color = OutlineFaint.copy(alpha = 0.3f))
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable { showIncoming = true },
                                leadingContent = { IconChip(Icons.Filled.RingVolume, AccentIndigo) },
                                headlineContent = { Text("Incoming call settings", color = TextPrimary) },
                                supportingContent = { Text("Ringer, flash, proximity and more", color = TextSecondary) }
                            )
                            HorizontalDivider(color = OutlineFaint.copy(alpha = 0.3f))
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable { showAdvanced = true },
                                leadingContent = { IconChip(Icons.Filled.Tune, AccentViolet) },
                                headlineContent = { Text("Advanced settings", color = TextPrimary) },
                                supportingContent = { Text("Dial pad, calls, backgrounds and more", color = TextSecondary) }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        GlassCard {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                leadingContent = { IconChip(Icons.Filled.Schedule, AccentAmber) },
                                headlineContent = { Text("24-hour time", color = TextPrimary) },
                                supportingContent = { Text("Applies to Recents and call history", color = TextSecondary) },
                                trailingContent = {
                                    Switch(
                                        checked = use24Hour,
                                        onCheckedChange = {
                                            use24Hour = it
                                            AppPrefs.set24Hour(context, it)
                                        },
                                        colors = switchColors
                                    )
                                }
                            )
                            HorizontalDivider(color = OutlineFaint.copy(alpha = 0.3f))
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                leadingContent = { IconChip(Icons.Filled.FiberManualRecord, CallRed) },
                                headlineContent = { Text("Call recording", color = TextPrimary) },
                                supportingContent = {
                                    Text(
                                        "Available as a button during calls. Best-effort -- quality depends on this device's hardware.",
                                        color = TextSecondary
                                    )
                                }
                            )
                            HorizontalDivider(color = OutlineFaint.copy(alpha = 0.3f))
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                leadingContent = { IconChip(Icons.Filled.MusicNote, AccentPink) },
                                headlineContent = { Text("Per-contact ringtones", color = TextPrimary) },
                                supportingContent = { Text("Set from a contact's page (tap the number, then the menu)", color = TextSecondary) }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        GlassCard {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable {
                                    val now = System.currentTimeMillis()
                                    if (now - lastAboutTap > 500L) aboutTaps = 0
                                    lastAboutTap = now
                                    aboutTaps++
                                    if (aboutTaps > 3) {
                                        aboutToast?.cancel()
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        context.startActivity(Intent(context, InfiniteScanActivity::class.java))
                                        aboutTaps = 0
                                    } else {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        aboutToast?.cancel()
                                        aboutToast = Toast.makeText(context, "Tap again", Toast.LENGTH_SHORT).also { it.show() }
                                    }
                                },
                                leadingContent = { IconChip(Icons.Filled.Info, AccentViolet) },
                                headlineContent = { Text("About", color = TextPrimary) },
                                supportingContent = { Text("IQ Dialer V-2.0 - TAP TAP TAP TAP TAP...... ", color = TextSecondary) }
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

// !;
