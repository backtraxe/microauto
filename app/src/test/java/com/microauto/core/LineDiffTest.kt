package com.microauto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LineDiffTest {

    @Test
    fun identicalFilesAreAllSame() {
        val result = LineDiff.diff("a\nb\nc", "a\nb\nc")

        assertEquals(0, result.added)
        assertEquals(0, result.removed)
        assertFalse(result.tooLarge)
        assertEquals(3, result.lines.size)
        assertTrue(result.lines.all { it.kind == DiffKind.SAME })
        assertEquals(listOf(1, 2, 3), result.lines.map { it.oldLine })
        assertEquals(listOf(1, 2, 3), result.lines.map { it.newLine })
    }

    @Test
    fun addedLineIsCountedWithNewLineNumber() {
        val result = LineDiff.diff("a\nc", "a\nb\nc")

        assertEquals(1, result.added)
        assertEquals(0, result.removed)
        assertEquals(
            listOf(
                DiffLine(DiffKind.SAME, "a", 1, 1),
                DiffLine(DiffKind.ADDED, "b", null, 2),
                DiffLine(DiffKind.SAME, "c", 2, 3),
            ),
            result.lines,
        )
    }

    @Test
    fun removedLineIsCountedWithOldLineNumber() {
        val result = LineDiff.diff("a\nb\nc", "a\nc")

        assertEquals(0, result.added)
        assertEquals(1, result.removed)
        assertEquals(
            listOf(
                DiffLine(DiffKind.SAME, "a", 1, 1),
                DiffLine(DiffKind.REMOVED, "b", 2, null),
                DiffLine(DiffKind.SAME, "c", 3, 2),
            ),
            result.lines,
        )
    }

    @Test
    fun modifiedLineProducesRemovedThenAdded() {
        val result = LineDiff.diff("a\nb\nc", "a\nB\nc")

        assertEquals(1, result.added)
        assertEquals(1, result.removed)
        assertEquals(
            listOf(
                DiffKind.SAME,
                DiffKind.REMOVED,
                DiffKind.ADDED,
                DiffKind.SAME,
            ),
            result.lines.map { it.kind },
        )
        assertEquals("b", result.lines[1].text)
        assertEquals("B", result.lines[2].text)
    }

    @Test
    fun emptyFilesProduceEmptyDiff() {
        val result = LineDiff.diff("", "")

        assertEquals(0, result.added)
        assertEquals(0, result.removed)
        assertTrue(result.lines.isEmpty())
        assertFalse(result.tooLarge)
    }

    @Test
    fun emptyOldFileIsAllAdded() {
        val result = LineDiff.diff("", "a\nb")

        assertEquals(2, result.added)
        assertEquals(0, result.removed)
        assertEquals(listOf(1, 2), result.lines.map { it.newLine })
        assertTrue(result.lines.all { it.kind == DiffKind.ADDED })
    }

    @Test
    fun emptyNewFileIsAllRemoved() {
        val result = LineDiff.diff("a\nb", "")

        assertEquals(0, result.added)
        assertEquals(2, result.removed)
        assertEquals(listOf(1, 2), result.lines.map { it.oldLine })
        assertTrue(result.lines.all { it.kind == DiffKind.REMOVED })
    }

    @Test
    fun lineNumbersAreOneBasedAfterInsertion() {
        val result = LineDiff.diff("b\nc", "a\nb\nc")

        assertEquals(
            listOf(
                DiffLine(DiffKind.ADDED, "a", null, 1),
                DiffLine(DiffKind.SAME, "b", 1, 2),
                DiffLine(DiffKind.SAME, "c", 2, 3),
            ),
            result.lines,
        )
    }

    @Test
    fun moreThanTwoThousandLinesSkipsDiff() {
        val lines = (1..LineDiff.MAX_LINES + 1).joinToString("\n") { "line $it" }

        val result = LineDiff.diff(lines, lines)

        assertTrue(result.tooLarge)
        assertTrue(result.lines.isEmpty())
        assertEquals(0, result.added)
        assertEquals(0, result.removed)
    }

    @Test
    fun exactlyTwoThousandLinesIsComputed() {
        val lines = (1..LineDiff.MAX_LINES).joinToString("\n") { "line $it" }

        val result = LineDiff.diff(lines, lines.replaceFirst("line 1", "line one"))

        assertFalse(result.tooLarge)
        assertEquals(1, result.added)
        assertEquals(1, result.removed)
    }

    @Test
    fun crlfIsTreatedAsLineBreaks() {
        val result = LineDiff.diff("a\r\nb", "a\nb")

        assertEquals(0, result.added)
        assertEquals(0, result.removed)
        assertEquals(2, result.lines.size)
        assertTrue(result.lines.all { it.kind == DiffKind.SAME })
    }
}
