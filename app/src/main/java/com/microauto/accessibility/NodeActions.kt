package com.microauto.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.microauto.core.Bounds

class NodeActions(
    private val service: AccessibilityService,
    private val gestures: GestureController
) {
    fun click(node: AccessibilityNodeInfo): Boolean {
        if (performClick(node)) return true
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 5) {
            if (parent.isClickable && performClick(parent)) return true
            parent = parent.parent
            depth++
        }
        val bounds = boundsOf(node) ?: return false
        return gestures.tap(bounds.centerX, bounds.centerY)
    }

    fun longClick(node: AccessibilityNodeInfo): Boolean {
        if (runCatching {
                node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            }.getOrDefault(false)
        ) {
            return true
        }
        val bounds = boundsOf(node) ?: return false
        return gestures.tap(bounds.centerX, bounds.centerY, 600)
    }

    fun setText(node: AccessibilityNodeInfo, value: String): Boolean {
        runCatching { node.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        return runCatching {
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }.getOrDefault(false)
    }

    fun scrollForward(node: AccessibilityNodeInfo): Boolean {
        if (runCatching {
                node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            }.getOrDefault(false)
        ) {
            return true
        }
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 5) {
            if (runCatching {
                    parent.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                }.getOrDefault(false)
            ) {
                return true
            }
            parent = parent.parent
            depth++
        }
        return false
    }

    fun tap(x: Int, y: Int, duration: Long): Boolean = gestures.tap(x, y, duration)

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long): Boolean =
        gestures.swipe(x1, y1, x2, y2, duration)

    fun back(): Boolean = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

    fun home(): Boolean = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)

    fun recents(): Boolean = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)

    fun boundsOf(node: AccessibilityNodeInfo): Bounds? {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return if (rect.isEmpty) {
            null
        } else {
            Bounds(rect.left, rect.top, rect.right, rect.bottom)
        }
    }

    private fun performClick(node: AccessibilityNodeInfo): Boolean =
        runCatching {
            node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }.getOrDefault(false)
}
