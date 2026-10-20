package com.microauto.script

import org.json.JSONArray
import org.json.JSONObject

data class IndexParse<T>(
    val entries: List<T>,
    val skipped: Int,
    val corrupt: Boolean
)

object ScriptIndexCodec {
    fun parseScripts(text: String): IndexParse<ScriptMeta> = parse(text) { obj ->
        ScriptMeta(
            id = obj.requiredString("id"),
            name = obj.requiredString("name"),
            createdAt = obj.optLong("createdAt"),
            updatedAt = obj.optLong("updatedAt"),
            folderId = obj.optString("folderId").takeIf { it.isNotBlank() }
        )
    }

    fun encodeScripts(scripts: List<ScriptMeta>): String {
        val array = JSONArray()
        scripts.forEach { script ->
            array.put(
                JSONObject().apply {
                    put("id", script.id)
                    put("name", script.name)
                    put("createdAt", script.createdAt)
                    put("updatedAt", script.updatedAt)
                    script.folderId?.let { put("folderId", it) }
                }
            )
        }
        return array.toString()
    }

    fun parseFolders(text: String): IndexParse<FolderMeta> = parse(text) { obj ->
        FolderMeta(
            id = obj.requiredString("id"),
            name = obj.requiredString("name"),
            createdAt = obj.optLong("createdAt")
        )
    }

    fun encodeFolders(folders: List<FolderMeta>): String {
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
        return array.toString()
    }

    fun parseVersions(text: String): IndexParse<ScriptVersion> = parse(text) { obj ->
        ScriptVersion(
            id = obj.requiredString("id"),
            scriptId = obj.requiredString("scriptId"),
            version = obj.getInt("version"),
            note = obj.optString("note"),
            createdAt = obj.optLong("createdAt")
        )
    }

    fun encodeVersions(versions: List<ScriptVersion>): String {
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
        return array.toString()
    }

    private fun <T> parse(text: String, decoder: (JSONObject) -> T): IndexParse<T> {
        val array = runCatching { JSONArray(text) }.getOrElse {
            return IndexParse(emptyList(), 0, corrupt = true)
        }
        val entries = ArrayList<T>(array.length())
        var skipped = 0
        for (index in 0 until array.length()) {
            val obj = runCatching { array.getJSONObject(index) }.getOrNull()
            if (obj == null) {
                skipped++
                continue
            }
            val entry = runCatching { decoder(obj) }.getOrNull()
            if (entry == null) skipped++ else entries += entry
        }
        return IndexParse(entries, skipped, corrupt = false)
    }

    private fun JSONObject.requiredString(key: String): String {
        val value = getString(key)
        require(value.isNotBlank()) { "$key is blank" }
        return value
    }
}
