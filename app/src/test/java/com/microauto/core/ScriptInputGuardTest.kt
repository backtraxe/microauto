package com.microauto.core

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptInputGuardTest {

    @Test
    fun acceptsNormalTyping() {
        assertNull(insert("log(", "x)"))
    }

    @Test
    fun acceptsTextAtByteLimit() {
        val dest = ""
        val source = "a".repeat(ScriptLimits.MAX_SCRIPT_BYTES)
        assertNull(ScriptInputGuard.replacementError(dest, 0, 0, source, 0, source.length))
    }

    @Test
    fun rejectsTextOverByteLimit() {
        val source = "a".repeat(ScriptLimits.MAX_SCRIPT_BYTES + 1)
        val error = ScriptInputGuard.replacementError("", 0, 0, source, 0, source.length)
        assertTrue(error?.contains("128 KB") == true)
    }

    @Test
    fun rejectsPasteOverLineLimit() {
        val source = (1..ScriptLimits.MAX_SCRIPT_LINES + 1).joinToString("\n") { "x" }
        val error = ScriptInputGuard.replacementError("", 0, 0, source, 0, source.length)
        assertTrue(error?.contains("20,000 行") == true)
    }

    @Test
    fun acceptsDeletionOnOversizedText() {
        val dest = "a".repeat(ScriptLimits.MAX_SCRIPT_BYTES + 1)
        assertNull(ScriptInputGuard.replacementError(dest, 0, 10, "", 0, 0))
    }

    @Test
    fun rejectsSameLengthReplacementOverByteLimit() {
        val dest = "a".repeat(50_000)
        val source = "中".repeat(50_000)
        val error = ScriptInputGuard.replacementError(dest, 0, dest.length, source, 0, source.length)
        assertTrue(error?.contains("128 KB") == true)
    }

    @Test
    fun rejectsShrinkingReplacementThatStillExceedsByteLimit() {
        val dest = "a".repeat(100_000)
        val source = "中".repeat(40_000)
        val error = ScriptInputGuard.replacementError(dest, 0, 50_000, source, 0, source.length)
        assertTrue(error?.contains("128 KB") == true)
    }

    private fun insert(dest: String, source: String): String? =
        ScriptInputGuard.replacementError(dest, dest.length, dest.length, source, 0, source.length)
}
