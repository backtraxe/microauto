package com.microauto.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.microauto.accessibility.ScreenState
import com.microauto.core.ScheduleTriggerPolicy
import com.microauto.core.TriggerDecision
import com.microauto.script.RunNotifications
import com.microauto.script.RunSource
import com.microauto.script.ScriptRepository
import com.microauto.script.ScriptService
import java.time.LocalTime

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val isRetry = intent?.action == ScriptScheduler.ACTION_RETRY
        val scriptId = intent?.getStringExtra(ScriptScheduler.EXTRA_SCRIPT_ID)
            ?: singleScheduledScriptId(context)
            ?: return

        val schedule = SchedulePrefs.scheduleFor(context, scriptId)
        if (schedule == null || !schedule.enabled) {
            if (schedule != null) ScriptScheduler.cancelScript(context, schedule)
            SchedulePrefs.clearPending(context, scriptId)
            return
        }

        val repository = ScriptRepository(context)
        val script = repository.read(scriptId)
        if (script == null) {
            ScriptScheduler.cancelScript(context, schedule)
            SchedulePrefs.remove(context, scriptId)
            RunNotifications.notifyFailure(context, "定时脚本", "脚本不存在，已移除定时")
            return
        }

        val scriptName = repository.find(scriptId)?.name ?: "定时脚本"
        val now = System.currentTimeMillis()
        val storedDeadline = SchedulePrefs.pendingDeadline(context, scriptId)
        val deadline = if (isRetry && storedDeadline <= 0) {
            now + ScheduleTriggerPolicy.RETRY_WINDOW_MILLIS
        } else {
            storedDeadline
        }
        val decision = ScheduleTriggerPolicy.decide(
            now = now,
            isRetry = isRetry,
            deadline = deadline,
            screenInteractive = ScreenState.isInteractive(context),
            screenLocked = ScreenState.isLocked(context)
        )

        when (decision) {
            TriggerDecision.Run -> {
                SchedulePrefs.clearPending(context, scriptId)
                ScriptScheduler.cancelRetry(context, schedule)
                runCatching {
                    ScriptService.start(
                        context,
                        scriptId,
                        scriptName,
                        script,
                        RunSource.SCHEDULE,
                    )
                }.onFailure {
                    RunNotifications.notifyFailure(
                        context,
                        scriptName,
                        "无法启动运行服务：${it.message}"
                    )
                }
            }

            is TriggerDecision.Retry -> {
                SchedulePrefs.setPending(context, scriptId, decision.deadline)
                if (!ScriptScheduler.scheduleRetry(context, schedule, decision.nextAt)) {
                    SchedulePrefs.clearPending(context, scriptId)
                    RunNotifications.notifyFailure(context, scriptName, "无法安排屏幕解锁后的重试")
                } else {
                    RunNotifications.notifyWaiting(
                        context,
                        scriptName,
                        "屏幕未解锁，稍后重试",
                    )
                }
            }

            is TriggerDecision.Skip -> {
                SchedulePrefs.clearPending(context, scriptId)
                ScriptScheduler.cancelRetry(context, schedule)
                RunNotifications.notifySkipped(context, scriptName, decision.reason)
            }
        }

        val current = SchedulePrefs.scheduleFor(context, scriptId)
        if (current?.enabled == true) {
            ScriptScheduler.scheduleScript(context, current)
        }
    }

    private fun singleScheduledScriptId(context: Context): String? {
        val enabled = SchedulePrefs.enabled(context)
        enabled.singleOrNull()?.let { return it.scriptId }
        val now = LocalTime.now()
        return enabled
            .filter { it.hour == now.hour && it.minute == now.minute }
            .singleOrNull()
            ?.scriptId
    }
}
