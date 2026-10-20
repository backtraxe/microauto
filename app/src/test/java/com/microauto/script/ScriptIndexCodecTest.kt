package com.microauto.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptIndexCodecTest {

    @Test
    fun parsesValidScriptIndex() {
        val text = """
            [
              {"id":"a","name":"签到","createdAt":1,"updatedAt":2,"folderId":"f1"},
              {"id":"b","name":"日报","createdAt":3,"updatedAt":4}
            ]
        """.trimIndent()

        val result = ScriptIndexCodec.parseScripts(text)

        assertFalse(result.corrupt)
        assertEquals(0, result.skipped)
        assertEquals(2, result.entries.size)
        assertEquals("f1", result.entries[0].folderId)
        assertNull(result.entries[1].folderId)
    }

    @Test
    fun skipsMalformedEntryButKeepsValidOnes() {
        val text = """
            [
              {"id":"a","name":"签到","createdAt":1,"updatedAt":2},
              {"name":"缺少 id"},
              {"id":"c","createdAt":5,"updatedAt":6}
            ]
        """.trimIndent()

        val result = ScriptIndexCodec.parseScripts(text)

        assertFalse(result.corrupt)
        assertEquals(1, result.entries.size)
        assertEquals("a", result.entries[0].id)
        assertEquals(2, result.skipped)
    }

    @Test
    fun marksCorruptWhenRootIsNotAnArray() {
        val result = ScriptIndexCodec.parseScripts("{not json")

        assertTrue(result.corrupt)
        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun encodesAndParsesScriptIndexRoundTrip() {
        val scripts = listOf(
            ScriptMeta("a", "签到", 1, 2, "f1"),
            ScriptMeta("b", "日报", 3, 4)
        )

        val result = ScriptIndexCodec.parseScripts(ScriptIndexCodec.encodeScripts(scripts))

        assertFalse(result.corrupt)
        assertEquals(scripts, result.entries)
    }

    @Test
    fun parsesFoldersAndVersions() {
        val folders = ScriptIndexCodec.parseFolders(
            """[{"id":"f","name":"工作","createdAt":1}]"""
        )
        assertEquals(1, folders.entries.size)
        assertEquals("工作", folders.entries[0].name)

        val versions = ScriptIndexCodec.parseVersions(
            """[{"id":"v","scriptId":"s","version":2,"note":"发布","createdAt":3}]"""
        )
        assertEquals(1, versions.entries.size)
        assertEquals(2, versions.entries[0].version)
    }

    @Test
    fun skipsMalformedFolderAndVersionEntries() {
        val folders = ScriptIndexCodec.parseFolders(
            """[{"name":"缺少 id"},{"id":"f","name":"工作","createdAt":1}]"""
        )
        assertEquals(1, folders.entries.size)
        assertEquals(1, folders.skipped)

        val versions = ScriptIndexCodec.parseVersions(
            """[{"id":"v","scriptId":"s","version":1},{"id":"x"}]"""
        )
        assertEquals(1, versions.entries.size)
        assertEquals(1, versions.skipped)
    }
}
