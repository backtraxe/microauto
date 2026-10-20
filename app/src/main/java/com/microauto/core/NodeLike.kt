package com.microauto.core

data class Bounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
}

interface NodeLike {
    val text: String?
    val desc: String?
    val viewId: String?
    val className: String?
    val clickable: Boolean
    val enabled: Boolean
    val selected: Boolean
    val bounds: Bounds?
    val children: List<NodeLike>
}
