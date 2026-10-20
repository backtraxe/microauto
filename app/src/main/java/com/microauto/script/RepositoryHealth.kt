package com.microauto.script

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object RepositoryHealth {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun report(value: String) {
        _message.value = value
    }

    fun clear() {
        _message.value = null
    }
}
