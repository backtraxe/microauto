package com.microauto.script

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

object RunLog {
    private const val MAX_CHARS = 20_000
    private const val MAX_FILE_BYTES = 256 * 1024L
    private const val PREFS_NAME = "run_log"
    private var file: File? = null
    private var prefs: SharedPreferences? = null
    private var appendsSinceTrim = 0

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text

    private val _meta = MutableStateFlow<RunLogMeta?>(null)
    val meta: StateFlow<RunLogMeta?> = _meta

    @Synchronized
    fun init(context: Context) {
        val logFile = File(context.filesDir, "latest.log")
        file = logFile
        _text.value = if (logFile.exists()) {
            logFile.readText().takeLast(MAX_CHARS)
        } else {
            ""
        }
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = preferences
        val stored = readMeta(preferences)
        val normalized = stored?.let {
            normalizeInterruptedMeta(it, logFile.lastModified())
        }
        _meta.value = normalized
        if (normalized != null && normalized != stored) persist(normalized)
    }

    @Synchronized
    fun begin(
        scriptId: String?,
        scriptName: String,
        source: RunSource,
        startedAt: Long = System.currentTimeMillis(),
    ) {
        val meta = RunLogMeta(
            scriptId = scriptId,
            scriptName = scriptName,
            source = source,
            startedAt = startedAt,
            finishedAt = null,
            status = RunPhase.RUNNING,
            errorLine = null,
            errorMessage = null,
        )
        _meta.value = meta
        persist(meta)
    }

    @Synchronized
    fun recordError(line: Int?, message: String?, expectedStartedAt: Long? = null) {
        val current = _meta.value ?: return
        if (current.status != RunPhase.STARTING && current.status != RunPhase.RUNNING) return
        if (expectedStartedAt != null && current.startedAt != expectedStartedAt) return
        val updated = current.copy(errorLine = line, errorMessage = message)
        _meta.value = updated
        persist(updated)
    }

    @Synchronized
    fun complete(phase: RunPhase, finishedAt: Long = System.currentTimeMillis()) {
        val current = _meta.value ?: RunLogMeta(
            scriptId = null,
            scriptName = "脚本",
            source = RunSource.MANUAL,
            startedAt = finishedAt,
            finishedAt = finishedAt,
            status = phase,
            errorLine = null,
            errorMessage = null,
        )
        val updated = current.copy(status = phase, finishedAt = finishedAt)
        _meta.value = updated
        persist(updated)
    }

    @Synchronized
    fun append(line: String) {
        _text.value = (_text.value + line + "\n").takeLast(MAX_CHARS)
        val logFile = file ?: return
        runCatching { logFile.appendText(line + "\n") }
        appendsSinceTrim++
        if (appendsSinceTrim >= 100) {
            appendsSinceTrim = 0
            trim(logFile)
        }
    }

    @Synchronized
    fun clear() {
        _text.value = ""
        _meta.value = null
        file?.let { runCatching { it.writeText("") } }
        prefs?.edit { clear() }
    }

    private fun readMeta(preferences: SharedPreferences): RunLogMeta? {
        val values = preferences.all.mapValues { (_, value) -> value?.toString() }
        return RunLogMeta.fromMap(values)
    }

    private fun persist(meta: RunLogMeta) {
        val preferences = prefs ?: return
        preferences.edit {
            clear()
            meta.toMap().forEach { (key, value) -> putString(key, value) }
        }
    }

    private fun trim(logFile: File) {
        runCatching {
            if (logFile.length() > MAX_FILE_BYTES) {
                logFile.writeText(logFile.readText().takeLast(MAX_CHARS / 2))
            }
        }
    }
}
