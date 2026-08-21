//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later).
// *
//**************************************************

// specific screen for Blacklist contacts. all side features will be included in one file in future. have some UI/UX issues, need to be fixed.

package com.iqstudio.dialer

import android.content.ContentUris
import android.content.Context
import android.provider.BlockedNumberContract
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BlockedEntry(val id: Long, val number: String)

private fun loadBlockedNumbers(context: Context): List<BlockedEntry> {
    if (!BlockedNumberContract.canCurrentUserBlockNumbers(context)) return emptyList()
    val entries = mutableListOf<BlockedEntry>()
    context.contentResolver.query(
        BlockedNumberContract.BlockedNumbers.CONTENT_URI,
        arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ID, BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER),
        null, null, null
    )?.use { cursor ->
        val idIdx = cursor.getColumnIndexOrThrow(BlockedNumberContract.BlockedNumbers.COLUMN_ID)
        val numberIdx = cursor.getColumnIndexOrThrow(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER)
        while (cursor.moveToNext()) {
            entries.add(BlockedEntry(cursor.getLong(idIdx), cursor.getString(numberIdx) ?: ""))
        }
    }
    return entries
}

private fun unblockNumber(context: Context, id: Long) {
    val uri = ContentUris.withAppendedId(BlockedNumberContract.BlockedNumbers.CONTENT_URI, id)
    context.contentResolver.delete(uri, null, null)
}

// ofc, you can't skip this! else everything will be useless anyways.

@Composable
fun BlocklistScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<BlockedEntry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        entries = withContext(Dispatchers.IO) { loadBlockedNumbers(context) }
        loaded = true
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(icon = Icons.Filled.ArrowBack, contentDescription = "Back", onClick = onBack)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Blocked numbers", fontSize = 20.sp, color = TextPrimary)
        }

        HorizontalDivider()

        if (!loaded) {
            Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        } else if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Text("No blocked numbers", color = TextSecondary, modifier = Modifier.align(Alignment.Center))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(entries) { entry ->
                    ListItem(
                        headlineContent = { Text(entry.number, color = TextPrimary) },
                        trailingContent = {
                            GlassIconButton(
                                icon = Icons.Filled.Delete,
                                contentDescription = "Unblock",
                                tint = CallRed,
                                onClick = {
                                    unblockNumber(context, entry.id)
                                    reloadKey++
                                }
                            )
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
