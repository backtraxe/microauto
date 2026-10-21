package com.microauto.core

object ScriptLimits {
    const val MAX_SCRIPT_BYTES = 128 * 1024
    const val MAX_SCRIPT_LINES = 20_000

    fun scriptError(text: String): String? {
        val bytes = utf8Size(text)
        if (bytes > MAX_SCRIPT_BYTES) {
            val kb = (bytes + 1023) / 1024
            return "脚本超过 128 KB（当前约 $kb KB），请拆分脚本"
        }
        if (lineCount(text) > MAX_SCRIPT_LINES) {
            return "脚本超过 20,000 行，请拆分脚本"
        }
        return null
    }

    fun utf8Size(text: String): Int = text.toByteArray(Charsets.UTF_8).size

    fun lineCount(text: String): Int = text.count { it == '\n' } + 1
}
