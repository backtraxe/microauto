package com.microauto.script

import android.content.Context
import com.microauto.core.ScriptNaming
import org.json.JSONArray
import org.json.JSONObject
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

    fun ensureInitialized(): List<ScriptMeta> {
        if (indexFile.exists()) return listAll()
        val legacy = File(appContext.filesDir, "script.js")
        if (legacy.exists()) {
            val legacyContent = runCatching { legacy.readText() }.getOrDefault("")
            if (legacyContent.isNotBlank()) {
                create("默认脚本", legacyContent)
            }
            legacy.delete()
        }
        ensureBuiltins()
        return listAll()
    }

    fun ensureBuiltins(): Int {
        val existing = readIndex().map { it.name }.toSet()
        var added = 0
        for (item in BuiltinScripts.all) {
            if (item.name !in existing) {
                create(item.name, item.content)
                added++
            }
        }
        return added
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
    ): ScriptMeta {
        val metas = readIndex()
        val uniqueName = ScriptNaming.uniqueName(name, metas.map { it.name }.toSet())
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val targetFolder = folderId?.takeIf { candidate ->
            readFolders().any { it.id == candidate }
        }
        File(dir, "$id.js").writeText(content)
        val meta = ScriptMeta(id, uniqueName, now, now, targetFolder)
        writeIndex(metas + meta)
        return meta
    }

    fun read(id: String): String? {
        val file = File(dir, "$id.js")
        if (!file.exists()) return null
        return runCatching { file.readText() }.getOrNull()
    }

    fun save(id: String, content: String): Boolean {
        val metas = readIndex().toMutableList()
        val index = metas.indexOfFirst { it.id == id }
        if (index < 0) return false
        File(dir, "$id.js").writeText(content)
        metas[index] = metas[index].copy(updatedAt = System.currentTimeMillis())
        writeIndex(metas)
        return true
    }

    fun rename(id: String, newName: String): Boolean {
        val metas = readIndex().toMutableList()
        val index = metas.indexOfFirst { it.id == id }
        if (index < 0) return false
        val base = ScriptNaming.sanitize(newName)
        if (base.isEmpty()) return false
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
        return true
    }

    fun delete(id: String): Boolean {
        val metas = readIndex().toMutableList()
        val index = metas.indexOfFirst { it.id == id }
        if (index < 0) return false
        metas.removeAt(index)
        File(dir, "$id.js").delete()
        writeIndex(metas)
        removeVersionsOf(id)
        return true
    }

    fun publishVersion(scriptId: String, note: String): ScriptVersion? {
        if (find(scriptId) == null) return null
        val content = read(scriptId) ?: return null
        val existing = readVersions().filter { it.scriptId == scriptId }
        val version = (existing.maxOfOrNull { it.version } ?: 0) + 1
        val id = UUID.randomUUID().toString()
        val versionDir = File(versionsDir, scriptId).apply { mkdirs() }
        File(versionDir, "$id.js").writeText(content)
        val record = ScriptVersion(
            id = id,
            scriptId = scriptId,
            version = version,
            note = note.trim(),
            createdAt = System.currentTimeMillis()
        )
        writeVersions(readVersions() + record)
        return record
    }

    fun versions(scriptId: String): List<ScriptVersion> =
        readVersions()
            .filter { it.scriptId == scriptId }
            .sortedByDescending { it.version }

    fun readVersion(versionId: String): String? {
        val version = readVersions().firstOrNull { it.id == versionId } ?: return null
        val file = File(versionsDir, "${version.scriptId}/${version.id}.js")
        if (!file.exists()) return null
        return runCatching { file.readText() }.getOrNull()
    }

    fun restoreVersion(versionId: String): Boolean {
        val version = readVersions().firstOrNull { it.id == versionId } ?: return false
        val content = readVersion(versionId) ?: return false
        val metas = readIndex().toMutableList()
        val index = metas.indexOfFirst { it.id == version.scriptId }
        if (index < 0) return false
        File(dir, "${version.scriptId}.js").writeText(content)
        metas[index] = metas[index].copy(updatedAt = System.currentTimeMillis())
        writeIndex(metas)
        return true
    }

    fun deleteVersion(versionId: String): Boolean {
        val versions = readVersions().toMutableList()
        val index = versions.indexOfFirst { it.id == versionId }
        if (index < 0) return false
        val version = versions.removeAt(index)
        File(versionsDir, "${version.scriptId}/${version.id}.js").delete()
        writeVersions(versions)
        return true
    }

    private fun removeVersionsOf(scriptId: String) {
        val versions = readVersions()
        versions.filter { it.scriptId == scriptId }.forEach {
            File(versionsDir, "${it.scriptId}/${it.id}.js").delete()
        }
        File(versionsDir, scriptId).deleteRecursively()
        writeVersions(versions.filterNot { it.scriptId == scriptId })
    }

    fun moveToFolder(id: String, folderId: String?): Boolean {
        if (folderId != null && folders().none { it.id == folderId }) return false
        val metas = readIndex().toMutableList()
        val index = metas.indexOfFirst { it.id == id }
        if (index < 0) return false
        metas[index] = metas[index].copy(
            folderId = folderId,
            updatedAt = System.currentTimeMillis()
        )
        writeIndex(metas)
        return true
    }

    fun folders(): List<FolderMeta> = readFolders().sortedBy { it.name.lowercase() }

    fun folder(id: String): FolderMeta? = readFolders().firstOrNull { it.id == id }

    fun folderName(id: String): String? = folder(id)?.name

    fun count(folderId: String): Int = readIndex().count { it.folderId == folderId }

    fun createFolder(name: String): FolderMeta? {
        val base = ScriptNaming.sanitize(name)
        if (base.isEmpty()) return null
        val folders = readFolders().toMutableList()
        val uniqueName = ScriptNaming.uniqueName(base, folders.map { it.name }.toSet())
        val folder = FolderMeta(
            id = UUID.randomUUID().toString(),
            name = uniqueName,
            createdAt = System.currentTimeMillis()
        )
        folders += folder
        writeFolders(folders)
        return folder
    }

    fun renameFolder(id: String, newName: String): Boolean {
        val base = ScriptNaming.sanitize(newName)
        if (base.isEmpty()) return false
        val folders = readFolders().toMutableList()
        val index = folders.indexOfFirst { it.id == id }
        if (index < 0) return false
        val uniqueName = ScriptNaming.uniqueName(
            base,
            folders.map { it.name }.toSet(),
            exclude = folders[index].name
        )
        folders[index] = folders[index].copy(name = uniqueName)
        writeFolders(folders)
        return true
    }

    fun deleteFolder(id: String): Boolean {
        val folders = readFolders().toMutableList()
        if (folders.none { it.id == id }) return false
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
        return true
    }

    private fun readIndex(): MutableList<ScriptMeta> {
        if (!indexFile.exists()) return mutableListOf()
        return runCatching {
            val array = JSONArray(indexFile.readText())
            MutableList(array.length()) { index ->
                val obj = array.getJSONObject(index)
                ScriptMeta(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    createdAt = obj.optLong("createdAt"),
                    updatedAt = obj.optLong("updatedAt"),
                    folderId = obj.optString("folderId").takeIf { it.isNotBlank() }
                )
            }
        }.getOrElse { mutableListOf() }
    }

    private fun writeIndex(metas: List<ScriptMeta>) {
        val array = JSONArray()
        metas.forEach { meta ->
            array.put(
                JSONObject().apply {
                    put("id", meta.id)
                    put("name", meta.name)
                    put("createdAt", meta.createdAt)
                    put("updatedAt", meta.updatedAt)
                    meta.folderId?.let { put("folderId", it) }
                }
            )
        }
        writeAtomically(indexFile, array.toString())
    }

    private fun readFolders(): MutableList<FolderMeta> {
        if (!foldersFile.exists()) return mutableListOf()
        return runCatching {
            val array = JSONArray(foldersFile.readText())
            MutableList(array.length()) { index ->
                val obj = array.getJSONObject(index)
                FolderMeta(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    createdAt = obj.optLong("createdAt")
                )
            }
        }.getOrElse { mutableListOf() }
    }

    private fun writeFolders(folders: List<FolderMeta>) {
        val array = JSONArray()
        folders.forEach { folder ->
            array.put(
                JSONObject().apply {
                    put("id", folder.id)
                    put("name", folder.name)
                    put("createdAt", folder.createdAt)
                }
            )
        }
        writeAtomically(foldersFile, array.toString())
    }

    private fun readVersions(): MutableList<ScriptVersion> {
        if (!versionsFile.exists()) return mutableListOf()
        return runCatching {
            val array = JSONArray(versionsFile.readText())
            MutableList(array.length()) { index ->
                val obj = array.getJSONObject(index)
                ScriptVersion(
                    id = obj.getString("id"),
                    scriptId = obj.getString("scriptId"),
                    version = obj.optInt("version"),
                    note = obj.optString("note"),
                    createdAt = obj.optLong("createdAt")
                )
            }
        }.getOrElse { mutableListOf() }
    }

    private fun writeVersions(versions: List<ScriptVersion>) {
        val array = JSONArray()
        versions.forEach { version ->
            array.put(
                JSONObject().apply {
                    put("id", version.id)
                    put("scriptId", version.scriptId)
                    put("version", version.version)
                    put("note", version.note)
                    put("createdAt", version.createdAt)
                }
            )
        }
        writeAtomically(versionsFile, array.toString())
    }

    private fun writeAtomically(target: File, text: String) {
        val temp = File(dir, "${target.name}.tmp")
        temp.writeText(text)
        if (!temp.renameTo(target)) {
            target.writeText(text)
            temp.delete()
        }
    }

    companion object {
        val DEFAULT_SCRIPT = """
            // 示例：打印当前界面节点树
            toast("MicroAuto 已运行");
            log("前台包名: " + currentPackage());
            dump();
        """.trimIndent()
    }
}
