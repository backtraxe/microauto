package com.microauto.core

object NodeDump {
    private const val MAX_DEPTH = 30
    private const val MAX_CHARS = 200_000

    fun format(root: NodeLike): String = buildString {
        appendNode(root, this, 0)
    }

    private fun appendNode(node: NodeLike, builder: StringBuilder, depth: Int) {
        if (depth > MAX_DEPTH || builder.length > MAX_CHARS) return
        val indent = "  ".repeat(depth)
        val text = if (node.isPassword) "<hidden>" else node.text?.replace("\n", "\\n")
        val desc = if (node.isPassword) "<hidden>" else node.desc
        builder.append(indent)
            .append(node.className ?: "?")
            .append(" id=").append(node.viewId ?: "-")
            .append(" text=").append(text ?: "-")
            .append(" desc=").append(desc ?: "-")
            .append(" clickable=").append(node.clickable)
            .append(" bounds=")
            .append(node.bounds?.let { "[${it.left},${it.top}][${it.right},${it.bottom}]" } ?: "-")
            .append('\n')
        node.children.forEach { appendNode(it, builder, depth + 1) }
    }
}
