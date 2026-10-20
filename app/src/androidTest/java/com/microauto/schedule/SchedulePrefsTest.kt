package com.microauto.schedule

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.microauto.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SchedulePrefsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        TestData.reset()
    }

    @Test
    fun savesAndLoadsMultipleSchedules() {
        val first = SchedulePrefs.save(context, "script-1", true, 8, 5)
        val second = SchedulePrefs.save(context, "script-2", true, 18, 30)

        assertEquals(2, SchedulePrefs.schedules(context).size)
        assertEquals(listOf("script-1", "script-2"), SchedulePrefs.enabled(context).map { it.scriptId })
        assertEquals(8, SchedulePrefs.scheduleFor(context, "script-1")?.hour)
        assertEquals(30, SchedulePrefs.scheduleFor(context, "script-2")?.minute)
        assertNotEquals(first.alarmId, second.alarmId)
    }

    @Test
    fun updatesExistingScheduleWithoutChangingAlarmId() {
        val created = SchedulePrefs.save(context, "script-1", true, 8, 5)

        val updated = SchedulePrefs.save(context, "script-1", true, 9, 45)

        assertEquals(created.alarmId, updated.alarmId)
        assertEquals(9, SchedulePrefs.scheduleFor(context, "script-1")?.hour)
        assertEquals(45, SchedulePrefs.scheduleFor(context, "script-1")?.minute)
    }

    @Test
    fun disablingScheduleKeepsAlarmIdButRemovesItFromEnabled() {
        val created = SchedulePrefs.save(context, "script-1", true, 8, 5)
        SchedulePrefs.save(context, "script-1", false, 8, 5)

        val schedule = SchedulePrefs.scheduleFor(context, "script-1")
        assertEquals(false, schedule?.enabled)
        assertEquals(created.alarmId, schedule?.alarmId)
        assertTrue(SchedulePrefs.enabled(context).isEmpty())
    }

    @Test
    fun removesScheduleAndItsPendingRun() {
        SchedulePrefs.save(context, "script-1", true, 8, 5)
        SchedulePrefs.setPending(context, "script-1", 123_456L)

        SchedulePrefs.remove(context, "script-1")

        assertEquals(null, SchedulePrefs.scheduleFor(context, "script-1"))
        assertEquals(0L, SchedulePrefs.pendingDeadline(context, "script-1"))
    }

    @Test
    fun migratesLegacySingleSchedule() {
        SchedulePrefs.prefs(context).edit()
            .putBoolean("enabled", true)
            .putInt("hour", 7)
            .putInt("minute", 45)
            .putString("script_id", "legacy")
            .commit()

        val schedules = SchedulePrefs.schedules(context)

        assertEquals(1, schedules.size)
        assertEquals("legacy", schedules[0].scriptId)
        assertEquals(7, schedules[0].hour)
        assertEquals(45, schedules[0].minute)
        assertTrue(schedules[0].enabled)
        assertFalse(SchedulePrefs.prefs(context).contains("enabled"))
        assertFalse(SchedulePrefs.prefs(context).contains("script_id"))
    }

    @Test
    fun savesAndClearsPendingRunPerScript() {
        SchedulePrefs.setPending(context, "script-1", 123L)
        SchedulePrefs.setPending(context, "script-2", 456L)

        assertEquals(123L, SchedulePrefs.pendingDeadline(context, "script-1"))
        assertEquals(setOf("script-1", "script-2"), SchedulePrefs.pendingScriptIds(context))

        SchedulePrefs.clearPending(context, "script-1")

        assertEquals(0L, SchedulePrefs.pendingDeadline(context, "script-1"))
        assertEquals(setOf("script-2"), SchedulePrefs.pendingScriptIds(context))
    }
}
