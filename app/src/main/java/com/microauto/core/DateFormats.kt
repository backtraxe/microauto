package com.microauto.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateFormats {
    private const val PATTERN = "yyyy/MM/dd HH:mm:ss"
    private const val TIME_PATTERN = "HH:mm"
    private val WEEKDAYS = listOf(
        "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"
    )

    fun format(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern(PATTERN, Locale.getDefault())
            .format(Instant.ofEpochMilli(epochMillis).atZone(zoneId))

    fun formatTime(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern(TIME_PATTERN, Locale.getDefault())
            .format(Instant.ofEpochMilli(epochMillis).atZone(zoneId))

    fun formatMonthDay(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
        return "${date.monthValue} 月 ${date.dayOfMonth} 日"
    }

    fun formatHomeDate(date: LocalDate): String =
        "${date.monthValue} 月 ${date.dayOfMonth} 日 · ${WEEKDAYS[date.dayOfWeek.value - 1]}"

    fun greeting(hour: Int): String = when {
        hour < 12 -> "早上好"
        hour < 18 -> "下午好"
        else -> "晚上好"
    }
}
