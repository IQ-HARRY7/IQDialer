//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later).
// *
//**************************************************

// Second interface of Dialer. <set view>

package com.iqstudio.dialer

import android.content.Context
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ContactEntry(val id: Long, val name: String, val number: String?)

private fun loadContacts(context: Context): List<ContactEntry> {
    if (!hasContactsPermission(context)) return emptyList()
    val entries = mutableListOf<ContactEntry>()
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER
    )
    context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        projection,
        null, null,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
    )?.use { cursor ->
        val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
        val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numberIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val seenIds = HashSet<Long>()
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idIdx)
            if (seenIds.add(id)) {
                entries.add(
                    ContactEntry(
                        id = id,
                        name = cursor.getString(nameIdx) ?: "Unknown",
                        number = cursor.getString(numberIdx)
                    )
                )
            }
        }
    }
    return entries
}

@Composable
fun ContactsScreen(onNestedScreenChange: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    var contacts by remember { mutableStateOf<List<ContactEntry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selectedNumber by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    val nested = showSettings || selectedNumber != null
    LaunchedEffect(nested) { onNestedScreenChange(nested) }
    DisposableEffect(Unit) { onDispose { onNestedScreenChange(false) } }

    LaunchedEffect(Unit) {
        contacts = withContext(Dispatchers.IO) { loadContacts(context) }
        loaded = true
    }

    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
        return
    }

    selectedNumber?.let { number ->
        ContactDetailScreen(phoneNumber = number, onBack = { selectedNumber = null })
        return
    }

    val filtered = remember(contacts, query) {
        if (query.isBlank()) contacts
        else contacts.filter {
            it.name.contains(query, ignoreCase = true) ||
                (it.number?.contains(query, ignoreCase = true) == true)
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
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

        if (!loaded) {
            Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        } else if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    if (contacts.isEmpty()) "No contacts found" else "No matches",
                    color = TextSecondary,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered) { contact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 3.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { if (contact.number != null) selectedNumber = contact.number }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
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
                    HorizontalDivider(color = OutlineFaint.copy(alpha = 0.3f))
                }
            }
        }
    }
}
