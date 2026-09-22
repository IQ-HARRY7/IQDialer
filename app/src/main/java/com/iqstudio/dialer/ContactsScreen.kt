//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later).
// *
//**************************************************

// Second screen - ✌️
// Second interface of Dialer. <set view>

package com.iqstudio.dialer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*

data class ContactEntry(val id: Long, val name: String, val number: String?)

// See RecentsScreen.kt for the same pattern with more panes -- NestedPane
// and nestedPaneTransitionSpec (UiComponents.kt) are shared so every
// nested-screen switch in the app reads as one consistent push/pop system.
private sealed interface ContactsPane : NestedPane {
    object List : ContactsPane { override val paneDepth = 0 }
    object Settings : ContactsPane { override val paneDepth = 1 }
    data class Contact(val number: String) : ContactsPane { override val paneDepth = 1 }
}

@Composable
fun ContactsScreen(onNestedScreenChange: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val contactsOrNull by DataCache.contacts.collectAsState()
    LaunchedEffect(Unit) { DataCache.ensureLoaded(context) }
    val contacts = contactsOrNull ?: emptyList()
    val loaded = contactsOrNull != null
    var query by remember { mutableStateOf("") }
    var selectedNumber by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    val nested = showSettings || selectedNumber != null
    LaunchedEffect(nested) { onNestedScreenChange(nested) }
    DisposableEffect(Unit) { onDispose { onNestedScreenChange(false) } }

    val filtered = remember(contacts, query) {
        if (query.isBlank()) contacts
        else contacts.filter {
            it.name.contains(query, ignoreCase = true) ||
                (it.number?.contains(query, ignoreCase = true) == true)
        }
    }

    val pane: ContactsPane = when {
        showSettings -> ContactsPane.Settings
        selectedNumber != null -> ContactsPane.Contact(selectedNumber!!)
        else -> ContactsPane.List
    }

    AnimatedContent(
        targetState = pane,
        transitionSpec = { nestedPaneTransitionSpec() },
        label = "contactsPane"
    ) { currentPane ->
        when (currentPane) {
            ContactsPane.Settings -> SettingsScreen(onBack = { showSettings = false })
            is ContactsPane.Contact -> ContactDetailScreen(phoneNumber = currentPane.number, onBack = { selectedNumber = null })
            ContactsPane.List -> Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Contacts", fontSize = 26.sp)
                    GlassIconButton(
                        icon = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        onClick = { showSettings = true }
                    )
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search contacts") },
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
                Crossfade(targetState = listState, label = "contactsListState") { state ->
                    when (state) {
                        "loading" -> Box(modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                        "empty" -> Box(modifier = Modifier.fillMaxSize()) {
                            Text(
                                if (contacts.isEmpty()) "No contacts found" else "No matches",
                                color = TextSecondary,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filtered, key = { it.id }) { contact ->
                                GlassRow(
                                    onClick = { if (contact.number != null) selectedNumber = contact.number },
                                    modifier = Modifier.animateItem()
                                ) {
                                    ContactAvatar(name = contact.name)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(contact.name, fontSize = 16.sp, color = TextPrimary)
                                        if (contact.number != null) {
                                            Text(contact.number, fontSize = 13.sp, color = TextSecondary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

