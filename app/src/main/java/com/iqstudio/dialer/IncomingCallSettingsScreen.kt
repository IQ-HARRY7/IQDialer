//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later).
// *
//**************************************************

// Ringer and in-call behaviour toggles, all backed by AppPrefs.
package com.iqstudio.dialer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*

@Composable
fun IncomingCallSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    var flip by remember { mutableStateOf(AppPrefs.flipToSilence(context)) }
    var quiet by remember { mutableStateOf(AppPrefs.quietRingerWhenLifted(context)) }
    var increasing by remember { mutableStateOf(AppPrefs.increasingRingtone(context)) }
    var flash by remember { mutableStateOf(AppPrefs.flashWhenRinging(context)) }
    var proximity by remember { mutableStateOf(AppPrefs.proximitySensor(context)) }
    var muteFirst by remember { mutableStateOf(AppPrefs.muteFirstRing(context)) }

    val switchColors = glassSwitchColors()

    @Composable
    fun ToggleItem(icon: ImageVector, accent: Color, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { IconChip(icon, accent) },
            headlineContent = { Text(title, color = TextPrimary) },
            supportingContent = { Text(subtitle, color = TextSecondary) },
            trailingContent = { Switch(checked = checked, onCheckedChange = onChange, colors = switchColors) }
        )
    }

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
            Text("Incoming call settings", fontSize = 20.sp, color = TextPrimary)
        }

        Column(modifier = Modifier.padding(16.dp)) {
            GlassCard {
                ToggleItem(
                    Icons.Filled.ScreenRotation, AccentIndigo,
                    "Flip to silence ringer",
                    "When phone is on a horizontal surface with screen facing up, flip it over to silence ringer, turn off vibration, and stop flashing notification light.",
                    flip
                ) {
                    flip = it
                    AppPrefs.setFlipToSilence(context, it)
                }
                HorizontalDivider(color = OutlineFaint.copy(alpha = 0.3f))
                ToggleItem(
                    Icons.Filled.VolumeDown, AccentTeal,
                    "Quiet ringer when lifted",
                    "Reduce ringer volume for incoming call when phone is raised.",
                    quiet
                ) {
                    quiet = it
                    AppPrefs.setQuietRingerWhenLifted(context, it)
                }
                HorizontalDivider(color = OutlineFaint.copy(alpha = 0.3f))
                ToggleItem(
                    Icons.Filled.VolumeUp, AccentViolet,
                    "Increasing ringtone volume",
                    "Ringtone volume will gradually grow to the level you set",
                    increasing
                ) {
                    increasing = it
                    AppPrefs.setIncreasingRingtone(context, it)
                }
                HorizontalDivider(color = OutlineFaint.copy(alpha = 0.3f))
                ToggleItem(
                    Icons.Filled.FlashOn, AccentAmber,
                    "Flash when ringing",
                    "Use flash to notify about incoming calls",
                    flash
                ) {
                    flash = it
                    AppPrefs.setFlashWhenRinging(context, it)
                }
                HorizontalDivider(color = OutlineFaint.copy(alpha = 0.3f))
                ToggleItem(
                    Icons.Filled.Sensors, AccentPink,
                    "Proximity sensor",
                    "Turn off the screen automatically when you're holding the device to your ear during a call",
                    proximity
                ) {
                    proximity = it
                    AppPrefs.setProximitySensor(context, it)
                }
                HorizontalDivider(color = OutlineFaint.copy(alpha = 0.3f))
                ToggleItem(
                    Icons.Filled.NotificationsOff, AccentOrange,
                    "Mute first ring",
                    "Mute the first ring for calls from unknown numbers",
                    muteFirst
                ) {
                    muteFirst = it
                    AppPrefs.setMuteFirstRing(context, it)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ;