package com.microauto.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleCodecTest {

    @Test
    fun parsesAndSortsSchedules() {
        val schedules = ScheduleCodec.parse(
            """
            [
              {"scriptId":"b","hour":18,"minute":30,"enabled":true,"alarmId":2},
              {"scriptId":"a","hour":8,"minute":5,"enabled":false,"alarmId":1}
            ]
            """.trimIndent()
        )

        assertEquals(listOf("a", "b"), schedules.map { it.scriptId })
        assertEquals(8, schedules[0].hour)
        assertEquals(false, schedules[0].enabled)
    }

    @Test
    fun skipsMalformedOrOutOfRangeEntries() {
        val schedules = ScheduleCodec.parse(
            """
            [
              {"scriptId":"ok","hour":8,"minute":0,"enabled":true,"alarmId":1},
              {"scriptId":"","hour":9,"minute":0,"enabled":true,"alarmId":2},
              {"scriptId":"bad-hour","hour":24,"minute":0,"enabled":true,"alarmId":3},
              {"scriptId":"bad-minute","hour":9,"minute":60,"enabled":true,"alarmId":4},
              {"scriptId":"no-alarm","hour":9,"minute":0,"enabled":true}
            ]
            """.trimIndent()
        )

        assertEquals(listOf("ok"), schedules.map { it.scriptId })
    }

    @Test
    fun returnsEmptyForCorruptRoot() {
        assertTrue(ScheduleCodec.parse("{not json").isEmpty())
    }

    @Test
    fun encodesAndParsesRoundTrip() {
        val schedules = listOf(
            ScheduledScript("a", 8, 5, true, 1),
            ScheduledScript("b", 18, 30, false, 2)
        )

        assertEquals(schedules, ScheduleCodec.parse(ScheduleCodec.encode(schedules)))
    }

    @Test
    fun migratesLegacyEnabledSchedule() {
        val schedules = ScheduleCodec.migrateLegacy(
            enabled = true,
            hour = 8,
            minute = 5,
            scriptId = "legacy"
        )

        assertEquals(1, schedules.size)
        assertEquals("legacy", schedules[0].scriptId)
        assertEquals(8, schedules[0].hour)
        assertEquals(5, schedules[0].minute)
        assertTrue(schedules[0].enabled)
        assertEquals(1, schedules[0].alarmId)
    }

    @Test
    fun doesNotMigrateDisabledOrUnboundLegacySchedule() {
        assertTrue(
            ScheduleCodec.migrateLegacy(
                enabled = false,
                hour = 8,
                minute = 5,
                scriptId = "legacy"
            ).isEmpty()
        )
        assertTrue(
            ScheduleCodec.migrateLegacy(
                enabled = true,
                hour = 8,
                minute = 5,
                scriptId = null
            ).isEmpty()
        )
    }

    @Test
    fun assignsNextAlarmId() {
        val schedules = listOf(
            ScheduledScript("a", 8, 0, true, 1),
            ScheduledScript("b", 9, 0, true, 7)
        )

        assertEquals(8, ScheduleCodec.nextAlarmId(schedules))
    }
}
