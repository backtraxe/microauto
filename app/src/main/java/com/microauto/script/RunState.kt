package com.microauto.script

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object RunState {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _activeScriptId = MutableStateFlow<String?>(null)
    val activeScriptId: StateFlow<String?> = _activeScriptId

    private val _activeToken = MutableStateFlow<Long?>(null)
    val activeToken: StateFlow<Long?> = _activeToken

    fun start(scriptId: String?, token: Long? = null) {
        _activeScriptId.value = scriptId
        _activeToken.value = token
        _running.value = true
    }

    fun setRunning(value: Boolean) {
        _running.value = value
        if (!value) {
            _activeScriptId.value = null
            _activeToken.value = null
        }
    }

    fun finish() {
        _activeScriptId.value = null
        _activeToken.value = null
        _running.value = false
    }
}
