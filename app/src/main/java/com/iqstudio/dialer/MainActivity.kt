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
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

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

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.History, contentDescription = "Recents") },
                    label = { Text("Recents") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.Person, contentDescription = "Contacts") },
                    label = { Text("Contacts") }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Crossfade(targetState = selectedTab, label = "tabSwitch") { tab ->
                if (tab == 0) RecentsScreen(refreshKey) else ContactsScreen()
            }
        }
    }
}

// for now, working. must fix the loop> thing later. 

// Sure! if you believe all code is AI Generated the There's nothing i can say. Get a brain 😐🧠
