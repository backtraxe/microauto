package com.microauto.core

data class FindMatch(
    val start: Int,
    val endExclusive: Int
)

object FindReplaceEngine {
    fun findAll(text: String, query: String, caseSensitive: Boolean): List<FindMatch> {
        if (query.isEmpty() || query.length > text.length) return emptyList()
        val matches = mutableListOf<FindMatch>()
        var index = 0
        while (index <= text.length - query.length) {
            if (text.regionMatches(
                    index,
                    query,
                    0,
                    query.length,
                    ignoreCase = !caseSensitive
                )
            ) {
                matches += FindMatch(index, index + query.length)
                index += query.length
            } else {
                index++
            }
        }
        return matches
    }

    fun replaceAll(
        text: String,
        matches: List<FindMatch>,
        replacement: String
    ): String {
        if (matches.isEmpty()) return text
        val builder = StringBuilder(text.length + matches.size * replacement.length)
        var last = 0
        for (match in matches) {
            builder.append(text, last, match.start)
            builder.append(replacement)
            last = match.endExclusive
        }
        builder.append(text, last, text.length)
        return builder.toString()
    }

    fun nextIndex(current: Int, size: Int, direction: Int): Int {
        if (size <= 0) return -1
        if (current !in 0 until size) {
            return if (direction >= 0) 0 else size - 1
        }
        val step = direction % size
        return (current + step + size) % size
    }
}
