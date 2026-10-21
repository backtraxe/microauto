package com.microauto.script

data class RunLogMeta(
    val scriptId: String?,
    val scriptName: String,
    val source: RunSource,
    val startedAt: Long,
    val finishedAt: Long?,
    val status: RunPhase,
    val errorLine: Int?,
    val errorMessage: String?,
) {
    fun toMap(): Map<String, String> = buildMap {
        scriptId?.let { put(KEY_SCRIPT_ID, it) }
        put(KEY_SCRIPT_NAME, scriptName)
        put(KEY_SOURCE, source.name)
        put(KEY_STARTED_AT, startedAt.toString())
        finishedAt?.let { put(KEY_FINISHED_AT, it.toString()) }
        put(KEY_STATUS, status.name)
        errorLine?.let { put(KEY_ERROR_LINE, it.toString()) }
        errorMessage?.let { put(KEY_ERROR_MESSAGE, it) }
    }

    companion object {
        private const val KEY_SCRIPT_ID = "script_id"
        private const val KEY_SCRIPT_NAME = "script_name"
        private const val KEY_SOURCE = "source"
        private const val KEY_STARTED_AT = "started_at"
        private const val KEY_FINISHED_AT = "finished_at"
        private const val KEY_STATUS = "status"
        private const val KEY_ERROR_LINE = "error_line"
        private const val KEY_ERROR_MESSAGE = "error_message"

        fun fromMap(values: Map<String, String?>): RunLogMeta? {
            val name = values[KEY_SCRIPT_NAME]?.takeIf { it.isNotBlank() } ?: return null
            val status = values[KEY_STATUS]
                ?.let { runCatching { RunPhase.valueOf(it) }.getOrNull() }
                ?: return null
            val source = values[KEY_SOURCE]
                ?.let { runCatching { RunSource.valueOf(it) }.getOrNull() }
                ?: RunSource.MANUAL
            return RunLogMeta(
                scriptId = values[KEY_SCRIPT_ID]?.takeIf { it.isNotBlank() },
                scriptName = name,
                source = source,
                startedAt = values[KEY_STARTED_AT]?.toLongOrNull() ?: 0L,
                finishedAt = values[KEY_FINISHED_AT]?.toLongOrNull(),
                status = status,
                errorLine = values[KEY_ERROR_LINE]?.toIntOrNull(),
                errorMessage = values[KEY_ERROR_MESSAGE]?.takeIf { it.isNotBlank() },
            )
        }
    }
}

internal fun normalizeInterruptedMeta(meta: RunLogMeta, logModifiedAt: Long): RunLogMeta {
    if (meta.status != RunPhase.RUNNING && meta.status != RunPhase.STARTING) return meta
    val finishedAt = logModifiedAt.takeIf { it > 0 && it >= meta.startedAt }
    return meta.copy(status = RunPhase.STOPPED, finishedAt = finishedAt)
}
