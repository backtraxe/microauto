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
    private const val REQUEST_CODE = 2001

    fun scheduleNext(context: Context) {
        if (!SchedulePrefs.isEnabled(context)) {
            cancel(context)
            return
        }
        val next = ScheduleTime.next(
            LocalDateTime.now(),
            SchedulePrefs.hour(context),
            SchedulePrefs.minute(context)
        )
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = pendingIntent(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
