package com.microauto.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ScriptBackupCodecTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun exportsAndImportsScriptsAndVersions() {
        val source = temp.newFolder("source")
        File(source, "index.json").writeText(
            """[{"id":"a","name":"签到","createdAt":1,"updatedAt":2}]"""
        )
        File(source, "a.js").writeText("log(1);")
        File(source, "versions/a").mkdirs()
        File(source, "versions/a/v1.js").writeText("log(0);")

        val output = ByteArrayOutputStream()
        val exported = ScriptBackupCodec.export(source, output)

        val target = temp.newFolder("target")
        val imported = ScriptBackupCodec.import(
            ByteArrayInputStream(output.toByteArray()),
            target
        )

        assertEquals(1, exported)
        assertEquals(1, imported)
        assertEquals("log(1);", File(target, "a.js").readText())
        assertEquals("log(0);", File(target, "versions/a/v1.js").readText())
    }

    @Test
    fun skipsTemporaryAndCorruptBackupFiles() {
        val source = temp.newFolder("source")
        File(source, "index.json").writeText("[]")
        File(source, "a.js").writeText("log(1);")
        File(source, "index.json.tmp").writeText("tmp")
        File(source, "index.json.corrupt-1").writeText("corrupt")

        val output = ByteArrayOutputStream()
        ScriptBackupCodec.export(source, output)

        val target = temp.newFolder("target")
        ScriptBackupCodec.import(ByteArrayInputStream(output.toByteArray()), target)

        assertFalse(File(target, "index.json.tmp").exists())
        assertFalse(File(target, "index.json.corrupt-1").exists())
    }

    @Test
    fun rejectsPathTraversalEntries() {
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { output ->
            output.putNextEntry(ZipEntry("../evil.js"))
            output.write("bad".toByteArray())
            output.closeEntry()
        }

        assertThrows(IllegalArgumentException::class.java) {
            ScriptBackupCodec.import(
                ByteArrayInputStream(zip.toByteArray()),
                temp.newFolder("target")
            )
        }
    }

    @Test
    fun rejectsBackupWithoutIndex() {
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { output ->
            output.putNextEntry(ZipEntry("a.js"))
            output.write("log(1);".toByteArray())
            output.closeEntry()
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            ScriptBackupCodec.import(
                ByteArrayInputStream(zip.toByteArray()),
                temp.newFolder("target")
            )
        }
        assertTrue(error.message?.contains("index.json") == true)
    }
}
