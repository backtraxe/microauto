package com.microauto.core

object ScriptInputGuard {
    // UTF-16 code unit is at most 3 UTF-8 bytes for BMP text; surrogate pairs
    // use 2 code units and 4 bytes, so this bound is safe.
    private const val FAST_ACCEPT_CHARS = ScriptLimits.MAX_SCRIPT_BYTES / 3

    fun replacementError(
        dest: CharSequence,
        dstart: Int,
        dend: Int,
        source: CharSequence,
        start: Int,
        end: Int
    ): String? {
        val newLength = dest.length - (dend - dstart) + (end - start)
        // Never block pure deletions on an already oversized text.
        if (start == end && newLength < dest.length) return null
        if (newLength <= FAST_ACCEPT_CHARS && newLength < ScriptLimits.MAX_SCRIPT_LINES) {
            return null
        }
        val candidate = StringBuilder(newLength)
            .append(dest, 0, dstart)
            .append(source, start, end)
            .append(dest, dend, dest.length)
        return ScriptLimits.scriptError(candidate.toString())
    }
}
