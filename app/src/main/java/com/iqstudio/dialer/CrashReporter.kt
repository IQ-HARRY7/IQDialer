//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later).
// *
//**************************************************

package com.iqstudio.dialer

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

data class CrashReport(val file: File, val time: Long, val text: String, val summary: String)

// Persists crash reports to private storage so they survive the process dying.
// Java and Kotlin exceptions are written by the uncaught-exception handler.
// Native crashes and ANRs cannot be caught in-process, so they are read from the
// system's process exit history on the next launch (Android 11+).
object CrashReporter {
    private const val TAG = "CrashReporter"
    private const val DIR_NAME = "crash_reports"
    private const val MAX_REPORTS = 10
    private const val MAX_BREADCRUMBS = 40
    private const val MAX_REPORT_CHARS = 100_000

    private val installed = AtomicBoolean(false)
    private val handling = AtomicBoolean(false)
    private val breadcrumbs = ArrayDeque<String>()

    fun install(app: Application) {
        if (!installed.compareAndSet(false, true)) return
        app.registerActivityLifecycleCallbacks(lifecycleLogger)

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (handling.compareAndSet(false, true)) {
                try {
                    saveCrash(app, thread, throwable)
                } catch (t: Throwable) {
                    Log.e(TAG, "Could not save crash report", t)
                }
            }
            Log.e(TAG, "Uncaught exception on thread " + thread.name, throwable)
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }

        Thread({ collectProcessExits(app) }, "crash-exit-scan").start()
    }

    fun breadcrumb(message: String) {
        synchronized(breadcrumbs) {
            if (breadcrumbs.size >= MAX_BREADCRUMBS) breadcrumbs.removeFirst()
            breadcrumbs.addLast(clockTime(System.currentTimeMillis()) + "  " + message)
        }
    }

    fun pendingReport(context: Context): CrashReport? {
        val acknowledged = AppPrefs.crashAcknowledged(context)
        val file = reportsDir(context).listFiles()
            ?.filter { it.isFile && it.lastModified() > acknowledged }
            ?.maxByOrNull { it.lastModified() }
            ?: return null
        val text = try {
            file.readText()
        } catch (e: Exception) {
            return null
        }
        return CrashReport(file, file.lastModified(), text, summarize(text))
    }

    fun acknowledge(context: Context, report: CrashReport) {
        val newest = reportsDir(context).listFiles()?.maxOfOrNull { it.lastModified() } ?: report.time
        AppPrefs.setCrashAcknowledged(context, maxOf(newest, report.time))
    }

    private val lifecycleLogger = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) =
            breadcrumb(activity.javaClass.simpleName + " created")

        override fun onActivityStarted(activity: Activity) {}

        override fun onActivityResumed(activity: Activity) =
            breadcrumb(activity.javaClass.simpleName + " resumed")

        override fun onActivityPaused(activity: Activity) =
            breadcrumb(activity.javaClass.simpleName + " paused")

        override fun onActivityStopped(activity: Activity) {}

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

        override fun onActivityDestroyed(activity: Activity) =
            breadcrumb(activity.javaClass.simpleName + " destroyed")
    }

    private fun saveCrash(context: Context, thread: Thread, throwable: Throwable) {
        val now = System.currentTimeMillis()
        val body = buildString {
            append("IQ Dialer crash report\n")
            append(header(context, now))
            append("Thread: ").append(thread.name).append(" (id ").append(thread.id).append(")\n")
            append("Exception: ").append(throwable.javaClass.name)
            throwable.message?.let { append(": ").append(it) }
            append("\n\nStack trace:\n")
            append(Log.getStackTraceString(throwable))
            append("\nRecent activity:\n")
            synchronized(breadcrumbs) {
                breadcrumbs.forEach { append("  ").append(it).append('\n') }
            }
        }
        writeReport(context, "crash", now, body)
    }

    private fun collectProcessExits(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val lastCheck = AppPrefs.lastExitCheck(context)
            val now = System.currentTimeMillis()
            if (lastCheck == 0L) {
                AppPrefs.setLastExitCheck(context, now)
                return
            }
            val manager = context.getSystemService(ActivityManager::class.java) ?: return
            var newest = lastCheck
            manager.getHistoricalProcessExitReasons(context.packageName, 0, 10)
                .filter { it.timestamp > lastCheck }
                .forEach { info ->
                    newest = maxOf(newest, info.timestamp)
                    when (info.reason) {
                        ApplicationExitInfo.REASON_ANR -> writeExitReport(context, info, "ANR (app not responding)", true)
                        ApplicationExitInfo.REASON_CRASH_NATIVE -> writeExitReport(context, info, "Native crash", false)
                        else -> Unit
                    }
                }
            AppPrefs.setLastExitCheck(context, newest)
        } catch (t: Throwable) {
            Log.e(TAG, "Exit history scan failed", t)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun writeExitReport(context: Context, info: ApplicationExitInfo, label: String, includeTrace: Boolean) {
        val body = buildString {
            append("IQ Dialer process exit report\n")
            append(header(context, info.timestamp))
            append("Reason: ").append(label).append('\n')
            append("Description: ").append(info.description ?: "none").append('\n')
            append("Process: ").append(info.processName).append(" (pid ").append(info.pid).append(")\n")
            if (includeTrace) {
                val trace = try {
                    info.traceInputStream?.use { it.bufferedReader().readText() }
                } catch (e: Exception) {
                    null
                }
                append("\nTrace:\n").append(trace ?: "not available").append('\n')
            }
        }
        writeReport(context, if (includeTrace) "anr" else "native", info.timestamp, body)
    }

    private fun header(context: Context, time: Long): String {
        val info = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) {
            null
        }
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return buildString {
            append("Time: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date(time))).append('\n')
            append("Version: ").append(info?.versionName ?: "unknown")
                .append(" (").append(info?.longVersionCode ?: 0L).append(")\n")
            append("Build: ").append(if (debuggable) "debug" else "release").append('\n')
            append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            append("Interface: ").append(if (VisualMode.advanced) "advanced" else "minimal").append('\n')
            append("Process: ").append(Application.getProcessName()).append('\n')
        }
    }

    private fun writeReport(context: Context, kind: String, time: Long, body: String) {
        val dir = reportsDir(context)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(time))
        val file = File(dir, kind + "_" + stamp + ".txt")
        val text = if (body.length > MAX_REPORT_CHARS) body.take(MAX_REPORT_CHARS) + "\n[truncated]\n" else body
        FileOutputStream(file).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        file.setLastModified(time)
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(MAX_REPORTS)?.forEach { it.delete() }
    }

    private fun reportsDir(context: Context): File =
        File(context.filesDir, DIR_NAME).also { if (!it.exists()) it.mkdirs() }

    private fun clockTime(time: Long): String =
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(time))

    private fun summarize(text: String): String =
        text.lineSequence()
            .filter { it.startsWith("Exception: ") || it.startsWith("Reason: ") || it.startsWith("Version: ") || it.startsWith("Time: ") }
            .take(4)
            .joinToString("\n")
}
