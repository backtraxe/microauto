package com.microauto.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import java.io.File

class PreludeSmokeTest {

    class FakeSelector {
        var textValue: String? = null

        fun text(value: String): FakeSelector {
            textValue = value
            return this
        }
    }

    class FakeUiObject {
        fun click(): Boolean = true
    }

    class FakeNative {
        var findOneCalled = false
        var logged: String? = null
        var tapArgs: Triple<Int, Int, Long>? = null
        private val selector = FakeSelector()

        fun selector(): FakeSelector = selector

        fun findOne(selector: FakeSelector, timeout: Long): FakeUiObject {
            findOneCalled = true
            return FakeUiObject()
        }

        fun tap(x: Int, y: Int, duration: Long): Boolean {
            tapArgs = Triple(x, y, duration)
            return true
        }

        fun log(message: String) {
            logged = message
        }
    }

    @Test
    fun selectorAndClickWorkThroughPrelude() {
        val rhino = Context.enter()
        try {
            rhino.optimizationLevel = -1
            val scope = rhino.initStandardObjects()
            val fake = FakeNative()
            ScriptableObject.putProperty(scope, "native", Context.javaToJS(fake, scope))

            val preludeFile = listOf(
                File("src/main/assets/prelude.js"),
                File("app/src/main/assets/prelude.js")
            ).first { it.exists() }
            val prelude = preludeFile.readText()
            rhino.evaluateString(scope, prelude, "prelude.js", 1, null)
            rhino.evaluateString(
                scope,
                "var node = text('签到').findOne(0); log(node.click()); click(10, 20);",
                "test.js",
                1,
                null
            )

            assertEquals("签到", fake.selector().textValue)
            assertTrue(fake.findOneCalled)
            assertEquals("true", fake.logged)
            assertEquals(Triple(10, 20, 50L), fake.tapArgs)
        } finally {
            Context.exit()
        }
    }
}
