package com.microauto.core

object TextLines {
    fun offsetOfLine(text: String, line: Int): Int? {
        if (line < 1) return null
        if (line == 1) return 0
        var currentLine = 1
        for (index in text.indices) {
            if (text[index] != '\n') continue
            currentLine++
            if (currentLine == line) return index + 1
        }
        return null
    }
}
