//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later). 
// * 
//**************************************************

// splash screen - soon will be replaced with new one. 
package com.iqstudio.dialer

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat

private fun requiredPermissions(): Array<String> {
    val base = mutableListOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.RECORD_AUDIO
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        base.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    return base.toTypedArray()
}

private fun hasAllPermissions(context: Context): Boolean =
    requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IQDialerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SplashScreen(onReady = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    })
                }
            }
        }
    }
}

@Composable
fun SplashScreen(onReady: () -> Unit) {
    val context = LocalContext.current
    val roleManager = remember { context.getSystemService(RoleManager::class.java) }

    fun isDefaultDialer() = roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
    fun hasOverlayPermission() = Settings.canDrawOverlays(context)

    var permissionsGranted by remember { mutableStateOf(hasAllPermissions(context)) }
    var isDefault by remember { mutableStateOf(isDefaultDialer()) }
    var hasOverlay by remember { mutableStateOf(hasOverlayPermission()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsGranted = hasAllPermissions(context) }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { isDefault = isDefaultDialer() }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { hasOverlay = hasOverlayPermission() }

    LaunchedEffect(permissionsGranted, isDefault, hasOverlay) {
        if (permissionsGranted && isDefault && hasOverlay) onReady()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("IQ Dialer needs a few things", fontSize = 22.sp, modifier = Modifier.padding(bottom = 32.dp))

        if (!isDefault) {
            Button(
                onClick = {
                    val intent = roleManager?.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                    if (intent != null) roleLauncher.launch(intent)
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) { Text("Set as default phone app") }
        } else {
            Text("Default phone app: done", modifier = Modifier.padding(bottom = 12.dp))
        }

        if (!permissionsGranted) {
            Button(
                onClick = { permissionLauncher.launch(requiredPermissions()) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) { Text("Grant permissions") }
        } else {
            Text("Permissions: done", modifier = Modifier.padding(bottom = 12.dp))
        }

        if (!hasOverlay) {
            Button(
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + context.packageName)
                    )
                    overlayLauncher.launch(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Allow floating call bubble") }
        } else {
            Text("Call bubble: done")
        }
    }
}


// anyways nothing to explain, it will modified soon. 