package com.microauto.core

class UndoHistory<T>(
    private val maxEntries: Int = 80,
    private val maxWeight: Int = 2_000_000,
    private val weight: (T) -> Int = { 1 }
) {
    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()
    private var undoWeight = 0
    private var redoWeight = 0

    fun record(state: T) {
        undoStack.addLast(state)
        undoWeight += weight(state)
        trimUndo()
        clearRedo()
    }

    fun undo(current: T): T? {
        val previous = undoStack.removeLastOrNull() ?: return null
        undoWeight -= weight(previous)
        redoStack.addLast(current)
        redoWeight += weight(current)
        trimRedo()
        return previous
    }

    fun redo(current: T): T? {
        val next = redoStack.removeLastOrNull() ?: return null
        redoWeight -= weight(next)
        undoStack.addLast(current)
        undoWeight += weight(current)
        trimUndo()
        return next
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()

    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        undoWeight = 0
        redoWeight = 0
    }

    private fun trimUndo() {
        while (undoStack.size > maxEntries || undoWeight > maxWeight) {
            val removed = undoStack.removeFirstOrNull() ?: break
            undoWeight -= weight(removed)
        }
    }

    private fun trimRedo() {
        while (redoStack.size > maxEntries || redoWeight > maxWeight) {
            val removed = redoStack.removeFirstOrNull() ?: break
            redoWeight -= weight(removed)
        }
    }

    private fun clearRedo() {
        redoStack.clear()
        redoWeight = 0
    }
}
