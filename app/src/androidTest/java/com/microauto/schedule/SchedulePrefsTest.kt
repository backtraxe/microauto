package com.microauto.schedule

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.microauto.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun savesAndLoadsSchedule() {
        SchedulePrefs.save(context, "script-1", true, 8, 5)

        assertTrue(SchedulePrefs.isEnabled(context))
        assertEquals("script-1", SchedulePrefs.scriptId(context))
        assertEquals(8, SchedulePrefs.hour(context))
        assertEquals(5, SchedulePrefs.minute(context))
    }

    @Test
    fun clearsScriptIdWithoutDisabling() {
        SchedulePrefs.save(context, "script-1", true, 8, 5)
        SchedulePrefs.setScriptId(context, null)

        assertNull(SchedulePrefs.scriptId(context))
        assertTrue(SchedulePrefs.isEnabled(context))
    }

    @Test
    fun disablesSchedule() {
        SchedulePrefs.save(context, "script-1", true, 8, 5)
        SchedulePrefs.setEnabled(context, false)

        assertEquals(false, SchedulePrefs.isEnabled(context))
    }
}
