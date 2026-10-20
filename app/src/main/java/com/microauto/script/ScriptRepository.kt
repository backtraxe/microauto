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
    val updatedAt: Long
)

class ScriptRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "scripts")
    private val indexFile = File(dir, "index.json")

    init {
        dir.mkdirs()
    }

    fun indexExists(): Boolean = indexFile.exists()

    fun ensureInitialized(): List<ScriptMeta> {
        if (indexFile.exists()) return list()
        val legacy = File(appContext.filesDir, "script.js")
        val legacyContent = if (legacy.exists()) {
            runCatching { legacy.readText() }.getOrDefault("")
        } else {
            ""
        }
        val content = legacyContent.ifBlank { DEFAULT_SCRIPT }
        val name = if (legacyContent.isNotBlank()) "默认脚本" else "示例"
        create(name, content)
        if (legacy.exists()) legacy.delete()
        return list()
    }

    fun list(): List<ScriptMeta> = readIndex().sortedByDescending { it.updatedAt }

    fun find(id: String): ScriptMeta? = readIndex().firstOrNull { it.id == id }

    fun create(name: String, content: String): ScriptMeta {
        val metas = readIndex()
        val uniqueName = ScriptNaming.uniqueName(name, metas.map { it.name }.toSet())
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        File(dir, "$id.js").writeText(content)
        val meta = ScriptMeta(id, uniqueName, now, now)
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
                    updatedAt = obj.optLong("updatedAt")
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
                }
            )
        }
        val text = array.toString()
        val temp = File(dir, "index.json.tmp")
        temp.writeText(text)
        if (!temp.renameTo(indexFile)) {
            indexFile.writeText(text)
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
