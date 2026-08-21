// This was the hardest part, took 3 weeks, to debug & implement everything. @IQ_HARRY_07

//****************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later).
// *
//****************************************************

package com.iqstudio.dialer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
   // resume/continue/ different than java 🫩 - debugging.
    private var resumeTrigger by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IQDialerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(refreshKey = resumeTrigger)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTrigger++
    }
}

@Composable
fun MainScreen(refreshKey: Int) {
    var selectedTab by remember { mutableStateOf(0) }
    var isNested by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().padding(bottom = 88.dp)) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(spring(stiffness = Spring.StiffnessMedium))
                        .togetherWith(fadeOut(spring(stiffness = Spring.StiffnessMedium)))
                },
                label = "tabSwitch"
            ) { tab ->
                if (tab == 0) {
                    RecentsScreen(refreshKey, onNestedScreenChange = { isNested = it })
                } else {
                    ContactsScreen(onNestedScreenChange = { isNested = it })
                }
            }
        }

        if (!isNested) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .height(64.dp)
                    .liquidGlass(shape = RoundedCornerShape(32.dp)),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FloatingNavItem(
                    icon = Icons.Filled.History,
                    label = "Recents",
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                FloatingNavItem(
                    icon = Icons.Filled.Person,
                    label = "Contacts",
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
            }
        }
    }
}

@Composable
private fun RowScope.FloatingNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .weight(1f)
            .padding(6.dp)
            .clip(RoundedCornerShape(24.dp))
            .then(
                if (selected) Modifier.background(GlassTint) else Modifier
            )
            .pressScale(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) Color.White else TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        if (selected) {
            Spacer(Modifier.width(6.dp))
            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
