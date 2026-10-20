package com.microauto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class ScheduleTimeTest {

    @Test
    fun schedulesLaterToday() {
        val now = LocalDateTime.of(2026, 10, 20, 7, 0)

        assertEquals(
            LocalDateTime.of(2026, 10, 20, 8, 0),
            ScheduleTime.next(now, 8, 0)
        )
    }

    @Test
    fun rollsToTomorrowWhenTimePassed() {
        val now = LocalDateTime.of(2026, 10, 20, 9, 0)

        assertEquals(
            LocalDateTime.of(2026, 10, 21, 8, 0),
            ScheduleTime.next(now, 8, 0)
        )
    }

    @Test
    fun rollsWhenExactlyNow() {
        val now = LocalDateTime.of(2026, 10, 20, 8, 0)

        assertEquals(
            LocalDateTime.of(2026, 10, 21, 8, 0),
            ScheduleTime.next(now, 8, 0)
        )
    }

    @Test
    fun parsesValidTime() {
        assertEquals(8 to 5, ScheduleTime.parseTime("08:05"))
        assertEquals(23 to 59, ScheduleTime.parseTime("23:59"))
        assertEquals(8 to 0, ScheduleTime.parseTime("8:00"))
    }

    @Test
    fun rejectsInvalidTime() {
        assertNull(ScheduleTime.parseTime("24:00"))
        assertNull(ScheduleTime.parseTime("8:5"))
        assertNull(ScheduleTime.parseTime("08-00"))
    }
}
