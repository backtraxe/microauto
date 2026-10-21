package com.microauto.script

import android.content.Context
import android.net.Uri
import android.util.Log
import com.microauto.schedule.SchedulePrefs
import com.microauto.schedule.ScriptScheduler
import java.io.File
import java.util.UUID

object ScriptBackup {
    private val restoreLock = Any()

    fun export(context: Context, uri: Uri): Int = synchronized(restoreLock) {
        val scriptsDir = File(context.filesDir, "scripts")
        val output = context.contentResolver.openOutputStream(uri)
            ?: error("无法写入备份文件")
        output.use {
            ScriptBackupCodec.export(scriptsDir, it)
        }
    }

    fun restore(context: Context, uri: Uri): Int = synchronized(restoreLock) {
        val filesDir = context.filesDir
        val temp = File(filesDir, "scripts-restore-${UUID.randomUUID()}")
        val current = File(filesDir, "scripts")
        val previous = File(filesDir, "scripts-before-restore")
        var previousMovedAside = false
        var swapped = false
        try {
            filesDir.listFiles { file -> file.name.startsWith("scripts-restore-") }
                ?.forEach { it.deleteRecursively() }
            val input = context.contentResolver.openInputStream(uri)
                ?: error("无法读取备份文件")
            val expected = input.use { ScriptBackupCodec.import(it, temp) }

            if (!current.exists() && previous.exists()) {
                require(previous.renameTo(current)) { "无法恢复上次中断的恢复操作" }
            }
            if (current.exists() && previous.exists()) {
                previous.deleteRecursively()
            }
            if (current.exists()) {
                require(current.renameTo(previous)) { "无法备份当前脚本" }
                previousMovedAside = true
            }
            if (!temp.renameTo(current)) {
                if (previousMovedAside && previous.exists()) previous.renameTo(current)
                error("无法恢复备份")
            }
            swapped = true

            ScriptRepository.clearCache()
            val repository = ScriptRepository(context)
            val scripts = repository.ensureInitialized()
            require(scripts.size == expected) { "恢复后的脚本数量不一致" }

            ScriptScheduler.cancelAll(context)
            SchedulePrefs.clearAll(context)

            previous.deleteRecursively()
            scripts.size
        } catch (throwable: Throwable) {
            if (swapped) {
                if (current.exists()) current.deleteRecursively()
                if (previous.exists()) previous.renameTo(current)
                ScriptRepository.clearCache()
            } else if (previousMovedAside && !current.exists() && previous.exists()) {
                previous.renameTo(current)
                ScriptRepository.clearCache()
            }
            throw throwable
        } finally {
            temp.deleteRecursively()
        }
    }

    /**
     * 进程在换入新目录后、完成加载前被杀时，恢复上次的 scripts 目录。
     * 必须在 ScriptRepository 初始化之前调用。
     */
    fun recoverInterruptedRestore(context: Context): Boolean = synchronized(restoreLock) {
        val filesDir = context.filesDir
        val current = File(filesDir, "scripts")
        val previous = File(filesDir, "scripts-before-restore")
        when {
            !previous.exists() -> false
            isUsableScriptsDir(current) -> {
                previous.deleteRecursively()
                false
            }

            !isUsableScriptsDir(previous) -> {
                previous.deleteRecursively()
                false
            }

            else -> {
                if (current.exists()) current.deleteRecursively()
                val restored = previous.renameTo(current)
                if (restored) {
                    ScriptRepository.clearCache()
                    Log.i("MicroAuto", "已恢复上次中断的备份")
                }
                restored
            }
        }
    }

    private fun isUsableScriptsDir(dir: File): Boolean {
        if (!dir.isDirectory) return false
        val index = File(dir, "index.json")
        if (!index.isFile) return false
        return runCatching {
            val parsed = ScriptIndexCodec.parseScripts(index.readText())
            !parsed.corrupt && parsed.entries.isNotEmpty()
        }.getOrDefault(false)
    }
}
