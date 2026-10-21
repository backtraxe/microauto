package com.microauto.api

import com.microauto.core.RegexGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UiSelectorTest {

    @Test
    fun rejectsOverlongPatternBeforeCompiling() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            UiSelector().textMatches("a".repeat(RegexGuard.MAX_PATTERN_CHARS + 1))
        }
        assertEquals(true, error.message?.contains("过长"))
    }

    @Test
    fun rejectsNestedQuantifier() {
        assertThrows(IllegalArgumentException::class.java) {
            UiSelector().textMatches("(a+)+$")
        }
    }

    @Test
    fun acceptsNormalPattern() {
        UiSelector().textMatches("签到|打卡", "i")
    }

    @Test
    fun acceptsGroupedPatterns() {
        UiSelector().textMatches("(ab)+|a+b")
        UiSelector().textMatches("(a|b){2}")
        UiSelector().textMatches("(a+){2}")
    }

    @Test
    fun rejectsUnboundedAlternationGroup() {
        assertThrows(IllegalArgumentException::class.java) {
            UiSelector().textMatches("(a|b)+")
        }
    }

    @Test
    fun rejectsVariableBoundedGroupUnderUnboundedOuter() {
        assertThrows(IllegalArgumentException::class.java) {
            UiSelector().textMatches("(a{1,2})+")
        }
    }

    @Test
    fun acceptsPatternAtCharacterLimit() {
        UiSelector().textMatches("a".repeat(RegexGuard.MAX_PATTERN_CHARS))
    }
}
