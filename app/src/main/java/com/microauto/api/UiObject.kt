package com.microauto.api

import android.view.accessibility.AccessibilityNodeInfo
import com.microauto.accessibility.NodeActions
import com.microauto.core.Bounds

class UiObject internal constructor(
    private val info: AccessibilityNodeInfo,
    private val actions: NodeActions
) {
    fun click(): Boolean = actions.click(info)

    fun longClick(): Boolean = actions.longClick(info)

    fun setText(value: String): Boolean = actions.setText(info, value)

    fun scrollForward(): Boolean = actions.scrollForward(info)

    fun text(): String? = info.text?.toString()

    fun desc(): String? = info.contentDescription?.toString()

    fun id(): String? = info.viewIdResourceName

    fun className(): String? = info.className?.toString()

    fun bounds(): Bounds? = actions.boundsOf(info)

    fun left(): Int = actions.boundsOf(info)?.left ?: 0

    fun top(): Int = actions.boundsOf(info)?.top ?: 0

    fun right(): Int = actions.boundsOf(info)?.right ?: 0

    fun bottom(): Int = actions.boundsOf(info)?.bottom ?: 0

    fun centerX(): Int = actions.boundsOf(info)?.let { (it.left + it.right) / 2 } ?: 0

    fun centerY(): Int = actions.boundsOf(info)?.let { (it.top + it.bottom) / 2 } ?: 0

    fun clickable(): Boolean = info.isClickable
}
