//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later).
// *
//**************************************************

// in future releases we will add all sub features into one.
package com.iqstudio.dialer

import android.app.role.RoleManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val roleManager = remember { context.getSystemService(RoleManager::class.java) }
    val isDefault = roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
    var showBlocklist by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var use24Hour by remember { mutableStateOf(AppPrefs.is24Hour(context)) }

    if (showBlocklist) {
        BlocklistScreen(onBack = { showBlocklist = false })
        return
    }
    if (showAdvanced) {
        AdvancedSettingsScreen(onBack = { showAdvanced = false })
        return
    }

    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = GlassTint,
        checkedBorderColor = GlassTint
    )

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
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    headlineContent = { Text("Default phone app", color = TextPrimary) },
                    supportingContent = { Text(if (isDefault) "IQ Dialer" else "Not set", color = TextSecondary) }
                )
                HorizontalDivider(color = OutlineFaint.copy(alpha = 0.3f))
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    modifier = Modifier.clickable { showBlocklist = true },
                    headlineContent = { Text("Blocked numbers", color = TextPrimary) },
                    supportingContent = { Text("Blocked numbers are logged but never ring", color = TextSecondary) }
                )
                HorizontalDivider(color = OutlineFaint.copy(alpha = 0.3f))
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    modifier = Modifier.clickable { showAdvanced = true },
                    headlineContent = { Text("Advanced settings", color = TextPrimary) },
                    supportingContent = { Text("Call screen background and more", color = TextSecondary) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            GlassCard {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
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
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
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
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    headlineContent = { Text("Per-contact ringtones", color = TextPrimary) },
                    supportingContent = { Text("Set from a contact's page (tap the number, then the menu)", color = TextSecondary) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            GlassCard {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    headlineContent = { Text("About", color = TextPrimary) },
                    supportingContent = { Text("IQ Dialer 0.1", color = TextSecondary) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
