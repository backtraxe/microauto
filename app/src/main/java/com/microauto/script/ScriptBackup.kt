package com.microauto.script

import android.content.Context
import android.net.Uri
import java.io.File

object ScriptBackup {
    fun export(context: Context, uri: Uri): Int {
        val scriptsDir = File(context.filesDir, "scripts")
        val output = context.contentResolver.openOutputStream(uri)
            ?: error("无法写入备份文件")
        output.use {
            return ScriptBackupCodec.export(scriptsDir, it)
        }
    }

    fun restore(context: Context, uri: Uri): Int {
        val filesDir = context.filesDir
        val temp = File(filesDir, "scripts-restore")
        val current = File(filesDir, "scripts")
        val previous = File(filesDir, "scripts-before-restore")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: error("无法读取备份文件")
            input.use { ScriptBackupCodec.import(it, temp) }

            if (previous.exists()) previous.deleteRecursively()
            if (current.exists()) {
                require(current.renameTo(previous)) { "无法备份当前脚本" }
            }
            if (!temp.renameTo(current)) {
                previous.renameTo(current)
                error("无法恢复备份")
            }
            previous.deleteRecursively()
            ScriptRepository.clearCache()
            return ScriptRepository(context).ensureInitialized().size
        } catch (throwable: Throwable) {
            temp.deleteRecursively()
            if (!current.exists() && previous.exists()) {
                previous.renameTo(current)
            }
            throw throwable
        }
    }
}
