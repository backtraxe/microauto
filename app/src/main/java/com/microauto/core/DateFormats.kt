package com.microauto.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateFormats {
    private const val PATTERN = "yyyy/MM/dd HH:mm:ss"
    private const val TIME_PATTERN = "HH:mm"

    fun format(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern(PATTERN, Locale.getDefault())
            .format(Instant.ofEpochMilli(epochMillis).atZone(zoneId))

    fun formatTime(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern(TIME_PATTERN, Locale.getDefault())
            .format(Instant.ofEpochMilli(epochMillis).atZone(zoneId))
}
