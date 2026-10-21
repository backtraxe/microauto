package com.microauto.script

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RunStateTest {

    @Before
    fun setUp() {
        RunState.finish()
    }

    @After
    fun tearDown() {
        RunState.finish()
    }

    @Test
    fun startTracksActiveScriptAndFinishClearsIt() {
        RunState.start("script-1")

        assertTrue(RunState.running.value)
        assertEquals("script-1", RunState.activeScriptId.value)

        RunState.finish()

        assertFalse(RunState.running.value)
        assertNull(RunState.activeScriptId.value)
    }

    @Test
    fun setRunningFalseClearsActiveScript() {
        RunState.start(null)
        assertNull(RunState.activeScriptId.value)

        RunState.setRunning(false)

        assertFalse(RunState.running.value)
        assertNull(RunState.activeScriptId.value)
    }

    @Test
    fun startReplacesPreviousScriptAndFinishIsIdempotent() {
        RunState.start("a")
        RunState.start("b")
        assertEquals("b", RunState.activeScriptId.value)

        RunState.finish()
        RunState.finish()

        assertFalse(RunState.running.value)
        assertNull(RunState.activeScriptId.value)
    }
}
