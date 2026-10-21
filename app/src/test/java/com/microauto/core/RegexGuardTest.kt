package com.microauto.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.Pattern

class RegexGuardTest {

    private class FakeNode(override val text: String? = null) : NodeLike {
        override val desc: String? = null
        override val viewId: String? = null
        override val className: String? = null
        override val clickable: Boolean = false
        override val enabled: Boolean = true
        override val selected: Boolean = false
        override val bounds: Bounds? = null
        override val children: List<NodeLike> = emptyList()
    }

    @Test
    fun rejectsOverlongPattern() {
        assertThrows(IllegalArgumentException::class.java) {
            RegexGuard.validate("a".repeat(RegexGuard.MAX_PATTERN_CHARS + 1))
        }
    }

    @Test
    fun rejectsNestedQuantifiers() {
        for (source in listOf(
            "(a+)+",
            "(a*)*",
            "(.*)*",
            "((a+))+",
            "(a+){2,}",
            "(a?)+",
            "(a{2,})+",
            "(a{0,1})+",
            "(a{1,2})+",
            "(a{0,2})+",
            "(a{2,3})+"
        )) {
            val error = assertThrows(IllegalArgumentException::class.java) {
                RegexGuard.validate(source)
            }
            assertTrue(error.message?.contains("嵌套量词") == true)
        }
    }

    @Test
    fun rejectsUnboundedAlternationGroupsConservatively() {
        for (source in listOf("(a|aa)+", "(a|b)+", "(ab|cd)+", "(?:a|b)*", "(a|b){2,}")) {
            assertThrows(IllegalArgumentException::class.java) {
                RegexGuard.validate(source)
            }
        }
    }

    @Test
    fun acceptsNormalPatterns() {
        RegexGuard.validate("签到|打卡")
        RegexGuard.validate("a+")
        RegexGuard.validate("[+*]")
        RegexGuard.validate("\\+\\+")
    }

    @Test
    fun acceptsGroupedAndAlternationPatterns() {
        for (source in listOf(
            "a+b",
            "(ab)+",
            "(a|b)",
            "(a|b){2}",
            "(?:ab)+",
            "(a+)?",
            "(a+){2}",
            "(a{2})+",
            "(a{2,2})+",
            "(abc){2}",
            "\\d{2,4}"
        )) {
            RegexGuard.validate(source)
        }
    }

    @Test
    fun patternLengthBoundaryIsInclusive() {
        RegexGuard.validate("a".repeat(RegexGuard.MAX_PATTERN_CHARS))
        assertThrows(IllegalArgumentException::class.java) {
            RegexGuard.validate("a".repeat(RegexGuard.MAX_PATTERN_CHARS + 1))
        }
    }

    @Test
    fun selectorMatchesNormalRegex() {
        val selector = Selector(textPattern = Pattern.compile("签到|打卡"))

        assertTrue(selector.matches(FakeNode(text = "今天签到")))
        assertFalse(selector.matches(FakeNode(text = "登录")))
        assertFalse(selector.matches(FakeNode(text = null)))
    }

    @Test
    fun oneMegabyteTextWithNestedQuantifierReturnsImmediately() {
        val started = System.nanoTime()
        val matched = Selector(textPattern = Pattern.compile("(a+)+$"))
            .matches(FakeNode(text = "a".repeat(1_000_000)))
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000

        assertFalse(matched)
        assertTrue("elapsed=${elapsedMillis}ms", elapsedMillis < 1_000)
    }

    @Test
    fun textLongerThanLimitIsNotMatched() {
        val pattern = Pattern.compile("a")
        assertTrue(RegexGuard.matches(pattern, "a".repeat(RegexGuard.MAX_TEXT_CHARS)))
        assertFalse(RegexGuard.matches(pattern, "a".repeat(RegexGuard.MAX_TEXT_CHARS + 1)))
    }
}
