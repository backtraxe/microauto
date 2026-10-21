package com.microauto.script

import java.io.File

object ScriptPaths {
    private val ID_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")

    fun isValidId(id: String?): Boolean = id != null && ID_PATTERN.matches(id)

    fun resolveWithin(root: File, relative: String): File? {
        if (relative.isEmpty() || relative.contains('\u0000')) return null
        val canonicalRoot = runCatching { root.canonicalFile }.getOrNull() ?: return null
        val candidate = runCatching { File(root, relative).canonicalFile }.getOrNull() ?: return null
        return candidate.takeIf { it.path.startsWith(canonicalRoot.path + File.separator) }
    }

    fun scriptFile(root: File, id: String): File? {
        if (!isValidId(id)) return null
        return resolveWithin(root, "$id.js")
    }

    fun versionDir(versionsDir: File, scriptId: String): File? {
        if (!isValidId(scriptId)) return null
        return resolveWithin(versionsDir, scriptId)
    }

    fun versionFile(versionsDir: File, scriptId: String, versionId: String): File? {
        if (!isValidId(scriptId) || !isValidId(versionId)) return null
        return resolveWithin(versionsDir, "$scriptId/$versionId.js")
    }
}
