package com.microauto.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class DateFormatsTest {

    @Test
    fun formatsWithUtcZone() {
        val epoch = Instant.parse("2026-10-21T04:57:33Z").toEpochMilli()

        assertEquals(
            "2026/10/21 04:57:33",
            DateFormats.format(epoch, ZoneOffset.UTC)
        )
    }

    @Test
    fun formatsWithLocalZoneOffset() {
        val epoch = Instant.parse("2026-10-21T04:57:33Z").toEpochMilli()

        assertEquals(
            "2026/10/21 12:57:33",
            DateFormats.format(epoch, ZoneId.of("Asia/Shanghai"))
        )
    }

    @Test
    fun formatsTimeWithLocalZoneOffset() {
        val epoch = Instant.parse("2026-10-21T04:57:33Z").toEpochMilli()

        assertEquals("04:57", DateFormats.formatTime(epoch, ZoneOffset.UTC))
        assertEquals("12:57", DateFormats.formatTime(epoch, ZoneId.of("Asia/Shanghai")))
    }
}
