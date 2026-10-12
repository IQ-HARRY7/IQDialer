//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later).
// *
//**************************************************

package com.iqstudio.dialer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Offers the latest unreviewed crash report after the app is reopened.
@Composable
fun CrashReportPrompt() {
    val context = LocalContext.current
    var report by remember { mutableStateOf<CrashReport?>(null) }

    LaunchedEffect(Unit) {
        report = withContext(Dispatchers.IO) { CrashReporter.pendingReport(context) }
    }

    val current = report ?: return

    fun close() {
        CrashReporter.acknowledge(context, current)
        report = null
    }

    AlertDialog(
        onDismissRequest = { close() },
        containerColor = SurfaceCard,
        title = { Text("IQ Dialer closed unexpectedly", color = TextPrimary) },
        text = {
            Column {
                Text(
                    "A crash report was saved on this device. Sharing it helps identify and fix the problem.",
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(current.summary, color = TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "IQ Dialer crash report")
                    putExtra(Intent.EXTRA_TEXT, current.text)
                }
                context.startActivity(Intent.createChooser(send, "Share crash report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                close()
            }) { Text("Share", color = AccentIndigo) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("IQ Dialer crash report", current.text))
                    Toast.makeText(context, "Report copied", Toast.LENGTH_SHORT).show()
                    close()
                }) { Text("Copy", color = AccentIndigo) }
                TextButton(onClick = { close() }) { Text("Dismiss", color = TextPrimary) }
            }
        }
    )
}
