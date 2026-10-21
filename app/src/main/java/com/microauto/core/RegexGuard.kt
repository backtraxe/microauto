package com.microauto.core

import java.util.regex.Pattern

/**
 * Heuristic guard against catastrophic regex backtracking. It rejects overly
 * long expressions, unbounded repetition of a group that itself contains
 * unbounded, nullable, or variable-length bounded repetition, and unbounded
 * repetition of any group with an alternation branch. The alternation rule is
 * deliberately conservative: a safe pattern such as `(ab|cd)+` is also rejected
 * and should be rewritten as separate selectors. This is a mitigation, not a
 * linear-time regex engine.
 */
object RegexGuard {
    const val MAX_PATTERN_CHARS = 1024
    const val MAX_TEXT_CHARS = 4096

    fun validate(source: String) {
        require(source.length <= MAX_PATTERN_CHARS) {
            "正则表达式过长（最多 $MAX_PATTERN_CHARS 字符）"
        }
        require(!hasRiskyRepetition(source)) {
            "正则表达式包含嵌套量词或重复的交替分支，可能造成卡顿，已拒绝"
        }
    }

    fun isSafe(source: String): Boolean =
        source.length <= MAX_PATTERN_CHARS && !hasRiskyRepetition(source)

    fun matches(pattern: Pattern, text: String?): Boolean {
        if (text == null || text.length > MAX_TEXT_CHARS) return false
        if (!isSafe(pattern.pattern())) return false
        return pattern.matcher(text).find()
    }

    private class GroupState {
        var hasUnboundedRepetition = false
        var hasAlternation = false
    }

    private class Brace(
        val unbounded: Boolean,
        val variable: Boolean
    )

    private fun hasRiskyRepetition(source: String): Boolean {
        val groups = ArrayDeque<GroupState>()
        var escaped = false
        var inCharacterClass = false
        var index = 0
        while (index < source.length) {
            val ch = source[index]
            if (escaped) {
                escaped = false
                index++
                continue
            }
            when {
                ch == '\\' -> escaped = true
                inCharacterClass -> if (ch == ']') inCharacterClass = false
                ch == '[' -> inCharacterClass = true
                ch == '(' -> groups.addLast(GroupState())
                ch == '|' -> {
                    if (groups.isNotEmpty()) groups.last().hasAlternation = true
                }

                ch == '*' || ch == '+' -> {
                    if (groups.isNotEmpty()) groups.last().hasUnboundedRepetition = true
                }

                ch == '?' -> {
                    // A '?' right after '(' is group syntax ((?:...), (?=...), (?i)),
                    // not a quantifier.
                    if (source.getOrNull(index - 1) != '(' && groups.isNotEmpty()) {
                        groups.last().hasUnboundedRepetition = true
                    }
                }

                ch == '{' -> {
                    val brace = parseBrace(source, index)
                    if (brace != null && groups.isNotEmpty() && brace.variable) {
                        groups.last().hasUnboundedRepetition = true
                    }
                }

                ch == ')' -> {
                    if (groups.isNotEmpty()) {
                        val group = groups.removeLast()
                        val outerUnbounded = when (source.getOrNull(index + 1)) {
                            '*', '+' -> true
                            '{' -> parseBrace(source, index + 1)?.unbounded == true
                            else -> false
                        }
                        if (outerUnbounded &&
                            (group.hasUnboundedRepetition || group.hasAlternation)
                        ) {
                            return true
                        }
                        if (groups.isNotEmpty()) {
                            groups.last().hasUnboundedRepetition =
                                groups.last().hasUnboundedRepetition ||
                                group.hasUnboundedRepetition
                            groups.last().hasAlternation =
                                groups.last().hasAlternation ||
                                group.hasAlternation
                        }
                    }
                }
            }
            index++
        }
        return false
    }

    private fun parseBrace(source: String, start: Int): Brace? {
        if (source.getOrNull(start) != '{') return null
        val end = source.indexOf('}', start + 1)
        if (end < 0) return null
        val body = source.substring(start + 1, end)
        if (body.isEmpty()) return null
        val parts = body.split(',')
        if (parts.size > 2) return null
        val min = parts[0].toIntOrNull() ?: return null
        if (parts.size == 1) {
            return Brace(unbounded = false, variable = false)
        }
        val max = parts[1]
        if (max.isEmpty()) {
            return Brace(unbounded = true, variable = true)
        }
        val maxValue = max.toIntOrNull() ?: return null
        return Brace(
            unbounded = false,
            variable = min != maxValue
        )
    }
}
