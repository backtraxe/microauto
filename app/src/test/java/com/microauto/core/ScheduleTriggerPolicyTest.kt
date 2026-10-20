package com.microauto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleTriggerPolicyTest {

    @Test
    fun runsImmediatelyWhenScreenIsOnAndUnlocked() {
        val decision = ScheduleTriggerPolicy.decide(
            now = 1_000,
            isRetry = false,
            deadline = 0,
            screenInteractive = true,
            screenLocked = false
        )

        assertEquals(TriggerDecision.Run, decision)
    }

    @Test
    fun defersFirstTriggerWhenScreenIsOff() {
        val now = 1_000L
        val decision = ScheduleTriggerPolicy.decide(
            now = now,
            isRetry = false,
            deadline = 0,
            screenInteractive = false,
            screenLocked = false
        ) as TriggerDecision.Retry

        assertEquals(now + ScheduleTriggerPolicy.RETRY_INTERVAL_MILLIS, decision.nextAt)
        assertEquals(now + ScheduleTriggerPolicy.RETRY_WINDOW_MILLIS, decision.deadline)
    }

    @Test
    fun defersFirstTriggerWhenDeviceIsLocked() {
        val now = 5_000L
        val decision = ScheduleTriggerPolicy.decide(
            now = now,
            isRetry = false,
            deadline = 0,
            screenInteractive = true,
            screenLocked = true
        ) as TriggerDecision.Retry

        assertEquals(now + ScheduleTriggerPolicy.RETRY_INTERVAL_MILLIS, decision.nextAt)
        assertEquals(now + ScheduleTriggerPolicy.RETRY_WINDOW_MILLIS, decision.deadline)
    }

    @Test
    fun retriesWithinWindow() {
        val now = 10_000L
        val deadline = now + 10 * 60_000L
        val decision = ScheduleTriggerPolicy.decide(
            now = now,
            isRetry = true,
            deadline = deadline,
            screenInteractive = false,
            screenLocked = false
        ) as TriggerDecision.Retry

        assertEquals(now + ScheduleTriggerPolicy.RETRY_INTERVAL_MILLIS, decision.nextAt)
        assertEquals(deadline, decision.deadline)
    }

    @Test
    fun clampsRetryToDeadline() {
        val now = 10_000L
        val deadline = now + 1_000
        val decision = ScheduleTriggerPolicy.decide(
            now = now,
            isRetry = true,
            deadline = deadline,
            screenInteractive = false,
            screenLocked = false
        ) as TriggerDecision.Retry

        assertEquals(deadline, decision.nextAt)
    }

    @Test
    fun skipsAfterWindowExpires() {
        val now = 10_000L
        val decision = ScheduleTriggerPolicy.decide(
            now = now,
            isRetry = true,
            deadline = now,
            screenInteractive = false,
            screenLocked = false
        )

        assertTrue(decision is TriggerDecision.Skip)
    }
}
