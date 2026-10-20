package com.microauto.api

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.microauto.accessibility.AndroidNode
import com.microauto.accessibility.AutomationService
import com.microauto.accessibility.GestureController
import com.microauto.accessibility.NodeActions
import com.microauto.core.NodeLike
import com.microauto.core.SelectorMatcher
import com.microauto.script.RunLog
import com.microauto.script.ScriptExitException
import com.microauto.script.ScriptTimeoutException

class AutomationBridge(
    private val service: AutomationService,
    private val appContext: Context
) {
    private val actions = NodeActions(service, GestureController(service))
    private val mainHandler = Handler(Looper.getMainLooper())
    private val deadline = System.currentTimeMillis() + MAX_RUN_MILLIS

    fun selector(): UiSelector = UiSelector()

    fun findOne(selector: UiSelector, timeoutMs: Long): UiObject? {
        val deadlineAt = System.currentTimeMillis() + timeoutMs.coerceAtLeast(0)
        while (true) {
            checkDeadline()
            val root = service.rootNode()
            if (root != null) {
                val found = SelectorMatcher.findFirst(AndroidNode(root), selector.selector)
                if (found is AndroidNode) return UiObject(found.info, actions)
            }
            if (timeoutMs <= 0 || System.currentTimeMillis() >= deadlineAt) return null
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
    }

    fun findAll(selector: UiSelector): Array<UiObject> {
        checkDeadline()
        val root = service.rootNode() ?: return emptyArray()
        return SelectorMatcher.findAll(AndroidNode(root), selector.selector)
            .filterIsInstance<AndroidNode>()
            .map { UiObject(it.info, actions) }
            .toTypedArray()
    }

    fun tap(x: Int, y: Int, duration: Long): Boolean {
        checkDeadline()
        return actions.tap(x, y, duration)
    }

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long): Boolean {
        checkDeadline()
        return actions.swipe(x1, y1, x2, y2, duration)
    }

    fun back(): Boolean {
        checkDeadline()
        return actions.back()
    }

    fun home(): Boolean {
        checkDeadline()
        return actions.home()
    }

    fun recents(): Boolean {
        checkDeadline()
        return actions.recents()
    }

    fun launchApp(pkg: String): Boolean {
        checkDeadline()
        val intent = appContext.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            appContext.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun currentPackage(): String? {
        checkDeadline()
        return service.rootNode()?.packageName?.toString()
    }

    fun waitForPackage(pkg: String, timeoutMs: Long): Boolean {
        val deadlineAt = System.currentTimeMillis() + timeoutMs.coerceAtLeast(0)
        while (true) {
            checkDeadline()
            if (currentPackage() == pkg) return true
            if (timeoutMs <= 0 || System.currentTimeMillis() >= deadlineAt) return false
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
    }

    fun sleep(ms: Long) {
        checkDeadline()
        val remaining = deadline - System.currentTimeMillis()
        if (remaining <= 0) throw ScriptTimeoutException()
        Thread.sleep(minOf(ms.coerceAtLeast(0), remaining))
        checkDeadline()
    }

    fun toast(message: String) {
        mainHandler.post {
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun log(message: String) {
        RunLog.append(message)
    }

    fun dump(): String {
        checkDeadline()
        val root = service.rootNode() ?: return "无法获取当前窗口：无障碍服务未连接或界面不可读"
        val builder = StringBuilder()
        dumpNode(AndroidNode(root), builder, 0)
        return builder.toString()
    }

    fun exit(): Boolean = throw ScriptExitException()

    private fun dumpNode(node: NodeLike, builder: StringBuilder, depth: Int) {
        if (depth > MAX_DUMP_DEPTH || builder.length > MAX_DUMP_CHARS) return
        val indent = "  ".repeat(depth)
        val text = node.text?.replace("\n", "\\n")
        builder.append(indent)
            .append(node.className ?: "?")
            .append(" id=").append(node.viewId ?: "-")
            .append(" text=").append(text ?: "-")
            .append(" desc=").append(node.desc ?: "-")
            .append(" clickable=").append(node.clickable)
            .append(" bounds=")
            .append(node.bounds?.let { "[${it.left},${it.top}][${it.right},${it.bottom}]" } ?: "-")
            .append('\n')
        node.children.forEach { dumpNode(it, builder, depth + 1) }
    }

    private fun checkDeadline() {
        if (System.currentTimeMillis() > deadline) throw ScriptTimeoutException()
    }

    private companion object {
        const val MAX_RUN_MILLIS = 10 * 60 * 1000L
        const val POLL_INTERVAL_MILLIS = 100L
        const val MAX_DUMP_DEPTH = 30
        const val MAX_DUMP_CHARS = 200_000
    }
}
