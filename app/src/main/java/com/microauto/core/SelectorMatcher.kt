package com.microauto.core

object SelectorMatcher {
    const val MAX_NODES = 2000
    const val MAX_RESULTS = 100

    fun findFirst(root: NodeLike, selector: Selector, maxNodes: Int = MAX_NODES): NodeLike? {
        val queue = ArrayDeque<NodeLike>()
        queue.addLast(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < maxNodes) {
            val node = queue.removeFirst()
            visited++
            if (selector.matches(node)) return node
            node.children.forEach { queue.addLast(it) }
        }
        return null
    }

    fun findAll(
        root: NodeLike,
        selector: Selector,
        maxNodes: Int = MAX_NODES,
        limit: Int = MAX_RESULTS
    ): List<NodeLike> {
        val result = ArrayList<NodeLike>()
        val queue = ArrayDeque<NodeLike>()
        queue.addLast(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < maxNodes && result.size < limit) {
            val node = queue.removeFirst()
            visited++
            if (selector.matches(node)) result.add(node)
            node.children.forEach { queue.addLast(it) }
        }
        return result
    }
}
