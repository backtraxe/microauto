package com.microauto.script

import android.content.Context
import com.microauto.core.ScriptNaming
import java.io.File
import java.util.UUID

data class ScriptMeta(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val folderId: String? = null
)

data class FolderMeta(
    val id: String,
    val name: String,
    val createdAt: Long
)

data class ScriptVersion(
    val id: String,
    val scriptId: String,
    val version: Int,
    val note: String,
    val createdAt: Long
)

class ScriptRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "scripts")
    private val indexFile = File(dir, "index.json")
    private val foldersFile = File(dir, "folders.json")
    private val versionsFile = File(dir, "versions.json")
    private val versionsDir = File(dir, "versions")

    init {
        dir.mkdirs()
    }

    fun indexExists(): Boolean = indexFile.exists()

    fun ensureInitialized(): List<ScriptMeta> = locked {
        if (!indexFile.exists()) {
            clearCache()
            val legacy = File(appContext.filesDir, "script.js")
            if (legacy.exists()) {
                val legacyContent = runCatching { legacy.readText() }.getOrDefault("")
                if (legacyContent.isNotBlank()) {
                    create("默认脚本", legacyContent)
                }
                legacy.delete()
            }
            ensureBuiltins()
        }
        listAll()
    }

    fun ensureBuiltins(): Int = locked {
        val existing = readIndex().map { it.name }.toSet()
        var added = 0
        for (item in BuiltinScripts.all) {
            if (item.name !in existing) {
                create(item.name, item.content)
                added++
            }
        }
        added
    }

    fun listAll(): List<ScriptMeta> = readIndex().sortedByDescending { it.updatedAt }

    fun list(folderId: String? = null): List<ScriptMeta> =
        readIndex()
            .filter { it.folderId == folderId }
            .sortedByDescending { it.updatedAt }

    fun find(id: String): ScriptMeta? = readIndex().firstOrNull { it.id == id }

    fun create(
        name: String,
        content: String,
        folderId: String? = null
    ): ScriptMeta = locked {
        val metas = readIndex()
        val uniqueName = ScriptNaming.uniqueName(name, metas.map { it.name }.toSet())
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val targetFolder = folderId?.takeIf { candidate ->
            readFolders().any { it.id == candidate }
        }
        writeAtomically(File(dir, "$id.js"), content)
        val meta = ScriptMeta(id, uniqueName, now, now, targetFolder)
        writeIndex(metas + meta)
        meta
    }

    fun read(id: String): String? {
        val file = ScriptPaths.scriptFile(dir, id) ?: return null
        if (!file.exists()) return null
        return runCatching { file.readText() }.getOrNull()
    }

    fun save(id: String, content: String): Boolean = locked {
        val metas = readIndex().toMutableList()
        val index = metas.indexOfFirst { it.id == id }
        val file = ScriptPaths.scriptFile(dir, id)
        if (index < 0 || file == null) {
            false
        } else {
            writeAtomically(file, content)
            metas[index] = metas[index].copy(updatedAt = System.currentTimeMillis())
            writeIndex(metas)
            true
        }
    }

    fun rename(id: String, newName: String): Boolean = locked {
        val metas = readIndex().toMutableList()
        val index = metas.indexOfFirst { it.id == id }
        if (index < 0) {
            false
        } else {
            val base = ScriptNaming.sanitize(newName)
            if (base.isEmpty()) {
                false
            } else {
                val uniqueName = ScriptNaming.uniqueName(
                    base,
                    metas.map { it.name }.toSet(),
                    exclude = metas[index].name
                )
                metas[index] = metas[index].copy(
                    name = uniqueName,
                    updatedAt = System.currentTimeMillis()
                )
                writeIndex(metas)
                true
            }
        }
    }

    fun delete(id: String): Boolean = locked {
        val metas = readIndex().toMutableList()
        val index = metas.indexOfFirst { it.id == id }
        val file = ScriptPaths.scriptFile(dir, id)
        if (index < 0 || file == null) {
            false
        } else {
            metas.removeAt(index)
            file.delete()
            writeIndex(metas)
            removeVersionsOf(id)
            true
        }
    }

    fun publishVersion(scriptId: String, note: String): ScriptVersion? = locked {
        val versionDir = ScriptPaths.versionDir(versionsDir, scriptId)
        if (find(scriptId) == null || versionDir == null) {
            null
        } else {
            val content = read(scriptId)
            if (content == null) {
                null
            } else {
                val existing = readVersions().filter { it.scriptId == scriptId }
                val version = (existing.maxOfOrNull { it.version } ?: 0) + 1
                val id = UUID.randomUUID().toString()
                val versionFile = ScriptPaths.versionFile(versionsDir, scriptId, id)
                if (versionFile == null) {
                    null
                } else {
                    versionDir.mkdirs()
                    writeAtomically(versionFile, content)
                    val record = ScriptVersion(
                        id = id,
                        scriptId = scriptId,
                        version = version,
                        note = note.trim(),
                        createdAt = System.currentTimeMillis()
                    )
                    writeVersions(readVersions() + record)
                    record
                }
            }
        }
    }

    fun versions(scriptId: String): List<ScriptVersion> =
        readVersions()
            .filter { it.scriptId == scriptId }
            .sortedByDescending { it.version }

    fun readVersion(versionId: String): String? {
        val version = readVersions().firstOrNull { it.id == versionId } ?: return null
        val file = ScriptPaths.versionFile(versionsDir, version.scriptId, version.id) ?: return null
        if (!file.exists()) return null
        return runCatching { file.readText() }.getOrNull()
    }

    fun restoreVersion(versionId: String): Boolean = locked {
        val version = readVersions().firstOrNull { it.id == versionId }
        val content = version?.let { readVersion(versionId) }
        if (version == null || content == null) {
            false
        } else {
            val metas = readIndex().toMutableList()
            val index = metas.indexOfFirst { it.id == version.scriptId }
            val target = ScriptPaths.scriptFile(dir, version.scriptId)
            if (index < 0 || target == null) {
                false
            } else {
                val current = read(version.scriptId)
                if (current != null && current != content) {
                    publishVersion(version.scriptId, "恢复前自动快照")
                }
                writeAtomically(target, content)
                metas[index] = metas[index].copy(updatedAt = System.currentTimeMillis())
                writeIndex(metas)
                true
            }
        }
    }

    fun deleteVersion(versionId: String): Boolean = locked {
        val versions = readVersions().toMutableList()
        val index = versions.indexOfFirst { it.id == versionId }
        if (index < 0) {
            false
        } else {
            val version = versions.removeAt(index)
            ScriptPaths.versionFile(versionsDir, version.scriptId, version.id)?.delete()
            writeVersions(versions)
            true
        }
    }

    private fun removeVersionsOf(scriptId: String) {
        if (!ScriptPaths.isValidId(scriptId)) return
        val versions = readVersions()
        versions.filter { it.scriptId == scriptId }.forEach {
            ScriptPaths.versionFile(versionsDir, it.scriptId, it.id)?.delete()
        }
        ScriptPaths.versionDir(versionsDir, scriptId)?.deleteRecursively()
        writeVersions(versions.filterNot { it.scriptId == scriptId })
    }

    fun moveToFolder(id: String, folderId: String?): Boolean = locked {
        if (folderId != null && folders().none { it.id == folderId }) {
            false
        } else {
            val metas = readIndex().toMutableList()
            val index = metas.indexOfFirst { it.id == id }
            if (index < 0) {
                false
            } else {
                metas[index] = metas[index].copy(
                    folderId = folderId,
                    updatedAt = System.currentTimeMillis()
                )
                writeIndex(metas)
                true
            }
        }
    }

    fun folders(): List<FolderMeta> = readFolders().sortedBy { it.name.lowercase() }

    fun folder(id: String): FolderMeta? = readFolders().firstOrNull { it.id == id }

    fun folderName(id: String): String? = folder(id)?.name

    fun count(folderId: String): Int = readIndex().count { it.folderId == folderId }

    fun createFolder(name: String): FolderMeta? = locked {
        val base = ScriptNaming.sanitize(name)
        if (base.isEmpty()) {
            null
        } else {
            val folders = readFolders().toMutableList()
            val uniqueName = ScriptNaming.uniqueName(base, folders.map { it.name }.toSet())
            val folder = FolderMeta(
                id = UUID.randomUUID().toString(),
                name = uniqueName,
                createdAt = System.currentTimeMillis()
            )
            folders += folder
            writeFolders(folders)
            folder
        }
    }

    fun renameFolder(id: String, newName: String): Boolean = locked {
        val base = ScriptNaming.sanitize(newName)
        if (base.isEmpty()) {
            false
        } else {
            val folders = readFolders().toMutableList()
            val index = folders.indexOfFirst { it.id == id }
            if (index < 0) {
                false
            } else {
                val uniqueName = ScriptNaming.uniqueName(
                    base,
                    folders.map { it.name }.toSet(),
                    exclude = folders[index].name
                )
                folders[index] = folders[index].copy(name = uniqueName)
                writeFolders(folders)
                true
            }
        }
    }

    fun deleteFolder(id: String): Boolean = locked {
        val folders = readFolders().toMutableList()
        if (folders.none { it.id == id }) {
            false
        } else {
            folders.removeAll { it.id == id }
            val now = System.currentTimeMillis()
            val metas = readIndex().map {
                if (it.folderId == id) {
                    it.copy(folderId = null, updatedAt = now)
                } else {
                    it
                }
            }
            writeIndex(metas)
            writeFolders(folders)
            true
        }
    }

    private fun readIndex(): MutableList<ScriptMeta> = locked {
        cachedIndex?.let { return@locked it.toMutableList() }
        val entries = readEntries(
            indexFile,
            "脚本索引",
            ScriptIndexCodec::parseScripts,
            ScriptIndexCodec::encodeScripts
        )
        cachedIndex = entries
        entries.toMutableList()
    }

    private fun writeIndex(metas: List<ScriptMeta>) {
        writeAtomically(indexFile, ScriptIndexCodec.encodeScripts(metas))
        cachedIndex = metas.toList()
    }

    private fun readFolders(): MutableList<FolderMeta> = locked {
        cachedFolders?.let { return@locked it.toMutableList() }
        val entries = readEntries(
            foldersFile,
            "文件夹索引",
            ScriptIndexCodec::parseFolders,
            ScriptIndexCodec::encodeFolders
        )
        cachedFolders = entries
        entries.toMutableList()
    }

    private fun writeFolders(folders: List<FolderMeta>) {
        writeAtomically(foldersFile, ScriptIndexCodec.encodeFolders(folders))
        cachedFolders = folders.toList()
    }

    private fun readVersions(): MutableList<ScriptVersion> = locked {
        cachedVersions?.let { return@locked it.toMutableList() }
        val entries = readEntries(
            versionsFile,
            "版本索引",
            ScriptIndexCodec::parseVersions,
            ScriptIndexCodec::encodeVersions
        )
        cachedVersions = entries
        entries.toMutableList()
    }

    private fun writeVersions(versions: List<ScriptVersion>) {
        writeAtomically(versionsFile, ScriptIndexCodec.encodeVersions(versions))
        cachedVersions = versions.toList()
    }

    private fun <T> readEntries(
        file: File,
        label: String,
        parser: (String) -> IndexParse<T>,
        encoder: (List<T>) -> String
    ): List<T> {
        if (!file.exists()) return emptyList()
        val text = runCatching { file.readText() }.getOrElse { return emptyList() }
        val parsed = parser(text)
        if (!parsed.corrupt && parsed.skipped == 0) return parsed.entries

        val recovered = if (parsed.corrupt) emptyList() else parsed.entries
        val backup = backupCorrupt(file)
        runCatching { writeAtomically(file, encoder(recovered)) }

        val detail = buildString {
            append("$label 文件损坏")
            backup?.let { append("，原文件已备份为 ${it.name}") }
            append("。")
            if (recovered.isNotEmpty()) append("已恢复 ${recovered.size} 条记录。")
            if (parsed.skipped > 0) append("跳过 ${parsed.skipped} 条损坏记录。")
        }
        RepositoryHealth.report(detail)
        return recovered
    }

    private fun backupCorrupt(file: File): File? {
        if (!file.exists()) return null
        val backup = File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}")
        return runCatching {
            file.copyTo(backup, overwrite = false)
            backup
        }.getOrNull()
    }

    private fun writeAtomically(target: File, text: String) {
        val parent = target.parentFile ?: dir
        val temp = File(parent, "${target.name}.tmp")
        temp.writeText(text)
        if (!temp.renameTo(target)) {
            target.writeText(text)
            temp.delete()
        }
    }

    private inline fun <T> locked(block: () -> T): T = synchronized(ioLock) { block() }

    companion object {
        private val ioLock = Any()
        private var cachedIndex: List<ScriptMeta>? = null
        private var cachedFolders: List<FolderMeta>? = null
        private var cachedVersions: List<ScriptVersion>? = null

        fun clearCache() {
            synchronized(ioLock) {
                cachedIndex = null
                cachedFolders = null
                cachedVersions = null
            }
        }

        val DEFAULT_SCRIPT = """
            // 示例：打印当前界面节点树
            toast("MicroAuto 已运行");
            log("前台包名: " + currentPackage());
            dump();
        """.trimIndent()
    }
}
