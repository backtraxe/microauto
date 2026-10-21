package com.microauto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextLinesTest {

    @Test
    fun firstLineStartsAtZero() {
        assertEquals(0, TextLines.offsetOfLine("a\nb", 1))
    }

    @Test
    fun findsLineAfterSingleNewline() {
        assertEquals(2, TextLines.offsetOfLine("a\nb", 2))
        assertEquals(13, TextLines.offsetOfLine("first\nsecond\nthird", 3))
    }

    @Test
    fun handlesBlankLines() {
        assertEquals(2, TextLines.offsetOfLine("a\n\nb", 2))
        assertEquals(3, TextLines.offsetOfLine("a\n\nb", 3))
    }

    @Test
    fun crlfCountsAsOneBreak() {
        assertEquals(6, TextLines.offsetOfLine("a\r\nb\r\nc", 3))
    }

    @Test
    fun trailingNewlineStartsEmptyLine() {
        assertEquals(2, TextLines.offsetOfLine("a\n", 2))
    }

    @Test
    fun emptyTextOnlyHasFirstLine() {
        assertEquals(0, TextLines.offsetOfLine("", 1))
        assertNull(TextLines.offsetOfLine("", 2))
    }

    @Test
    fun outOfRangeAndNonPositiveReturnNull() {
        assertNull(TextLines.offsetOfLine("a\nb", 3))
        assertNull(TextLines.offsetOfLine("a\nb", 0))
        assertNull(TextLines.offsetOfLine("a\nb", -1))
    }
}
