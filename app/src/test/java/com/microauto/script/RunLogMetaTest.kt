package com.microauto.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunLogMetaTest {

    @Test
    fun mapRoundTripKeepsAllFields() {
        val meta = RunLogMeta(
            scriptId = "script-1",
            scriptName = "签到脚本",
            source = RunSource.SCHEDULE,
            startedAt = 1_000L,
            finishedAt = 6_100L,
            status = RunPhase.FAILED,
            errorLine = 3,
            errorMessage = "ReferenceError: foo is not defined",
        )

        val restored = RunLogMeta.fromMap(meta.toMap())

        assertEquals(meta, restored)
    }

    @Test
    fun runningMetaWithoutFinishRoundTrips() {
        val meta = RunLogMeta(
            scriptId = null,
            scriptName = "看界面",
            source = RunSource.DUMP,
            startedAt = 42L,
            finishedAt = null,
            status = RunPhase.RUNNING,
            errorLine = null,
            errorMessage = null,
        )

        assertEquals(meta, RunLogMeta.fromMap(meta.toMap()))
    }

    @Test
    fun legacyMissingMetaReturnsNull() {
        assertNull(RunLogMeta.fromMap(emptyMap()))
        assertNull(RunLogMeta.fromMap(mapOf("script_name" to "旧日志")))
    }

    @Test
    fun unknownEnumsAndBadNumbersAreTolerated() {
        val restored = RunLogMeta.fromMap(
            mapOf(
                "script_name" to "旧日志",
                "source" to "UNKNOWN",
                "status" to "SUCCESS",
                "started_at" to "not-a-number",
                "error_line" to "x",
            )
        )

        assertEquals(RunSource.MANUAL, restored?.source)
        assertEquals(0L, restored?.startedAt)
        assertNull(restored?.errorLine)
    }

    @Test
    fun unknownStatusReturnsNull() {
        assertNull(
            RunLogMeta.fromMap(
                mapOf(
                    "script_name" to "旧日志",
                    "status" to "UNKNOWN",
                )
            )
        )
    }

    @Test
    fun interruptedRunUsesLogTimestampAsFinishedAt() {
        val meta = runningMeta(startedAt = 1_000L)

        val normalized = normalizeInterruptedMeta(meta, logModifiedAt = 5_000L)

        assertEquals(RunPhase.STOPPED, normalized.status)
        assertEquals(5_000L, normalized.finishedAt)
    }

    @Test
    fun interruptedRunWithStaleTimestampHasNoFinishedAt() {
        val meta = runningMeta(startedAt = 5_000L)

        val normalized = normalizeInterruptedMeta(meta, logModifiedAt = 4_999L)

        assertEquals(RunPhase.STOPPED, normalized.status)
        assertNull(normalized.finishedAt)
    }

    @Test
    fun interruptedRunWithoutLogFileHasNoFinishedAt() {
        val meta = runningMeta(startedAt = 5_000L)

        val normalized = normalizeInterruptedMeta(meta, logModifiedAt = 0L)

        assertEquals(RunPhase.STOPPED, normalized.status)
        assertNull(normalized.finishedAt)
    }

    @Test
    fun completedMetaIsNotNormalized() {
        val meta = RunLogMeta(
            scriptId = "script-1",
            scriptName = "签到脚本",
            source = RunSource.MANUAL,
            startedAt = 1_000L,
            finishedAt = 2_000L,
            status = RunPhase.SUCCESS,
            errorLine = null,
            errorMessage = null,
        )

        assertEquals(meta, normalizeInterruptedMeta(meta, logModifiedAt = 9_000L))
    }

    private fun runningMeta(startedAt: Long): RunLogMeta = RunLogMeta(
        scriptId = "script-1",
        scriptName = "签到脚本",
        source = RunSource.MANUAL,
        startedAt = startedAt,
        finishedAt = null,
        status = RunPhase.RUNNING,
        errorLine = null,
        errorMessage = null,
    )
}
