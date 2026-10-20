package com.microauto.script

object ScriptExecutionGuard {
    private data class State(
        val deadlineMillis: Long,
        val isCancelled: () -> Boolean
    )

    private val current = ThreadLocal<State>()

    fun install(deadlineMillis: Long, isCancelled: () -> Boolean) {
        current.set(State(deadlineMillis, isCancelled))
    }

    fun check() {
        val state = current.get() ?: return
        if (state.isCancelled()) throw ScriptCancelledException()
        if (System.currentTimeMillis() > state.deadlineMillis) throw ScriptTimeoutException()
    }

    fun clear() {
        current.remove()
    }
}
