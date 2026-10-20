package com.microauto.schedule

import org.json.JSONArray
import org.json.JSONObject

data class ScheduledScript(
    val scriptId: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    val alarmId: Int
)

object ScheduleCodec {
    fun parse(text: String): List<ScheduledScript> {
        val array = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        val result = ArrayList<ScheduledScript>(array.length())
        for (index in 0 until array.length()) {
            val obj = runCatching { array.getJSONObject(index) }.getOrNull() ?: continue
            val schedule = runCatching {
                ScheduledScript(
                    scriptId = obj.getString("scriptId").takeIf { it.isNotBlank() }
                        ?: error("blank scriptId"),
                    hour = obj.getInt("hour"),
                    minute = obj.getInt("minute"),
                    enabled = obj.getBoolean("enabled"),
                    alarmId = obj.getInt("alarmId")
                )
            }.getOrNull() ?: continue
            if (schedule.hour !in 0..23 || schedule.minute !in 0..59 || schedule.alarmId <= 0) {
                continue
            }
            result += schedule
        }
        return result
            .distinctBy { it.scriptId }
            .sortedWith(compareBy({ it.hour }, { it.minute }))
    }

    fun encode(schedules: List<ScheduledScript>): String {
        val array = JSONArray()
        schedules.forEach { schedule ->
            array.put(
                JSONObject().apply {
                    put("scriptId", schedule.scriptId)
                    put("hour", schedule.hour)
                    put("minute", schedule.minute)
                    put("enabled", schedule.enabled)
                    put("alarmId", schedule.alarmId)
                }
            )
        }
        return array.toString()
    }

    fun migrateLegacy(
        enabled: Boolean,
        hour: Int,
        minute: Int,
        scriptId: String?
    ): List<ScheduledScript> {
        if (!enabled || scriptId.isNullOrBlank()) return emptyList()
        return listOf(
            ScheduledScript(
                scriptId = scriptId,
                hour = hour.coerceIn(0, 23),
                minute = minute.coerceIn(0, 59),
                enabled = true,
                alarmId = 1
            )
        )
    }

    fun nextAlarmId(schedules: List<ScheduledScript>): Int =
        (schedules.maxOfOrNull { it.alarmId } ?: 0) + 1
}
