package com.microauto.script

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object RunState {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _activeScriptId = MutableStateFlow<String?>(null)
    val activeScriptId: StateFlow<String?> = _activeScriptId

    fun start(scriptId: String?) {
        _activeScriptId.value = scriptId
        _running.value = true
    }

    fun setRunning(value: Boolean) {
        _running.value = value
        if (!value) _activeScriptId.value = null
    }

    fun finish() {
        _activeScriptId.value = null
        _running.value = false
    }
}
