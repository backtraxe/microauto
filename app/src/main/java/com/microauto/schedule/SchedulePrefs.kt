package com.microauto.schedule

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.microauto.script.RepositoryHealth

object SchedulePrefs {
    private const val NAME = "schedule"
    private const val KEY_SCHEDULES = "schedules"
    private const val KEY_NEXT_ALARM_ID = "next_alarm_id"

    private const val LEGACY_KEY_ENABLED = "enabled"
    private const val LEGACY_KEY_HOUR = "hour"
    private const val LEGACY_KEY_MINUTE = "minute"
    private const val LEGACY_KEY_SCRIPT_ID = "script_id"

    private const val PENDING_PREFIX = "pending_deadline_"
    private const val LEGACY_KEY_PENDING_SCRIPT_ID = "pending_script_id"
    private const val LEGACY_KEY_PENDING_DEADLINE = "pending_deadline"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun schedules(context: Context): List<ScheduledScript> {
        val prefs = prefs(context)
        val raw = prefs.getString(KEY_SCHEDULES, null)
        if (raw != null) {
            val parsed = ScheduleCodec.parse(raw)
            if (parsed.isEmpty() && raw.trim() != "[]") {
                prefs.edit { putString(KEY_SCHEDULES, "[]") }
                RepositoryHealth.report("定时配置损坏，已重置。")
            }
            return parsed
        }

        val migrated = ScheduleCodec.migrateLegacy(
            enabled = prefs.getBoolean(LEGACY_KEY_ENABLED, false),
            hour = prefs.getInt(LEGACY_KEY_HOUR, 8),
            minute = prefs.getInt(LEGACY_KEY_MINUTE, 0),
            scriptId = prefs.getString(LEGACY_KEY_SCRIPT_ID, null)
        )
        writeSchedules(prefs, migrated)
        if (migrated.isNotEmpty()) {
            prefs.edit {
                putInt(KEY_NEXT_ALARM_ID, maxOf(2, prefs.getInt(KEY_NEXT_ALARM_ID, 0)))
            }
        }
        prefs.edit {
            remove(LEGACY_KEY_ENABLED)
            remove(LEGACY_KEY_HOUR)
            remove(LEGACY_KEY_MINUTE)
            remove(LEGACY_KEY_SCRIPT_ID)
        }
        return migrated
    }

    fun scheduleFor(context: Context, scriptId: String): ScheduledScript? =
        schedules(context).firstOrNull { it.scriptId == scriptId }

    fun enabled(context: Context): List<ScheduledScript> =
        schedules(context).filter { it.enabled }

    fun isEnabled(context: Context): Boolean = enabled(context).isNotEmpty()

    fun save(
        context: Context,
        scriptId: String,
        enabled: Boolean,
        hour: Int,
        minute: Int
    ): ScheduledScript {
        val prefs = prefs(context)
        val schedules = schedules(context).toMutableList()
        val index = schedules.indexOfFirst { it.scriptId == scriptId }
        val schedule = if (index >= 0) {
            schedules[index].copy(
                hour = hour.coerceIn(0, 23),
                minute = minute.coerceIn(0, 59),
                enabled = enabled
            )
        } else {
            ScheduledScript(
                scriptId = scriptId,
                hour = hour.coerceIn(0, 23),
                minute = minute.coerceIn(0, 59),
                enabled = enabled,
                alarmId = nextAlarmId(prefs, schedules)
            )
        }
        if (index >= 0) {
            schedules[index] = schedule
        } else {
            schedules += schedule
        }
        writeSchedules(prefs, schedules)
        return schedule
    }

    fun remove(context: Context, scriptId: String) {
        val prefs = prefs(context)
        writeSchedules(prefs, schedules(context).filterNot { it.scriptId == scriptId })
        clearPending(prefs, scriptId)
    }

    fun setPending(context: Context, scriptId: String, deadline: Long) {
        prefs(context).edit { putLong(PENDING_PREFIX + scriptId, deadline) }
    }

    fun pendingDeadline(context: Context, scriptId: String): Long =
        prefs(context).getLong(PENDING_PREFIX + scriptId, 0L)

    fun pendingScriptIds(context: Context): Set<String> =
        prefs(context).all.keys
            .filter { it.startsWith(PENDING_PREFIX) }
            .mapTo(mutableSetOf()) { it.removePrefix(PENDING_PREFIX) }

    fun clearPending(context: Context, scriptId: String) {
        clearPending(prefs(context), scriptId)
    }

    fun clearAllPending(context: Context) {
        val prefs = prefs(context)
        prefs.edit {
            prefs.all.keys
                .filter { it.startsWith(PENDING_PREFIX) }
                .forEach { remove(it) }
            remove(LEGACY_KEY_PENDING_SCRIPT_ID)
            remove(LEGACY_KEY_PENDING_DEADLINE)
        }
    }

    private fun clearPending(prefs: SharedPreferences, scriptId: String) {
        prefs.edit { remove(PENDING_PREFIX + scriptId) }
    }

    private fun nextAlarmId(
        prefs: SharedPreferences,
        schedules: List<ScheduledScript>
    ): Int {
        val stored = prefs.getInt(KEY_NEXT_ALARM_ID, 0)
        val candidate = maxOf(stored, ScheduleCodec.nextAlarmId(schedules))
        prefs.edit { putInt(KEY_NEXT_ALARM_ID, candidate + 1) }
        return candidate
    }

    private fun writeSchedules(prefs: SharedPreferences, schedules: List<ScheduledScript>) {
        prefs.edit { putString(KEY_SCHEDULES, ScheduleCodec.encode(schedules)) }
    }
}
