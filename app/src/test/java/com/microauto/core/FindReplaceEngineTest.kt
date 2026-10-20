package com.microauto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FindReplaceEngineTest {

    @Test
    fun findsAllNonOverlappingMatches() {
        val matches = FindReplaceEngine.findAll("签到 签到 打卡", "签到", caseSensitive = true)

        assertEquals(2, matches.size)
        assertEquals(0, matches[0].start)
        assertEquals(2, matches[0].endExclusive)
        assertEquals(3, matches[1].start)
    }

    @Test
    fun respectsCaseSensitivity() {
        val sensitive = FindReplaceEngine.findAll("Hello hello", "hello", caseSensitive = true)
        val insensitive = FindReplaceEngine.findAll("Hello hello", "hello", caseSensitive = false)

        assertEquals(1, sensitive.size)
        assertEquals(2, insensitive.size)
    }

    @Test
    fun emptyQueryHasNoMatches() {
        assertTrue(FindReplaceEngine.findAll("abc", "", caseSensitive = true).isEmpty())
    }

    @Test
    fun queryLongerThanTextHasNoMatches() {
        assertTrue(FindReplaceEngine.findAll("abc", "abcd", caseSensitive = true).isEmpty())
    }

    @Test
    fun replacesAllMatchesLiterally() {
        val text = "a cat and a cat"
        val matches = FindReplaceEngine.findAll(text, "cat", caseSensitive = true)

        assertEquals("a dog and a dog", FindReplaceEngine.replaceAll(text, matches, "dog"))
    }

    @Test
    fun replacementTreatsDollarAndBackslashLiterally() {
        val text = "price"
        val matches = FindReplaceEngine.findAll(text, "price", caseSensitive = true)

        assertEquals("$1\\", FindReplaceEngine.replaceAll(text, matches, "$1\\"))
    }

    @Test
    fun nextIndexWrapsInBothDirections() {
        assertEquals(1, FindReplaceEngine.nextIndex(0, 3, 1))
        assertEquals(0, FindReplaceEngine.nextIndex(2, 3, 1))
        assertEquals(2, FindReplaceEngine.nextIndex(0, 3, -1))
        assertEquals(1, FindReplaceEngine.nextIndex(2, 3, -1))
    }

    @Test
    fun nextIndexHandlesEmptyAndOutOfRange() {
        assertEquals(-1, FindReplaceEngine.nextIndex(0, 0, 1))
        assertEquals(0, FindReplaceEngine.nextIndex(-1, 3, 1))
        assertEquals(2, FindReplaceEngine.nextIndex(5, 3, -1))
    }
}
