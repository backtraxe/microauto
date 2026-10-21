package com.microauto.script

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RunStateTest {

    @Before
    fun setUp() {
        RunState.finish()
    }

    @After
    fun tearDown() {
        RunState.finish()
    }

    @Test
    fun startTracksActiveScriptAndFinishClearsIt() {
        RunState.start("script-1", 42L)

        assertTrue(RunState.running.value)
        assertEquals("script-1", RunState.activeScriptId.value)
        assertEquals(42L, RunState.activeToken.value)

        RunState.finish()

        assertFalse(RunState.running.value)
        assertNull(RunState.activeScriptId.value)
        assertNull(RunState.activeToken.value)
    }

    @Test
    fun setRunningFalseClearsActiveScriptAndToken() {
        RunState.start(null, 7L)
        assertNull(RunState.activeScriptId.value)
        assertEquals(7L, RunState.activeToken.value)

        RunState.setRunning(false)

        assertFalse(RunState.running.value)
        assertNull(RunState.activeScriptId.value)
        assertNull(RunState.activeToken.value)
    }

    @Test
    fun startReplacesPreviousScriptAndFinishIsIdempotent() {
        RunState.start("a")
        RunState.start("b")
        assertEquals("b", RunState.activeScriptId.value)

        RunState.finish()
        RunState.finish()

        assertFalse(RunState.running.value)
        assertNull(RunState.activeScriptId.value)
    }

    @Test
    fun startCreatesStartingSnapshotWithSource() {
        RunState.start("script-1", 42L, "签到脚本", RunSource.SCHEDULE)

        val snapshot = RunState.snapshot.value ?: error("缺少快照")
        assertEquals("script-1", snapshot.scriptId)
        assertEquals("签到脚本", snapshot.scriptName)
        assertEquals(RunPhase.STARTING, snapshot.phase)
        assertEquals(RunSource.SCHEDULE, snapshot.source)
        assertTrue(snapshot.startedAt > 0)
        assertNull(snapshot.finishedAt)
    }

    @Test
    fun markRunningMovesPhaseWithoutDroppingIdentity() {
        RunState.start("script-1", 42L, "签到脚本", RunSource.MANUAL)

        RunState.markRunning()

        val snapshot = RunState.snapshot.value ?: error("缺少快照")
        assertEquals(RunPhase.RUNNING, snapshot.phase)
        assertEquals("script-1", snapshot.scriptId)
        assertEquals("签到脚本", snapshot.scriptName)
        assertTrue(RunState.running.value)
    }

    @Test
    fun completeSuccessKeepsResultSnapshotAndClearsActiveRun() {
        RunState.start("script-1", 42L, "签到脚本", RunSource.MANUAL)
        RunState.markRunning()

        RunState.complete(RunPhase.SUCCESS)

        val snapshot = RunState.snapshot.value ?: error("缺少快照")
        assertEquals(RunPhase.SUCCESS, snapshot.phase)
        assertTrue((snapshot.finishedAt ?: 0L) >= snapshot.startedAt)
        assertFalse(RunState.running.value)
        assertNull(RunState.activeScriptId.value)
        assertNull(RunState.activeToken.value)
    }

    @Test
    fun completeFailedKeepsErrorDetails() {
        RunState.start("script-1", 42L, "签到脚本", RunSource.MANUAL)
        RunState.markRunning()
        RunState.recordError(3, "ReferenceError: foo is not defined")

        RunState.complete(RunPhase.FAILED)

        val snapshot = RunState.snapshot.value ?: error("缺少快照")
        assertEquals(RunPhase.FAILED, snapshot.phase)
        assertEquals(3, snapshot.errorLine)
        assertEquals("ReferenceError: foo is not defined", snapshot.errorMessage)
    }

    @Test
    fun completeStoppedMarksStopPhase() {
        RunState.start("script-1", 42L, "签到脚本", RunSource.MANUAL)
        RunState.markRunning()

        RunState.complete(RunPhase.STOPPED)

        assertEquals(RunPhase.STOPPED, RunState.snapshot.value?.phase)
        assertFalse(RunState.running.value)
    }

    @Test
    fun stopIsIgnoredAfterCompletion() {
        RunState.start("script-1", 42L, "签到脚本", RunSource.MANUAL)
        RunState.complete(RunPhase.SUCCESS)

        RunState.stop()

        assertEquals(RunPhase.SUCCESS, RunState.snapshot.value?.phase)
    }

    @Test
    fun failStartCreatesFailedSnapshotWithoutActiveRun() {
        RunState.failStart("无法启动前台服务", "script-1", "签到脚本", RunSource.MANUAL)

        val snapshot = RunState.snapshot.value ?: error("缺少快照")
        assertEquals(RunPhase.FAILED, snapshot.phase)
        assertEquals("无法启动前台服务", snapshot.errorMessage)
        assertFalse(RunState.running.value)
        assertNull(RunState.activeToken.value)
    }

    @Test
    fun dismissClearsResultSnapshot() {
        RunState.start("script-1", 42L, "签到脚本", RunSource.MANUAL)
        RunState.complete(RunPhase.SUCCESS)

        RunState.dismiss()

        assertNull(RunState.snapshot.value)
        assertFalse(RunState.running.value)
    }

    @Test
    fun markRunningWithoutSnapshotIsNoOp() {
        RunState.markRunning()

        assertNull(RunState.snapshot.value)
        assertFalse(RunState.running.value)
    }

    @Test
    fun recordErrorIsIgnoredAfterCompletion() {
        RunState.start("script-1", 42L, "签到脚本", RunSource.MANUAL)
        val startedAt = RunState.snapshot.value!!.startedAt
        RunState.complete(RunPhase.SUCCESS)

        RunState.recordError(3, "late error", startedAt)

        assertEquals(RunPhase.SUCCESS, RunState.snapshot.value?.phase)
        assertNull(RunState.snapshot.value?.errorLine)
    }

    @Test
    fun lateBackgroundMigrationsDoNotOverwriteNewRun() {
        RunState.start("old", null, "旧脚本", RunSource.MANUAL, startedAt = 1_000L)
        val oldStartedAt = RunState.snapshot.value!!.startedAt
        RunState.complete(RunPhase.SUCCESS)

        RunState.start("new", null, "新脚本", RunSource.MANUAL, startedAt = 2_000L)
        val newStartedAt = RunState.snapshot.value!!.startedAt

        RunState.markRunning(oldStartedAt)
        RunState.recordError(3, "old error", oldStartedAt)

        val snapshot = RunState.snapshot.value ?: error("缺少快照")
        assertEquals("新脚本", snapshot.scriptName)
        assertEquals(RunPhase.STARTING, snapshot.phase)
        assertNull(snapshot.errorLine)

        RunState.markRunning(newStartedAt)
        assertEquals(RunPhase.RUNNING, RunState.snapshot.value?.phase)
    }

    @Test
    fun recordErrorOnlyAppliesWhileRunIsActive() {
        RunState.start("script-1", null, "签到脚本", RunSource.MANUAL)
        RunState.recordError(3, "ReferenceError", RunState.snapshot.value!!.startedAt)
        assertEquals(3, RunState.snapshot.value?.errorLine)

        RunState.complete(RunPhase.FAILED)
        RunState.recordError(9, "after failure", null)
        assertEquals(3, RunState.snapshot.value?.errorLine)
    }

    @Test
    fun failStartIsIgnoredWhileRunIsActive() {
        RunState.start("script-1", 42L, "签到脚本", RunSource.MANUAL)

        RunState.failStart("启动失败", "script-1", "签到脚本", RunSource.MANUAL)

        assertEquals(RunPhase.STARTING, RunState.snapshot.value?.phase)
        assertNull(RunState.snapshot.value?.errorMessage)
        assertTrue(RunState.running.value)
        assertEquals(42L, RunState.activeToken.value)
    }

    @Test
    fun failStartReplacesFinishedResult() {
        RunState.start("script-1", 42L, "签到脚本", RunSource.MANUAL)
        RunState.complete(RunPhase.SUCCESS)

        RunState.failStart("无法启动前台服务", "script-1", "签到脚本", RunSource.MANUAL)

        assertEquals(RunPhase.FAILED, RunState.snapshot.value?.phase)
        assertEquals("无法启动前台服务", RunState.snapshot.value?.errorMessage)
    }

    @Test
    fun dismissWithIdentityKeepsNewerRun() {
        RunState.start("old", null, "旧脚本", RunSource.MANUAL, startedAt = 1_000L)
        val oldStartedAt = RunState.snapshot.value!!.startedAt
        RunState.complete(RunPhase.SUCCESS)
        RunState.start("new", null, "新脚本", RunSource.MANUAL, startedAt = 2_000L)
        val newStartedAt = RunState.snapshot.value!!.startedAt

        RunState.dismiss(oldStartedAt)

        assertEquals("新脚本", RunState.snapshot.value?.scriptName)
        assertTrue(RunState.running.value)

        RunState.dismiss(newStartedAt)

        assertNull(RunState.snapshot.value)
        assertFalse(RunState.running.value)
    }

    @Test
    fun backgroundMigrationAfterCompletionIsIgnored() {
        RunState.start("script-1", 42L, "签到脚本", RunSource.MANUAL)
        val startedAt = RunState.snapshot.value!!.startedAt
        val release = CountDownLatch(1)
        val migrated = CountDownLatch(1)
        val worker = Thread {
            await(release)
            RunState.recordError(3, "late ReferenceError", startedAt)
            migrated.countDown()
        }

        worker.start()
        RunState.complete(RunPhase.SUCCESS)
        release.countDown()
        assertTrue(migrated.await(2, TimeUnit.SECONDS))
        worker.join(2_000)

        assertEquals(RunPhase.SUCCESS, RunState.snapshot.value?.phase)
        assertNull(RunState.snapshot.value?.errorLine)
    }

    private fun await(latch: CountDownLatch) {
        check(latch.await(2, TimeUnit.SECONDS)) { "等待超时" }
    }
}
