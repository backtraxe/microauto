package com.microauto.script

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ScriptBackupCodec {
    private const val INDEX_FILE = "index.json"
    private const val MAX_TOTAL_BYTES = 50L * 1024 * 1024
    private const val BUFFER_SIZE = 8 * 1024

    fun export(sourceDir: File, output: OutputStream): Int {
        require(sourceDir.isDirectory) { "脚本目录不存在" }
        var scripts = 0
        ZipOutputStream(output).use { zip ->
            sourceDir.walkTopDown()
                .filter { it.isFile }
                .filterNot { it.name.endsWith(".tmp") || it.name.contains(".corrupt-") }
                .forEach { file ->
                    val relative = file.relativeTo(sourceDir).invariantSeparatorsPath
                    zip.putNextEntry(ZipEntry(relative))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    if (file.parentFile == sourceDir && file.extension == "js") {
                        scripts++
                    }
                }
        }
        return scripts
    }

    fun import(input: InputStream, targetDir: File): Int {
        if (targetDir.exists()) targetDir.deleteRecursively()
        targetDir.mkdirs()
        val canonicalTarget = targetDir.canonicalFile
        var totalBytes = 0L

        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                val target = File(targetDir, entry.name).canonicalFile
                val prefix = canonicalTarget.path + File.separator
                require(target.path.startsWith(prefix)) { "非法备份路径: ${entry.name}" }
                target.parentFile?.mkdirs()
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        totalBytes += read
                        require(totalBytes <= MAX_TOTAL_BYTES) { "备份文件过大" }
                        output.write(buffer, 0, read)
                    }
                }
                zip.closeEntry()
            }
        }

        val index = File(targetDir, INDEX_FILE)
        require(index.exists()) { "备份缺少 $INDEX_FILE" }
        val parsed = ScriptIndexCodec.parseScripts(index.readText())
        require(!parsed.corrupt) { "备份的 $INDEX_FILE 已损坏" }
        return parsed.entries.size
    }
}
