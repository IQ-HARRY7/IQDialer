//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later). 
// * 
//**************************************************

package com.iqstudio.dialer

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// recording & saving.
object CallRecordingManager {
    private const val TAG = "CallRecordingManager"

    private var recorder: MediaRecorder? = null
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    var lastFilePath: String? = null
        private set

    fun start(context: Context, label: String): Boolean {
        if (_isRecording.value) return true
        return try {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "CallRecordings")
            if (!dir.exists()) dir.mkdirs()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val safeLabel = label.replace(Regex("[^A-Za-z0-9]"), "_")
            val file = File(dir, safeLabel + "_" + timestamp + ".m4a")

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
            lastFilePath = file.absolutePath
            _isRecording.value = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "start failed", e)
            recorder = null
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
        _isRecording.value = false
    }
}
