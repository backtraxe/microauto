package com.microauto.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.microauto.core.Bounds
import com.microauto.core.NodeLike

class AndroidNode(val info: AccessibilityNodeInfo) : NodeLike {
    private val rect = Rect()

    override val text: String? get() = info.text?.toString()
    override val desc: String? get() = info.contentDescription?.toString()
    override val viewId: String? get() = info.viewIdResourceName
    override val className: String? get() = info.className?.toString()
    override val clickable: Boolean get() = info.isClickable
    override val enabled: Boolean get() = info.isEnabled
    override val selected: Boolean get() = info.isSelected
    override val bounds: Bounds?
        get() = runCatching {
            info.getBoundsInScreen(rect)
            if (rect.isEmpty) {
                null
            } else {
                Bounds(rect.left, rect.top, rect.right, rect.bottom)
            }
        }.getOrNull()

    override val children: List<NodeLike>
        get() = runCatching {
            buildList {
                for (index in 0 until info.childCount) {
                    val child = info.getChild(index) ?: continue
                    add(AndroidNode(child))
                }
            }
        }.getOrDefault(emptyList())
}
