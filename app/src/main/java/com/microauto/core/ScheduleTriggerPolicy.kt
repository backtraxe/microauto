package com.microauto.core

sealed interface TriggerDecision {
    data object Run : TriggerDecision

    data class Retry(
        val nextAt: Long,
        val deadline: Long
    ) : TriggerDecision

    data class Skip(val reason: String) : TriggerDecision
}

object ScheduleTriggerPolicy {
    const val RETRY_INTERVAL_MILLIS = 2 * 60 * 1000L
    const val RETRY_WINDOW_MILLIS = 60 * 60 * 1000L

    fun decide(
        now: Long,
        isRetry: Boolean,
        deadline: Long,
        screenInteractive: Boolean,
        screenLocked: Boolean
    ): TriggerDecision {
        if (screenInteractive && !screenLocked) return TriggerDecision.Run
        if (!isRetry) {
            return TriggerDecision.Retry(
                nextAt = now + RETRY_INTERVAL_MILLIS,
                deadline = now + RETRY_WINDOW_MILLIS
            )
        }
        if (now >= deadline) return TriggerDecision.Skip("屏幕未解锁")
        return TriggerDecision.Retry(
            nextAt = minOf(now + RETRY_INTERVAL_MILLIS, deadline),
            deadline = deadline
        )
    }
}
