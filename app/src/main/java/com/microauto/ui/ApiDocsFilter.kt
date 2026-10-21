package com.microauto.ui

object ApiDocsFilter {
    fun filter(sections: List<ApiSection>, query: String): List<ApiSection> {
        val needle = query.trim()
        if (needle.isEmpty()) return sections
        return sections.mapNotNull { section ->
            if (section.title.contains(needle, ignoreCase = true)) {
                section
            } else {
                val entries = section.entries.filter { entry -> matches(entry, needle) }
                if (entries.isEmpty()) null else section.copy(entries = entries)
            }
        }
    }

    fun entryCount(sections: List<ApiSection>): Int = sections.sumOf { it.entries.size }

    private fun matches(entry: ApiEntry, query: String): Boolean =
        entry.signature.contains(query, ignoreCase = true) ||
            entry.description.contains(query, ignoreCase = true) ||
            entry.example?.contains(query, ignoreCase = true) == true
}
