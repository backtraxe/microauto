package com.microauto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoHistoryTest {

    @Test
    fun undoAndRedoRestoreStatesInOrder() {
        val history = UndoHistory<Int>()
        history.record(0)
        history.record(1)

        assertEquals(1, history.undo(2))
        assertEquals(0, history.undo(1))
        assertEquals(1, history.redo(0))
        assertEquals(2, history.redo(1))
    }

    @Test
    fun newEditClearsRedo() {
        val history = UndoHistory<Int>()
        history.record(0)
        history.record(1)
        assertEquals(1, history.undo(2))

        history.record(1)

        assertFalse(history.canRedo())
        assertNull(history.redo(2))
    }

    @Test
    fun trimsOldestEntriesWhenLimitIsReached() {
        val history = UndoHistory<Int>(maxEntries = 2)
        history.record(0)
        history.record(1)
        history.record(2)

        assertEquals(2, history.undo(3))
        assertEquals(1, history.undo(2))
        assertNull(history.undo(1))
    }

    @Test
    fun trimsByWeightBudget() {
        val history = UndoHistory<String>(maxWeight = 4, weight = { it.length })
        history.record("aa")
        history.record("bb")
        history.record("cc")

        assertEquals("cc", history.undo("dd"))
        assertEquals("bb", history.undo("cc"))
        assertNull(history.undo("bb"))
    }

    @Test
    fun emptyHistoryHasNoOperations() {
        val history = UndoHistory<Int>()

        assertFalse(history.canUndo())
        assertFalse(history.canRedo())
        assertNull(history.undo(1))
        assertNull(history.redo(1))
    }

    @Test
    fun clearRemovesBothStacks() {
        val history = UndoHistory<Int>()
        history.record(0)
        history.undo(1)

        history.clear()

        assertFalse(history.canUndo())
        assertFalse(history.canRedo())
    }

    @Test
    fun undoThenRedoKeepsStackConsistent() {
        val history = UndoHistory<Int>()
        history.record(0)
        history.record(1)
        assertEquals(1, history.undo(2))

        assertTrue(history.canUndo())
        assertTrue(history.canRedo())

        assertEquals(2, history.redo(1))
        assertEquals(1, history.undo(2))
    }
}
