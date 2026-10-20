package com.microauto.script

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

object RunLog {
    private const val MAX_CHARS = 20_000
    private const val MAX_FILE_BYTES = 256 * 1024L
    private var file: File? = null
    private var appendsSinceTrim = 0

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text

    @Synchronized
    fun init(context: Context) {
        val logFile = File(context.filesDir, "latest.log")
        file = logFile
        _text.value = if (logFile.exists()) {
            logFile.readText().takeLast(MAX_CHARS)
        } else {
            ""
        }
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
        file?.let { runCatching { it.writeText("") } }
    }

    private fun trim(logFile: File) {
        runCatching {
            if (logFile.length() > MAX_FILE_BYTES) {
                logFile.writeText(logFile.readText().takeLast(MAX_CHARS / 2))
            }
        }
    }
}
