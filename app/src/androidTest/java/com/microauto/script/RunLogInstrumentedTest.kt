package com.microauto.script

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.microauto.TestData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RunLogInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val logFile: File
        get() = File(context.filesDir, "latest.log")

    @Before
    fun setUp() {
        TestData.reset()
        RunLog.init(context)
    }

    @After
    fun tearDown() {
        RunLog.clear()
    }

    @Test
    fun interruptedRunUsesLogTimestampAndPersistsNormalizedMeta() {
        val startedAt = System.currentTimeMillis() - 10_000
        RunLog.begin("script-1", "签到脚本", RunSource.MANUAL, startedAt = startedAt)
        RunLog.append("partial output")
        assertTrue(logFile.setLastModified(startedAt + 5_000))
        val modifiedAt = logFile.lastModified()

        RunLog.init(context)

        val meta = RunLog.meta.value ?: error("缺少 meta")
        assertEquals(RunPhase.STOPPED, meta.status)
        assertEquals(modifiedAt, meta.finishedAt)
        val prefs = context.getSharedPreferences("run_log", Context.MODE_PRIVATE)
        assertEquals("STOPPED", prefs.getString("status", null))
        assertEquals(modifiedAt.toString(), prefs.getString("finished_at", null))
    }

    @Test
    fun interruptedRunWithTimestampBeforeStartHasNoFinishedAt() {
        val startedAt = System.currentTimeMillis() - 1_000
        RunLog.begin("script-1", "签到脚本", RunSource.MANUAL, startedAt = startedAt)
        RunLog.append("partial output")
        assertTrue(logFile.setLastModified(startedAt - 1_000))

        RunLog.init(context)

        val meta = RunLog.meta.value ?: error("缺少 meta")
        assertEquals(RunPhase.STOPPED, meta.status)
        assertNull(meta.finishedAt)
        val prefs = context.getSharedPreferences("run_log", Context.MODE_PRIVATE)
        assertEquals("STOPPED", prefs.getString("status", null))
        assertNull(prefs.getString("finished_at", null))
    }
}
