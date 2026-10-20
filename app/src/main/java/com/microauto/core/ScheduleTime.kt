package com.microauto.core

import java.time.LocalDateTime

object ScheduleTime {
    fun next(now: LocalDateTime, hour: Int, minute: Int): LocalDateTime {
        require(hour in 0..23) { "hour out of range" }
        require(minute in 0..59) { "minute out of range" }
        var candidate = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
        return candidate
    }

    fun parseTime(value: String): Pair<Int, Int>? {
        val match = Regex("^([01]?\\d|2[0-3]):([0-5]\\d)$").matchEntire(value.trim()) ?: return null
        return match.groupValues[1].toInt() to match.groupValues[2].toInt()
    }
}
