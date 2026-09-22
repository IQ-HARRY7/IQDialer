//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later).
// *
//**************************************************

// in-app contact editor -- replaces the old system ACTION_EDIT handoff.
// needs so much improvements. 

package com.iqstudio.dialer

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class PhoneRow(val dataId: Long?, number: String, type: Int) {
    var number by mutableStateOf(number)
    var type by mutableStateOf(type)
}

private class EmailRow(val dataId: Long?, address: String, type: Int) {
    var address by mutableStateOf(address)
    var type by mutableStateOf(type)
}

private data class LoadedContact(
    val rawContactId: Long,
    val nameDataId: Long?,
    val displayName: String,
    val phones: List<PhoneRow>,
    val emails: List<EmailRow>
)

// Edits are written against a single raw contact -- the common case for a
// phone-local contact. A contact merged across multiple accounts would need
// per-raw-contact handling; not something this app's data model needs today.
private fun loadEditableContact(context: Context, contactId: Long): LoadedContact? {
    val resolver = context.contentResolver

    val rawContactId = resolver.query(
        ContactsContract.RawContacts.CONTENT_URI,
        arrayOf(ContactsContract.RawContacts._ID),
        ContactsContract.RawContacts.CONTACT_ID + " = ?",
        arrayOf(contactId.toString()),
        null
    )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null } ?: return null

    var nameDataId: Long? = null
    var displayName = ""
    resolver.query(
        ContactsContract.Data.CONTENT_URI,
        arrayOf(ContactsContract.Data._ID, ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME),
        ContactsContract.Data.CONTACT_ID + " = ? AND " + ContactsContract.Data.MIMETYPE + " = ?",
        arrayOf(contactId.toString(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE),
        null
    )?.use { c ->
        if (c.moveToFirst()) {
            nameDataId = c.getLong(0)
            displayName = c.getString(1) ?: ""
        }
    }

    val phones = mutableListOf<PhoneRow>()
    resolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Phone._ID, ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.TYPE),
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
        arrayOf(contactId.toString()),
        null
    )?.use { c ->
        while (c.moveToNext()) {
            phones.add(PhoneRow(c.getLong(0), c.getString(1) ?: "", c.getInt(2)))
        }
    }

    val emails = mutableListOf<EmailRow>()
    resolver.query(
        ContactsContract.CommonDataKinds.Email.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Email._ID, ContactsContract.CommonDataKinds.Email.ADDRESS, ContactsContract.CommonDataKinds.Email.TYPE),
        ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?",
        arrayOf(contactId.toString()),
        null
    )?.use { c ->
        while (c.moveToNext()) {
            emails.add(EmailRow(c.getLong(0), c.getString(1) ?: "", c.getInt(2)))
        }
    }

    return LoadedContact(rawContactId, nameDataId, displayName, phones, emails)
}

private fun saveContact(
    context: Context,
    loaded: LoadedContact,
    displayName: String,
    phones: List<PhoneRow>,
    emails: List<EmailRow>,
    deletedPhoneIds: List<Long>,
    deletedEmailIds: List<Long>
): Boolean {
    val ops = ArrayList<ContentProviderOperation>()

    if (displayName != loaded.displayName) {
        if (loaded.nameDataId != null) {
            ops.add(
                ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                    .withSelection(ContactsContract.Data._ID + " = ?", arrayOf(loaded.nameDataId.toString()))
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
                    .build()
            )
        } else {
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, loaded.rawContactId)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
                    .build()
            )
        }
    }

    phones.forEach { row ->
        if (row.dataId != null) {
            if (row.number.isBlank()) {
                ops.add(
                    ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                        .withSelection(ContactsContract.Data._ID + " = ?", arrayOf(row.dataId.toString()))
                        .build()
                )
            } else {
                ops.add(
                    ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                        .withSelection(ContactsContract.Data._ID + " = ?", arrayOf(row.dataId.toString()))
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, row.number)
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, row.type)
                        .build()
                )
            }
        } else if (row.number.isNotBlank()) {
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, loaded.rawContactId)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, row.number)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, row.type)
                    .build()
            )
        }
    }

    emails.forEach { row ->
        if (row.dataId != null) {
            if (row.address.isBlank()) {
                ops.add(
                    ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                        .withSelection(ContactsContract.Data._ID + " = ?", arrayOf(row.dataId.toString()))
                        .build()
                )
            } else {
                ops.add(
                    ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                        .withSelection(ContactsContract.Data._ID + " = ?", arrayOf(row.dataId.toString()))
                        .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, row.address)
                        .withValue(ContactsContract.CommonDataKinds.Email.TYPE, row.type)
                        .build()
                )
            }
        } else if (row.address.isNotBlank()) {
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, loaded.rawContactId)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, row.address)
                    .withValue(ContactsContract.CommonDataKinds.Email.TYPE, row.type)
                    .build()
            )
        }
    }

    deletedPhoneIds.forEach { id ->
        ops.add(
            ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                .withSelection(ContactsContract.Data._ID + " = ?", arrayOf(id.toString()))
                .build()
        )
    }
    deletedEmailIds.forEach { id ->
        ops.add(
            ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                .withSelection(ContactsContract.Data._ID + " = ?", arrayOf(id.toString()))
                .build()
        )
    }

    if (ops.isEmpty()) return true
    return try {
        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        true
    } catch (e: Exception) {
        false
    }
}

private val PHONE_TYPES = listOf(
    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
    ContactsContract.CommonDataKinds.Phone.TYPE_HOME,
    ContactsContract.CommonDataKinds.Phone.TYPE_WORK,
    ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
)

private val EMAIL_TYPES = listOf(
    ContactsContract.CommonDataKinds.Email.TYPE_HOME,
    ContactsContract.CommonDataKinds.Email.TYPE_WORK,
    ContactsContract.CommonDataKinds.Email.TYPE_OTHER
)

@Composable
private fun TypeDropdown(label: (Int) -> CharSequence, types: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Text(
            label(selected).toString(),
            color = TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.clickable { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            types.forEach { type ->
                DropdownMenuItem(
                    text = { Text(label(type).toString()) },
                    onClick = { onSelect(type); expanded = false }
                )
            }
        }
    }
}

@Composable
fun EditContactScreen(contactId: Long, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf<LoadedContact?>(null) }
    var displayName by remember { mutableStateOf("") }
    val phones = remember { mutableStateListOf<PhoneRow>() }
    val emails = remember { mutableStateListOf<EmailRow>() }
    val deletedPhoneIds = remember { mutableStateListOf<Long>() }
    val deletedEmailIds = remember { mutableStateListOf<Long>() }

    LaunchedEffect(contactId) {
        val result = withContext(Dispatchers.IO) { loadEditableContact(context, contactId) }
        if (result != null) {
            loaded = result
            displayName = result.displayName
            phones.clear(); phones.addAll(result.phones)
            emails.clear(); emails.addAll(result.emails)
        }
    }

    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(icon = Icons.Filled.Close, contentDescription = "Cancel", onClick = onBack)
            Text("Edit contact", fontSize = 18.sp, color = TextPrimary)
            GlassIconButton(
                icon = Icons.Filled.Check,
                contentDescription = "Save",
                onClick = {
                    val current = loaded ?: return@GlassIconButton
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) {
                            saveContact(context, current, displayName, phones, emails, deletedPhoneIds, deletedEmailIds)
                        }
                        if (saved) onSaved()
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                ContactAvatar(name = displayName.ifBlank { null }, size = 88.dp)
            }

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("Phone", fontSize = 13.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(4.dp))

            phones.forEachIndexed { index, row ->
                GlassCard(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TypeDropdown(
                            label = { ContactsContract.CommonDataKinds.Phone.getTypeLabel(context.resources, it, "") },
                            types = PHONE_TYPES,
                            selected = row.type,
                            onSelect = { row.type = it }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        OutlinedTextField(
                            value = row.number,
                            onValueChange = { row.number = it },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            row.dataId?.let { deletedPhoneIds.add(it) }
                            phones.removeAt(index)
                        }) {
                            Icon(Icons.Filled.RemoveCircle, contentDescription = "Remove number", tint = CallRed)
                        }
                    }
                }
            }
            TextButton(onClick = { phones.add(PhoneRow(null, "", ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)) }) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = CallBlue)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add phone", color = CallBlue)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Email", fontSize = 13.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(4.dp))

            emails.forEachIndexed { index, row ->
                GlassCard(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TypeDropdown(
                            label = { ContactsContract.CommonDataKinds.Email.getTypeLabel(context.resources, it, "") },
                            types = EMAIL_TYPES,
                            selected = row.type,
                            onSelect = { row.type = it }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        OutlinedTextField(
                            value = row.address,
                            onValueChange = { row.address = it },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            row.dataId?.let { deletedEmailIds.add(it) }
                            emails.removeAt(index)
                        }) {
                            Icon(Icons.Filled.RemoveCircle, contentDescription = "Remove email", tint = CallRed)
                        }
                    }
                }
            }
            TextButton(onClick = { emails.add(EmailRow(null, "", ContactsContract.CommonDataKinds.Email.TYPE_HOME)) }) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = CallBlue)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add email", color = CallBlue)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// end. end doesn't always means end.