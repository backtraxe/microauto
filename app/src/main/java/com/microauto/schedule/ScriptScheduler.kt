package com.microauto.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.microauto.core.ScheduleTime
import java.time.LocalDateTime
import java.time.ZoneId

object ScriptScheduler {
    const val ACTION_RETRY = "com.microauto.action.RETRY"
    const val EXTRA_SCRIPT_ID = "script_id"

    private const val DAILY_REQUEST_BASE = 10_000
    private const val RETRY_REQUEST_BASE = 1_000_000
    private const val LEGACY_REQUEST_CODE = 2001
    private const val LEGACY_RETRY_REQUEST_CODE = 2002

    fun rescheduleAll(context: Context) {
        cancelLegacy(context)
        val schedules = SchedulePrefs.schedules(context)
        schedules.forEach { cancelDaily(context, it) }
        schedules.filter { it.enabled }.forEach { scheduleScript(context, it) }
    }

    fun scheduleScript(context: Context, schedule: ScheduledScript) {
        if (!schedule.enabled) {
            cancelScript(context, schedule)
            return
        }
        val next = ScheduleTime.next(
            LocalDateTime.now(),
            schedule.hour,
            schedule.minute
        )
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        setAlarm(alarmManager, triggerAt, dailyPendingIntent(context, schedule))
    }

    fun cancelScript(context: Context, schedule: ScheduledScript) {
        cancelDaily(context, schedule)
        cancelRetry(context, schedule)
    }

    private fun cancelDaily(context: Context, schedule: ScheduledScript) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(dailyPendingIntent(context, schedule))
    }

    fun scheduleRetry(
        context: Context,
        schedule: ScheduledScript,
        triggerAtMillis: Long
    ): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        return runCatching {
            setAlarm(alarmManager, triggerAtMillis, retryPendingIntent(context, schedule))
            true
        }.getOrDefault(false)
    }

    fun cancelRetry(context: Context, schedule: ScheduledScript) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(retryPendingIntent(context, schedule))
    }

    fun restorePending(context: Context) {
        val now = System.currentTimeMillis()
        SchedulePrefs.pendingScriptIds(context).forEach { scriptId ->
            val schedule = SchedulePrefs.scheduleFor(context, scriptId)
            val deadline = SchedulePrefs.pendingDeadline(context, scriptId)
            when {
                schedule == null || !schedule.enabled -> {
                    SchedulePrefs.clearPending(context, scriptId)
                }

                deadline > now -> {
                    scheduleRetry(context, schedule, now + 30_000)
                }

                else -> {
                    SchedulePrefs.clearPending(context, scriptId)
                }
            }
        }
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        SchedulePrefs.schedules(context).forEach { schedule ->
            alarmManager.cancel(dailyPendingIntent(context, schedule))
            alarmManager.cancel(retryPendingIntent(context, schedule))
        }
        cancelLegacy(context)
    }

    private fun setAlarm(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            alarmManager.canScheduleExactAlarms()
        ) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    private fun dailyPendingIntent(
        context: Context,
        schedule: ScheduledScript
    ): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java)
            .putExtra(EXTRA_SCRIPT_ID, schedule.scriptId)
        return PendingIntent.getBroadcast(
            context,
            DAILY_REQUEST_BASE + schedule.alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun retryPendingIntent(
        context: Context,
        schedule: ScheduledScript
    ): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java)
            .setAction(ACTION_RETRY)
            .putExtra(EXTRA_SCRIPT_ID, schedule.scriptId)
        return PendingIntent.getBroadcast(
            context,
            RETRY_REQUEST_BASE + schedule.alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelLegacy(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val daily = PendingIntent.getBroadcast(
            context,
            LEGACY_REQUEST_CODE,
            Intent(context, ScheduleReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val retry = PendingIntent.getBroadcast(
            context,
            LEGACY_RETRY_REQUEST_CODE,
            Intent(context, ScheduleReceiver::class.java).setAction(ACTION_RETRY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(daily)
        alarmManager.cancel(retry)
    }
}
