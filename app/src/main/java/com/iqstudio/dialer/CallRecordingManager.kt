//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later). 
// * 
//**************************************************

package com.iqstudio.dialer

import android.content.ContentValues
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// recording & saving. Files live in the app's private data dir -- no file manager can browse there.

// privacy at your hands

object CallRecordingManager {
    private const val TAG = "CallRecordingManager"

    private var recorder: MediaRecorder? = null
    private var owner: Any? = null
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    var lastFilePath: String? = null
        private set

    private fun dir(context: Context): File =
        File(context.filesDir, "call_recordings").also { if (!it.exists()) it.mkdirs() }

    fun recordings(context: Context): List<File> =
        dir(context).listFiles { f -> f.isFile && f.extension == "m4a" }?.sortedBy { it.lastModified() } ?: emptyList()

    fun start(context: Context, label: String, ownerCall: Any? = null): Boolean {
        if (_isRecording.value) return true
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val safeLabel = label.replace(Regex("[^A-Za-z0-9]"), "_")
            val file = File(dir(context), safeLabel + "_" + timestamp + ".m4a")

            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()

            recorder = mr
            owner = ownerCall
            lastFilePath = file.absolutePath
            _isRecording.value = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "start failed", e)
            recorder = null
            owner = null
            _isRecording.value = false
            false
        }
    }

    fun stop() {
        try {
            recorder?.stop()
            recorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "stop failed", e)
        }
        recorder = null
        owner = null
        _isRecording.value = false
    }

    fun stopFor(ownerCall: Any) {
        if (_isRecording.value && owner === ownerCall) stop()
    }

    // Opt-in export from Advanced settings: copies, never moves, so the private originals stay.
    
    // I know I'll receive recommendations regarding this, so I've added it already. it's upto you ✌️   
    
    fun exportToStorage(context: Context): Int {
        var copied = 0
        val relative = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Environment.DIRECTORY_RECORDINGS + "/IQDialer"
        } else {
            Environment.DIRECTORY_MUSIC + "/IQDialer"
        }
        recordings(context).forEach { file ->
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, relative)
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return@forEach
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                copied++
            } catch (e: Exception) {
                Log.e(TAG, "export failed for " + file.name, e)
            }
        }
        return copied
    }
}


// So much unemployment 
