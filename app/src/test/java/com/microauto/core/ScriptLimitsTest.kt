package com.microauto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptLimitsTest {

    @Test
    fun acceptsNormalScripts() {
        assertNull(ScriptLimits.scriptError("log('hello');"))
    }

    @Test
    fun rejectsScriptsOverByteLimit() {
        val text = "a".repeat(ScriptLimits.MAX_SCRIPT_BYTES + 1)
        val error = ScriptLimits.scriptError(text)
        assertTrue(error?.contains("128 KB") == true)
        assertTrue(error?.contains("请拆分脚本") == true)
    }

    @Test
    fun acceptsExactByteLimit() {
        assertNull(ScriptLimits.scriptError("a".repeat(ScriptLimits.MAX_SCRIPT_BYTES)))
    }

    @Test
    fun rejectsMultiByteScriptOverByteLimit() {
        val text = "中".repeat(ScriptLimits.MAX_SCRIPT_BYTES / 3 + 1)
        assertTrue(ScriptLimits.scriptError(text)?.contains("128 KB") == true)
    }

    @Test
    fun rejectsTooManyLines() {
        val text = (1..ScriptLimits.MAX_SCRIPT_LINES + 1)
            .joinToString("\n") { "x" }
        assertTrue(ScriptLimits.scriptError(text)?.contains("20,000 行") == true)
    }

    @Test
    fun acceptsLineLimitExactly() {
        val text = (1..ScriptLimits.MAX_SCRIPT_LINES).joinToString("\n") { "x" }
        assertNull(ScriptLimits.scriptError(text))
    }

    @Test
    fun countsLinesLikeTheEditor() {
        assertEquals(1, ScriptLimits.lineCount(""))
        assertEquals(1, ScriptLimits.lineCount("a"))
        assertEquals(2, ScriptLimits.lineCount("a\n"))
    }
}
