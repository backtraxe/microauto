package com.microauto.core

object ScriptNaming {
    private val knownExtensions = listOf(".js", ".mjs", ".txt", ".script")
    private val invalidNameChars = Regex("""[\\/:*?"<>|\u0000-\u001F]""")
    private const val MAX_NAME_LENGTH = 60

    fun sanitize(raw: String): String {
        val cleaned = raw.trim()
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace(invalidNameChars, "_")
            .trim()
            .trimEnd('.')
            .take(MAX_NAME_LENGTH)
            .trimEnd('.', ' ')
        return if (cleaned == "." || cleaned == "..") "" else cleaned
    }

    fun fromFileName(fileName: String): String {
        val baseName = fileName.substringAfterLast('/').substringAfterLast('\\').trim()
        val lower = baseName.lowercase()
        val extension = knownExtensions.firstOrNull { lower.endsWith(it) }
        val withoutExtension = if (extension != null) baseName.dropLast(extension.length) else baseName
        return sanitize(withoutExtension).ifEmpty { "导入脚本" }
    }

    fun uniqueName(base: String, existing: Set<String>, exclude: String? = null): String {
        val cleaned = sanitize(base).ifEmpty { "未命名" }
        val names = if (exclude == null) existing else existing - exclude
        if (cleaned !in names) return cleaned
        var index = 2
        while ("$cleaned ($index)" in names) index++
        return "$cleaned ($index)"
    }
}
