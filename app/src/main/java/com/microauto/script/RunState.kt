package com.microauto.script

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object RunState {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    fun setRunning(value: Boolean) {
        _running.value = value
    }
}
