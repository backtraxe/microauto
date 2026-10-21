package com.microauto.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.RhinoException

class RhinoErrorInfoTest {

    @Test
    fun userScriptErrorKeepsLineNumberAndDetails() {
        val error = evaluate(
            source = "var a = 1;\nvar b = 2;\nfoo();",
            sourceName = "script.js",
        )

        val info = describeRhinoError(error)

        assertEquals(3, info.line)
        assertTrue(info.message.contains("ReferenceError"))
        assertFalse(info.builtinApi)
    }

    @Test
    fun preludeErrorIsReportedAsBuiltinApiWithoutLine() {
        val error = evaluate(
            source = "var a = 1;\nmissing.call();",
            sourceName = "prelude.js",
        )

        val info = describeRhinoError(error)

        assertNull(info.line)
        assertTrue(info.builtinApi)
        assertTrue(info.message.startsWith("内置 API 错误："))
    }

    @Test
    fun errorWithUnknownSourceHasNoLine() {
        val error = evaluate(
            source = "nope();",
            sourceName = "eval",
        )

        val info = describeRhinoError(error)

        assertNull(info.line)
        assertFalse(info.builtinApi)
        assertTrue(info.message.contains("ReferenceError"))
    }

    private fun evaluate(source: String, sourceName: String): RhinoException {
        val rhino = ScriptContextFactory.enterContext()
        try {
            rhino.optimizationLevel = -1
            val scope = rhino.initSafeStandardObjects()
            rhino.evaluateString(scope, source, sourceName, 1, null)
            error("脚本应当抛出 RhinoException")
        } catch (error: RhinoException) {
            return error
        } finally {
            Context.exit()
        }
    }
}
