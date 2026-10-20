package com.microauto.script

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.Context
import java.util.concurrent.atomic.AtomicReference

class RhinoExecutionTest {

    @Test
    fun instructionObserverStopsInfiniteLoopAtDeadline() {
        val error = AtomicReference<Throwable?>()
        val thread = Thread {
            ScriptExecutionGuard.install(
                deadlineMillis = System.currentTimeMillis() + 200,
                isCancelled = { false }
            )
            val rhino = ScriptContextFactory.enterContext()
            try {
                rhino.optimizationLevel = -1
                rhino.instructionObserverThreshold = 1_000
                val scope = rhino.initSafeStandardObjects()
                rhino.evaluateString(scope, "while(true){}", "loop.js", 1, null)
            } catch (throwable: Throwable) {
                error.set(throwable)
            } finally {
                Context.exit()
                ScriptExecutionGuard.clear()
            }
        }

        thread.start()
        thread.join(3_000)

        assertFalse("死循环应在 deadline 后终止", thread.isAlive)
        assertTrue(
            "应以 ScriptTimeoutException 终止，实际为 ${error.get()}",
            error.get() is ScriptTimeoutException
        )
    }
}
