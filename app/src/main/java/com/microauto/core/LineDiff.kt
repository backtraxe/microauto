package com.microauto.core

enum class DiffKind { SAME, ADDED, REMOVED }

data class DiffLine(
    val kind: DiffKind,
    val text: String,
    val oldLine: Int? = null,
    val newLine: Int? = null,
)

data class DiffResult(
    val lines: List<DiffLine>,
    val added: Int,
    val removed: Int,
    val tooLarge: Boolean = false,
)

object LineDiff {
    const val MAX_LINES = 2_000

    fun diff(oldText: String, newText: String): DiffResult {
        val oldLines = splitLines(oldText)
        val newLines = splitLines(newText)
        if (oldLines.size > MAX_LINES || newLines.size > MAX_LINES) {
            return DiffResult(emptyList(), added = 0, removed = 0, tooLarge = true)
        }

        val oldCount = oldLines.size
        val newCount = newLines.size
        val stride = newCount + 1
        val lcs = IntArray((oldCount + 1) * stride)
        for (oldIndex in oldCount - 1 downTo 0) {
            for (newIndex in newCount - 1 downTo 0) {
                val position = oldIndex * stride + newIndex
                lcs[position] = if (oldLines[oldIndex] == newLines[newIndex]) {
                    lcs[(oldIndex + 1) * stride + newIndex + 1] + 1
                } else {
                    maxOf(
                        lcs[(oldIndex + 1) * stride + newIndex],
                        lcs[oldIndex * stride + newIndex + 1],
                    )
                }
            }
        }

        val lines = ArrayList<DiffLine>(oldCount + newCount)
        var added = 0
        var removed = 0
        var oldIndex = 0
        var newIndex = 0
        while (oldIndex < oldCount && newIndex < newCount) {
            if (oldLines[oldIndex] == newLines[newIndex]) {
                lines += DiffLine(
                    kind = DiffKind.SAME,
                    text = oldLines[oldIndex],
                    oldLine = oldIndex + 1,
                    newLine = newIndex + 1,
                )
                oldIndex++
                newIndex++
            } else if (
                lcs[(oldIndex + 1) * stride + newIndex] >=
                lcs[oldIndex * stride + newIndex + 1]
            ) {
                lines += DiffLine(
                    kind = DiffKind.REMOVED,
                    text = oldLines[oldIndex],
                    oldLine = oldIndex + 1,
                )
                removed++
                oldIndex++
            } else {
                lines += DiffLine(
                    kind = DiffKind.ADDED,
                    text = newLines[newIndex],
                    newLine = newIndex + 1,
                )
                added++
                newIndex++
            }
        }
        while (oldIndex < oldCount) {
            lines += DiffLine(
                kind = DiffKind.REMOVED,
                text = oldLines[oldIndex],
                oldLine = oldIndex + 1,
            )
            removed++
            oldIndex++
        }
        while (newIndex < newCount) {
            lines += DiffLine(
                kind = DiffKind.ADDED,
                text = newLines[newIndex],
                newLine = newIndex + 1,
            )
            added++
            newIndex++
        }
        return DiffResult(lines, added, removed)
    }

    private fun splitLines(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        return text.replace("\r\n", "\n").split('\n')
    }
}
