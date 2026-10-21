package com.zevbuild.zevblock.core.diagnostics

import android.content.Context
import android.content.SharedPreferences
import android.os.Process
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

data class DiagnosticLogEntry(
    val id: Long = System.currentTimeMillis(),
    val timestampFormatted: String,
    val source: String,
    val summary: String,
    val stackTrace: String,
    val autoRecovered: Boolean = true
)

/**
 * CrashGuardManager:
 * - Installs a global uncaught exception safeguard & coroutine exception handler.
 * - Captures and persists background/service exceptions and auto-recovery events.
 * - Tracks cold/warm app launch speed and JVM memory health for the Tools diagnostic console.
 */
object CrashGuardManager {

    private const val TAG = "CrashGuardManager"
    private const val PREFS_NAME = "zevblock_crash_guard_prefs"
    private const val KEY_LOGS_JSON = "diagnostic_logs_json"
    private const val MAX_DIAGNOSTIC_LOGS = 25

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var isInstalled = false
    private var appContext: Context? = null
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    private val _diagnosticLogs = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
    val diagnosticLogs: StateFlow<List<DiagnosticLogEntry>> = _diagnosticLogs.asStateFlow()

    private val _lastStartupDurationMs = MutableStateFlow(12L)
    val lastStartupDurationMs: StateFlow<Long> = _lastStartupDurationMs.asStateFlow()

    private val _autoRecoveryCount = MutableStateFlow(0)
    val autoRecoveryCount: StateFlow<Int> = _autoRecoveryCount.asStateFlow()

    private val processStartElapsedMs = SystemClock.elapsedRealtime()

    fun install(context: Context) {
        if (isInstalled) return
        synchronized(this) {
            if (isInstalled) return
            appContext = context.applicationContext
            defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                handleUncaughtException(thread, throwable)
            }

            isInstalled = true
            ioScope.launch {
                loadPersistedLogs()
            }
        }
    }

    fun recordStartupComplete(activityStartElapsedMs: Long) {
        val duration = (SystemClock.elapsedRealtime() - activityStartElapsedMs).coerceAtLeast(1L)
        _lastStartupDurationMs.value = duration
        Log.i(TAG, "MainActivity interactive startup completed in ${duration}ms")
    }

    fun coroutineExceptionHandler(source: String): CoroutineExceptionHandler {
        return CoroutineExceptionHandler { _, throwable ->
            recordHandledException(source, throwable, autoRecovered = true)
        }
    }

    fun recordHandledException(
        source: String,
        throwable: Throwable,
        autoRecovered: Boolean = true
    ) {
        Log.e(TAG, "CrashGuard intercepted error in [$source]: ${throwable.message}", throwable)
        val entry = DiagnosticLogEntry(
            timestampFormatted = formatTimestamp(System.currentTimeMillis()),
            source = source,
            summary = "${throwable.javaClass.simpleName}: ${throwable.message ?: "Unexpected error"}",
            stackTrace = extractShortStackTrace(throwable),
            autoRecovered = autoRecovered
        )
        appendEntry(entry)
    }

    fun recordServiceRecovery(source: String, detail: String) {
        Log.i(TAG, "CrashGuard service recovery [$source]: $detail")
        val entry = DiagnosticLogEntry(
            timestampFormatted = formatTimestamp(System.currentTimeMillis()),
            source = source,
            summary = detail,
            stackTrace = "Auto-recovered safely without interrupting user session.",
            autoRecovered = true
        )
        appendEntry(entry)
    }

    fun clearLogs() {
        _diagnosticLogs.value = emptyList()
        _autoRecoveryCount.value = 0
        ioScope.launch {
            getPrefs()?.edit()?.remove(KEY_LOGS_JSON)?.apply()
        }
    }

    fun getMemorySummaryMb(): String {
        return try {
            val runtime = Runtime.getRuntime()
            val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            val maxMb = runtime.maxMemory() / (1024 * 1024)
            "${usedMb}MB / ${maxMb}MB"
        } catch (e: Exception) {
            "Normal"
        }
    }

    fun buildDiagnosticReport(): String {
        val logs = _diagnosticLogs.value
        return buildString {
            appendLine("=== ZevBlock Crash Guard & Health Report ===")
            appendLine("Startup Time: ${_lastStartupDurationMs.value} ms")
            appendLine("Memory Usage: ${getMemorySummaryMb()}")
            appendLine("Auto-Recoveries: ${_autoRecoveryCount.value}")
            appendLine("Recorded Events (${logs.size}):")
            if (logs.isEmpty()) {
                appendLine("No crashes or service errors recorded. All systems healthy.")
            } else {
                for (item in logs) {
                    appendLine("- [${item.timestampFormatted}] [${item.source}] ${item.summary}")
                    appendLine("  ${item.stackTrace.replace("\n", " | ")}")
                }
            }
        }
    }

    private fun handleUncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val isMainThread = thread.name == "main" || thread.id == 1L
            recordHandledException(
                source = "Thread:${thread.name}",
                throwable = throwable,
                autoRecovered = !isMainThread
            )
            // Persist synchronously if possible before exit
            saveLogsNow(_diagnosticLogs.value)

            // If a background worker thread threw an uncaught exception, swallow and auto-recover
            // so the user's foreground Activity never crashes!
            if (!isMainThread) {
                Log.w(TAG, "Swallowed uncaught background thread exception on ${thread.name} to prevent app crash.")
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inside CrashGuard uncaught handler", e)
        }

        // Delegate fatal main-thread errors to default OS handler or terminate cleanly
        val fallback = defaultHandler
        if (fallback != null) {
            fallback.uncaughtException(thread, throwable)
        } else {
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    private fun appendEntry(entry: DiagnosticLogEntry) {
        val updated = listOf(entry) + _diagnosticLogs.value.take(MAX_DIAGNOSTIC_LOGS - 1)
        _diagnosticLogs.value = updated
        if (entry.autoRecovered) {
            _autoRecoveryCount.value = updated.count { it.autoRecovered }
        }
        ioScope.launch {
            saveLogsNow(updated)
        }
    }

    private fun loadPersistedLogs() {
        try {
            val prefs = getPrefs() ?: return
            val raw = prefs.getString(KEY_LOGS_JSON, null) ?: return
            val arr = JSONArray(raw)
            val list = ArrayList<DiagnosticLogEntry>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(
                    DiagnosticLogEntry(
                        id = obj.optLong("id", System.currentTimeMillis() + i),
                        timestampFormatted = obj.optString("ts", ""),
                        source = obj.optString("src", "System"),
                        summary = obj.optString("sum", ""),
                        stackTrace = obj.optString("trace", ""),
                        autoRecovered = obj.optBoolean("rec", true)
                    )
                )
            }
            _diagnosticLogs.value = list
            _autoRecoveryCount.value = list.count { it.autoRecovered }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load persisted diagnostic logs", e)
        }
    }

    private fun saveLogsNow(logs: List<DiagnosticLogEntry>) {
        try {
            val prefs = getPrefs() ?: return
            val arr = JSONArray()
            for (item in logs.take(MAX_DIAGNOSTIC_LOGS)) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("ts", item.timestampFormatted)
                    put("src", item.source)
                    put("sum", item.summary)
                    put("trace", item.stackTrace)
                    put("rec", item.autoRecovered)
                }
                arr.put(obj)
            }
            prefs.edit().putString(KEY_LOGS_JSON, arr.toString()).commit()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist diagnostic logs", e)
        }
    }

    private fun getPrefs(): SharedPreferences? {
        return appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun extractShortStackTrace(throwable: Throwable): String {
        return try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            sw.toString()
                .lineSequence()
                .take(5)
                .joinToString("\n")
        } catch (e: Exception) {
            throwable.toString()
        }
    }

    private fun formatTimestamp(ms: Long): String {
        return try {
            SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))
        } catch (e: Exception) {
            ""
        }
    }
}
