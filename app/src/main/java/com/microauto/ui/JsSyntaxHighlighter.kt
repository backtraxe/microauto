package com.microauto.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.widget.EditText
import androidx.core.graphics.toColorInt
import com.microauto.core.JsTokenType
import com.microauto.core.JsTokenizer

class JsSyntaxHighlighter(
    private val editor: EditText,
    context: Context
) : TextWatcher {

    private class SyntaxSpan(color: Int) : ForegroundColorSpan(color)

    private val palette = Palette.from(context)
    private val handler = Handler(Looper.getMainLooper())
    private val highlightTask = Runnable { highlight() }

    fun install() {
        editor.addTextChangedListener(this)
        highlight()
    }

    fun release() {
        handler.removeCallbacks(highlightTask)
        editor.removeTextChangedListener(this)
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

    override fun afterTextChanged(s: Editable?) {
        handler.removeCallbacks(highlightTask)
        handler.postDelayed(highlightTask, HIGHLIGHT_DELAY_MS)
    }

    private fun highlight() {
        val text = editor.text ?: return
        text.getSpans(0, text.length, SyntaxSpan::class.java).forEach { text.removeSpan(it) }
        if (text.length > MAX_HIGHLIGHT_CHARS) return
        for (token in JsTokenizer.tokenize(text.toString())) {
            val color = when (token.type) {
                JsTokenType.COMMENT -> palette.comment
                JsTokenType.STRING -> palette.string
                JsTokenType.KEYWORD -> palette.keyword
                JsTokenType.NUMBER -> palette.number
                JsTokenType.BUILTIN -> palette.builtin
            }
            text.setSpan(
                SyntaxSpan(color),
                token.start,
                token.end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private data class Palette(
        val keyword: Int,
        val string: Int,
        val comment: Int,
        val number: Int,
        val builtin: Int
    ) {
        companion object {
            fun from(context: Context): Palette {
                val dark = (context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                return if (dark) {
                    Palette(
                        keyword = "#C4B5FD".toColorInt(),
                        string = "#6EE7B7".toColorInt(),
                        comment = "#9CA3AF".toColorInt(),
                        number = "#FCD34D".toColorInt(),
                        builtin = "#93C5FD".toColorInt()
                    )
                } else {
                    Palette(
                        keyword = "#6D28D9".toColorInt(),
                        string = "#047857".toColorInt(),
                        comment = "#6B7280".toColorInt(),
                        number = "#B45309".toColorInt(),
                        builtin = "#1D4ED8".toColorInt()
                    )
                }
            }
        }
    }

    private companion object {
        const val HIGHLIGHT_DELAY_MS = 80L
        const val MAX_HIGHLIGHT_CHARS = 50_000
    }
}
