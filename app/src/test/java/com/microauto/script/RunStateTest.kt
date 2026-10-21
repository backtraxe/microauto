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
        RunState.start("script-1", 42L)

        assertTrue(RunState.running.value)
        assertEquals("script-1", RunState.activeScriptId.value)
        assertEquals(42L, RunState.activeToken.value)

        RunState.finish()

        assertFalse(RunState.running.value)
        assertNull(RunState.activeScriptId.value)
        assertNull(RunState.activeToken.value)
    }

    @Test
    fun setRunningFalseClearsActiveScriptAndToken() {
        RunState.start(null, 7L)
        assertNull(RunState.activeScriptId.value)
        assertEquals(7L, RunState.activeToken.value)

        RunState.setRunning(false)

        assertFalse(RunState.running.value)
        assertNull(RunState.activeScriptId.value)
        assertNull(RunState.activeToken.value)
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
