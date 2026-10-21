package com.microauto.script

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

enum class RunPhase { STARTING, RUNNING, SUCCESS, FAILED, STOPPED }

enum class RunSource { MANUAL, SCHEDULE, DUMP }

data class RunSnapshot(
    val scriptId: String?,
    val scriptName: String,
    val phase: RunPhase,
    val source: RunSource,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val errorLine: Int? = null,
    val errorMessage: String? = null,
)

object RunState {
    private val _snapshot = MutableStateFlow<RunSnapshot?>(null)
    val snapshot: StateFlow<RunSnapshot?> = _snapshot

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _activeScriptId = MutableStateFlow<String?>(null)
    val activeScriptId: StateFlow<String?> = _activeScriptId

    private val _activeToken = MutableStateFlow<Long?>(null)
    val activeToken: StateFlow<Long?> = _activeToken

    fun start(
        scriptId: String?,
        token: Long? = null,
        scriptName: String = "脚本",
        source: RunSource = RunSource.MANUAL,
        startedAt: Long = System.currentTimeMillis(),
    ) {
        _snapshot.value = RunSnapshot(
            scriptId = scriptId,
            scriptName = scriptName,
            phase = RunPhase.STARTING,
            source = source,
            startedAt = startedAt,
        )
        _activeScriptId.value = scriptId
        _activeToken.value = token
        _running.value = true
    }

    fun markRunning(expectedStartedAt: Long? = null) {
        _snapshot.update { current ->
            if (current == null || !current.phase.isActive()) return@update current
            if (expectedStartedAt != null && current.startedAt != expectedStartedAt) {
                return@update current
            }
            current.copy(phase = RunPhase.RUNNING)
        }
    }

    fun recordError(line: Int?, message: String?, expectedStartedAt: Long? = null) {
        _snapshot.update { current ->
            if (current == null || !current.phase.isActive()) return@update current
            if (expectedStartedAt != null && current.startedAt != expectedStartedAt) {
                return@update current
            }
            current.copy(errorLine = line, errorMessage = message)
        }
    }

    fun complete(phase: RunPhase, finishedAt: Long = System.currentTimeMillis()) {
        val previous = _snapshot.getAndUpdate { current ->
            if (current == null || !current.phase.isActive()) {
                current
            } else {
                current.copy(phase = phase, finishedAt = finishedAt)
            }
        }
        if (previous?.phase?.isActive() == true) clearActiveRun()
    }

    fun stop(finishedAt: Long = System.currentTimeMillis()) {
        val previous = _snapshot.getAndUpdate { current ->
            if (current == null || !current.phase.isActive()) {
                current
            } else {
                current.copy(phase = RunPhase.STOPPED, finishedAt = finishedAt)
            }
        }
        if (previous?.phase?.isActive() == true) clearActiveRun()
    }

    fun failStart(
        message: String,
        scriptId: String? = null,
        scriptName: String = "脚本",
        source: RunSource = RunSource.MANUAL,
        startedAt: Long = System.currentTimeMillis(),
        finishedAt: Long = startedAt,
    ) {
        val failed = RunSnapshot(
            scriptId = scriptId,
            scriptName = scriptName,
            phase = RunPhase.FAILED,
            source = source,
            startedAt = startedAt,
            finishedAt = finishedAt,
            errorMessage = message,
        )
        val previous = _snapshot.getAndUpdate { current ->
            if (current?.phase?.isActive() == true) current else failed
        }
        if (previous?.phase?.isActive() != true) clearActiveRun()
    }

    fun dismiss(expectedStartedAt: Long? = null) {
        val previous = _snapshot.getAndUpdate { current ->
            when {
                current == null -> null
                expectedStartedAt == null -> null
                current.startedAt == expectedStartedAt -> null
                else -> current
            }
        }
        val cleared = previous == null ||
            expectedStartedAt == null ||
            previous.startedAt == expectedStartedAt
        if (cleared) clearActiveRun()
    }

    fun setRunning(value: Boolean) {
        if (!value) {
            dismiss()
            return
        }
        _running.value = true
    }

    fun finish() {
        dismiss()
    }

    private fun RunPhase.isActive(): Boolean =
        this == RunPhase.STARTING || this == RunPhase.RUNNING

    private fun clearActiveRun() {
        _activeScriptId.value = null
        _activeToken.value = null
        _running.value = false
    }
}
