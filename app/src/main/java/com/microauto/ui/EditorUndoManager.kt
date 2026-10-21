package com.microauto.ui

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import com.microauto.core.UndoHistory

class EditorUndoManager(
    private val editor: EditText,
    private val onChanged: () -> Unit
) : TextWatcher {

    private data class Snapshot(
        val text: String,
        val selectionStart: Int,
        val selectionEnd: Int
    )

    private val history = UndoHistory<Snapshot>(weight = { it.text.length })
    private var applying = false
    private var installed = false
    private var lastChangeAt = 0L

    fun start() {
        if (!installed) {
            editor.addTextChangedListener(this)
            installed = true
        }
        reset()
    }

    fun stop() {
        if (installed) {
            editor.removeTextChangedListener(this)
            installed = false
        }
    }

    fun pause() {
        stop()
    }

    fun resume() {
        if (!installed) {
            editor.addTextChangedListener(this)
            installed = true
        }
    }

    fun reset() {
        history.clear()
        lastChangeAt = 0L
        onChanged()
    }

    fun breakCoalescing() {
        lastChangeAt = 0L
    }

    fun canUndo(): Boolean = history.canUndo()

    fun canRedo(): Boolean = history.canRedo()

    fun undo(): Boolean {
        val previous = history.undo(currentSnapshot()) ?: return false
        apply(previous)
        lastChangeAt = 0L
        onChanged()
        return true
    }

    fun redo(): Boolean {
        val next = history.redo(currentSnapshot()) ?: return false
        apply(next)
        lastChangeAt = 0L
        onChanged()
        return true
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        if (applying || s == null) return
        if (count == 0 && after == 0) return
        val now = System.currentTimeMillis()
        if (now - lastChangeAt > COALESCE_MS) {
            history.record(
                Snapshot(
                    text = s.toString(),
                    selectionStart = editor.selectionStart,
                    selectionEnd = editor.selectionEnd
                )
            )
        }
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

    override fun afterTextChanged(s: Editable?) {
        if (applying || s == null) return
        lastChangeAt = System.currentTimeMillis()
        onChanged()
    }

    private fun currentSnapshot(): Snapshot = Snapshot(
        text = editor.text?.toString().orEmpty(),
        selectionStart = editor.selectionStart,
        selectionEnd = editor.selectionEnd
    )

    private fun apply(snapshot: Snapshot) {
        applying = true
        try {
            editor.setText(snapshot.text)
            val length = editor.text?.length ?: 0
            editor.setSelection(
                snapshot.selectionStart.coerceIn(0, length),
                snapshot.selectionEnd.coerceIn(0, length)
            )
        } finally {
            applying = false
        }
    }

    private companion object {
        const val COALESCE_MS = 700L
    }
}
