package com.microauto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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

    @Test
    fun parsesMidnightAndNoon() {
        assertEquals(0 to 0, ScheduleTime.parseTime("00:00"))
        assertEquals(12 to 0, ScheduleTime.parseTime("12:00"))
    }

    @Test
    fun rejectsBlankTime() {
        assertNull(ScheduleTime.parseTime(""))
        assertNull(ScheduleTime.parseTime("  "))
    }

    @Test
    fun rollsAcrossMonthAndYear() {
        val now = LocalDateTime.of(2026, 12, 31, 23, 30)

        assertEquals(
            LocalDateTime.of(2027, 1, 1, 8, 0),
            ScheduleTime.next(now, 8, 0)
        )
    }

    @Test
    fun rollsFromLateNight() {
        val now = LocalDateTime.of(2026, 10, 20, 23, 59, 59)

        assertEquals(
            LocalDateTime.of(2026, 10, 21, 0, 0),
            ScheduleTime.next(now, 0, 0)
        )
    }

    @Test
    fun rejectsInvalidHourAndMinute() {
        val now = LocalDateTime.of(2026, 10, 20, 8, 0)

        assertThrows(IllegalArgumentException::class.java) {
            ScheduleTime.next(now, 24, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScheduleTime.next(now, 8, 60)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScheduleTime.next(now, -1, 0)
        }
    }
}
