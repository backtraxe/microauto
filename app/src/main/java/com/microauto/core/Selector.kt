package com.microauto.core

import java.util.regex.Pattern

data class Selector(
    val text: String? = null,
    val textContains: String? = null,
    val textPattern: Pattern? = null,
    val id: String? = null,
    val idContains: String? = null,
    val desc: String? = null,
    val descContains: String? = null,
    val className: String? = null,
    val clickable: Boolean? = null,
    val enabled: Boolean? = null,
    val selected: Boolean? = null
) {
    fun matches(node: NodeLike): Boolean {
        val nodeText = node.text
        if (text != null && nodeText != text) return false
        if (textContains != null && nodeText?.contains(textContains) != true) return false
        if (textPattern != null && (nodeText == null || !textPattern.matcher(nodeText).find())) return false
        if (id != null && node.viewId != id) return false
        if (idContains != null && node.viewId?.contains(idContains) != true) return false
        if (desc != null && node.desc != desc) return false
        if (descContains != null && node.desc?.contains(descContains) != true) return false
        if (className != null && node.className != className) return false
        if (clickable != null && node.clickable != clickable) return false
        if (enabled != null && node.enabled != enabled) return false
        if (selected != null && node.selected != selected) return false
        return true
    }
}
