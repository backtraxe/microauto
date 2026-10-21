package com.microauto.script

import android.content.Context
import com.microauto.accessibility.AutomationService
import com.microauto.accessibility.ScreenState
import com.microauto.core.DateFormats
import org.mozilla.javascript.RhinoException

object ScriptRunner {
    private const val MAX_RUN_MILLIS = 10 * 60 * 1000L

    @Volatile
    private var currentThread: Thread? = null

    @Volatile
    private var running = false

    @Volatile
    private var cancelRequested = false

    fun run(context: Context, script: String): Boolean {
        if (running) {
            RunLog.append("[跳过] 已有脚本在运行")
            return false
        }
        running = true
        cancelRequested = false
        Thread.interrupted()
        currentThread = Thread.currentThread()
        val startedAt = System.currentTimeMillis()
        RunLog.append("▶ ${timestamp()} 开始执行")
        try {
            var service = AutomationService.instance
            if (service == null) {
                RunLog.append("[等待] 无障碍服务连接中…")
                val waitUntil = System.currentTimeMillis() + 5000
                while (service == null && System.currentTimeMillis() < waitUntil) {
                    Thread.sleep(200)
                    service = AutomationService.instance
                }
            }
            if (service == null) {
                RunLog.append("[错误] 无障碍服务未开启")
                return false
            }
            if (!ScreenState.isInteractive(context)) {
                RunLog.append("[错误] 屏幕未点亮，已终止")
                return false
            }
            if (ScreenState.isLocked(context)) {
                RunLog.append("[错误] 屏幕已锁定，请先解锁")
                return false
            }
            ScriptExecutionGuard.install(startedAt + MAX_RUN_MILLIS) { cancelRequested }
            ScriptEngine.execute(context, service, script)
            RunLog.append("✔ ${timestamp()} 执行结束（${System.currentTimeMillis() - startedAt} ms）")
            return true
        } catch (error: Throwable) {
            return handleError(error)
        } finally {
            ScriptExecutionGuard.clear()
            running = false
            currentThread = null
        }
    }

    fun cancel() {
        cancelRequested = true
        currentThread?.interrupt()
    }

    fun isRunning(): Boolean = running

    private fun handleError(error: Throwable): Boolean {
        val exit = findCause(error, ScriptExitException::class.java)
        if (exit != null) {
            RunLog.append("■ ${timestamp()} 脚本主动退出")
            return true
        }
        val cancelled = findCause(error, ScriptCancelledException::class.java)
        if (cancelled != null) {
            RunLog.append("■ ${timestamp()} 已停止")
            return false
        }
        val timeout = findCause(error, ScriptTimeoutException::class.java)
        if (timeout != null) {
            RunLog.append("[错误] $timeout")
            return false
        }
        if (findCause(error, InterruptedException::class.java) != null || Thread.currentThread().isInterrupted) {
            RunLog.append("■ ${timestamp()} 已停止")
            return false
        }
        if (error is RhinoException) {
            RunLog.append("[脚本错误] ${error.details()}")
            error.scriptStackTrace?.takeIf { it.isNotBlank() }?.let { RunLog.append(it) }
            return false
        }
        RunLog.append("[错误] ${error.javaClass.simpleName}: ${error.message}")
        return false
    }

    private fun findCause(error: Throwable, type: Class<out Throwable>): Throwable? {
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 10) {
            if (type.isInstance(current)) return current
            current = current.cause
            depth++
        }
        return null
    }

    private fun timestamp(): String = DateFormats.format(System.currentTimeMillis())
}
