package com.microauto.script

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ScriptBackupCodec {
    private const val INDEX_FILE = "index.json"
    private const val FOLDERS_FILE = "folders.json"
    private const val VERSIONS_FILE = "versions.json"
    private const val VERSIONS_DIR = "versions"
    private const val MAX_TOTAL_BYTES = 50L * 1024 * 1024
    private const val MAX_INDEX_BYTES = 2L * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 5L * 1024 * 1024
    private const val MAX_RECORDS = 10_000
    private const val MAX_ENTRIES = 2 * MAX_RECORDS + 3
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
                    if (!isAllowedEntry(relative)) return@forEach
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
        var entries = 0

        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                require(entries <= MAX_ENTRIES) { "备份条目过多" }
                val name = entry.name
                validateEntryName(name)
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                require(isAllowedEntry(name)) { "备份包含不允许的文件: $name" }
                val target = File(targetDir, name).canonicalFile
                val prefix = canonicalTarget.path + File.separator
                require(target.path.startsWith(prefix)) { "非法备份路径: $name" }
                target.parentFile?.mkdirs()
                val limit = if (name == INDEX_FILE) MAX_INDEX_BYTES else MAX_ENTRY_BYTES
                var entryBytes = 0L
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        entryBytes += read
                        totalBytes += read
                        require(entryBytes <= limit) { "备份条目过大: $name" }
                        require(totalBytes <= MAX_TOTAL_BYTES) { "备份文件过大" }
                        output.write(buffer, 0, read)
                    }
                }
                zip.closeEntry()
            }
        }

        return validate(targetDir)
    }

    private fun validate(targetDir: File): Int {
        val index = File(targetDir, INDEX_FILE)
        require(index.exists()) { "备份缺少 $INDEX_FILE" }
        require(index.length() <= MAX_INDEX_BYTES) { "备份的 $INDEX_FILE 过大" }
        val parsed = ScriptIndexCodec.parseScripts(index.readText())
        require(!parsed.corrupt) { "备份的 $INDEX_FILE 已损坏" }
        require(parsed.skipped == 0) { "备份的 $INDEX_FILE 含损坏记录" }
        require(parsed.entries.size <= MAX_RECORDS) { "备份的脚本数量过多" }
        require(parsed.entries.isNotEmpty()) { "备份中没有脚本" }

        val scriptIds = mutableSetOf<String>()
        parsed.entries.forEach { meta ->
            require(ScriptPaths.isValidId(meta.id)) { "非法脚本 ID: ${meta.id}" }
            require(scriptIds.add(meta.id)) { "重复脚本 ID: ${meta.id}" }
            val file = ScriptPaths.scriptFile(targetDir, meta.id)
            require(file != null && file.isFile) { "缺少脚本文件: ${meta.id}.js" }
        }

        val folders = File(targetDir, FOLDERS_FILE)
        if (folders.exists()) {
            val parsedFolders = ScriptIndexCodec.parseFolders(folders.readText())
            require(!parsedFolders.corrupt) { "备份的 $FOLDERS_FILE 已损坏" }
            require(parsedFolders.skipped == 0) { "备份的 $FOLDERS_FILE 含损坏记录" }
            require(parsedFolders.entries.size <= MAX_RECORDS) { "备份的文件夹数量过多" }
            val folderIds = mutableSetOf<String>()
            parsedFolders.entries.forEach { folder ->
                require(ScriptPaths.isValidId(folder.id)) { "非法文件夹 ID: ${folder.id}" }
                require(folderIds.add(folder.id)) { "重复文件夹 ID: ${folder.id}" }
            }
        }

        val versions = File(targetDir, VERSIONS_FILE)
        if (versions.exists()) {            val parsedVersions = ScriptIndexCodec.parseVersions(versions.readText())
            require(!parsedVersions.corrupt) { "备份的 $VERSIONS_FILE 已损坏" }
            require(parsedVersions.skipped == 0) { "备份的 $VERSIONS_FILE 含损坏记录" }
            require(parsedVersions.entries.size <= MAX_RECORDS) { "备份的版本数量过多" }
            val versionIds = mutableSetOf<String>()
            parsedVersions.entries.forEach { version ->
                require(version.scriptId in scriptIds) { "版本指向不存在的脚本: ${version.scriptId}" }
                require(versionIds.add(version.id)) { "重复版本 ID: ${version.id}" }
                val file = ScriptPaths.versionFile(
                    File(targetDir, VERSIONS_DIR),
                    version.scriptId,
                    version.id
                )
                require(file != null && file.isFile) {
                    "缺少版本文件: ${version.scriptId}/${version.id}.js"
                }
            }
        }

        return parsed.entries.size
    }

    private fun validateEntryName(name: String) {
        require(name.isNotEmpty()) { "备份包含空条目名" }
        require(!name.contains('\u0000')) { "非法备份路径: $name" }
        require(!name.contains('\\')) { "非法备份路径: $name" }
        require(!name.startsWith("/")) { "非法备份路径: $name" }
        require(name.split('/').none { it == ".." || it == "." }) { "非法备份路径: $name" }
    }

    private fun isAllowedEntry(name: String): Boolean {
        if (name == INDEX_FILE || name == FOLDERS_FILE || name == VERSIONS_FILE) return true
        if (!name.endsWith(".js")) return false
        if ('/' !in name) return ScriptPaths.isValidId(name.removeSuffix(".js"))
        val parts = name.split('/')
        if (parts.size != 3 || parts[0] != VERSIONS_DIR) return false
        return ScriptPaths.isValidId(parts[1]) &&
            ScriptPaths.isValidId(parts[2].removeSuffix(".js"))
    }
}
