package com.microauto.script

import org.junit.Assert.assertThrows
import org.junit.Test

class ScriptExecutionGuardTest {

    @Test
    fun throwsTimeoutWhenDeadlinePassed() {
        ScriptExecutionGuard.install(
            deadlineMillis = System.currentTimeMillis() - 1,
            isCancelled = { false }
        )
        try {
            assertThrows(ScriptTimeoutException::class.java) {
                ScriptExecutionGuard.check()
            }
        } finally {
            ScriptExecutionGuard.clear()
        }
    }

    @Test
    fun throwsCancelledWhenFlagSet() {
        ScriptExecutionGuard.install(
            deadlineMillis = System.currentTimeMillis() + 60_000,
            isCancelled = { true }
        )
        try {
            assertThrows(ScriptCancelledException::class.java) {
                ScriptExecutionGuard.check()
            }
        } finally {
            ScriptExecutionGuard.clear()
        }
    }

    @Test
    fun passesWhenWithinDeadlineAndNotCancelled() {
        ScriptExecutionGuard.install(
            deadlineMillis = System.currentTimeMillis() + 60_000,
            isCancelled = { false }
        )
        try {
            ScriptExecutionGuard.check()
        } finally {
            ScriptExecutionGuard.clear()
        }
    }

    @Test
    fun checkIsNoOpWhenNotInstalled() {
        ScriptExecutionGuard.clear()
        ScriptExecutionGuard.check()
    }
}
