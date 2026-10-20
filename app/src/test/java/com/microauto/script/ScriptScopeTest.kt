package com.microauto.script

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mozilla.javascript.Context

class ScriptScopeTest {

    @Test
    fun safeScopeDoesNotExposeJavaPackages() {
        val rhino = Context.enter()
        try {
            val scope = ScriptScope.create(rhino)
            assertEquals(
                "undefined",
                rhino.evaluateString(scope, "typeof Packages", "scope.js", 1, null)
            )
            assertEquals(
                "undefined",
                rhino.evaluateString(scope, "typeof java", "scope.js", 1, null)
            )
        } finally {
            Context.exit()
        }
    }
}
