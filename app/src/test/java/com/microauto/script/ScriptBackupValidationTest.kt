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

class ScriptBackupValidationTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun zip(entries: List<Pair<String, String>>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun import(entries: List<Pair<String, String>>): Int =
        ScriptBackupCodec.import(
            ByteArrayInputStream(zip(entries)),
            temp.newFolder()
        )

    @Test
    fun rejectsBackupWithOnlyIndex() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            import(
                listOf(
                    "index.json" to
                        """[{"id":"a","name":"签到","createdAt":1,"updatedAt":2}]"""
                )
            )
        }
        assertTrue(error.message?.contains("a.js") == true)
    }

    @Test
    fun rejectsEmptyBackup() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            import(listOf("index.json" to "[]"))
        }
        assertTrue(error.message?.contains("没有脚本") == true)
    }

    @Test
    fun rejectsMissingVersionFile() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            import(
                listOf(
                    "index.json" to
                        """[{"id":"a","name":"签到","createdAt":1,"updatedAt":2}]""",
                    "a.js" to "log(1);",
                    "versions.json" to
                        """[{"id":"v1","scriptId":"a","version":1,"note":"","createdAt":1}]"""
                )
            )
        }
        assertTrue(error.message?.contains("版本文件") == true)
    }

    @Test
    fun rejectsDuplicateScriptId() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            import(
                listOf(
                    "index.json" to
                        """[{"id":"a","name":"一","createdAt":1,"updatedAt":2},
                           {"id":"a","name":"二","createdAt":1,"updatedAt":2}]""",
                    "a.js" to "log(1);"
                )
            )
        }
        assertTrue(error.message?.contains("重复脚本 ID") == true)
    }

    @Test
    fun rejectsPathTraversalId() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            import(
                listOf(
                    "index.json" to
                        """[{"id":"../evil","name":"坏","createdAt":1,"updatedAt":2}]""",
                    "a.js" to "log(1);"
                )
            )
        }
        assertTrue(error.message?.contains("损坏记录") == true)
    }

    @Test
    fun rejectsUnknownEntryName() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            import(
                listOf(
                    "index.json" to
                        """[{"id":"a","name":"签到","createdAt":1,"updatedAt":2}]""",
                    "a.js" to "log(1);",
                    "evil.txt" to "bad"
                )
            )
        }
        assertTrue(error.message?.contains("不允许的文件") == true)
    }

    @Test
    fun rejectsTooManyEntries() {
        val entries = buildList {
            add("index.json" to "[]")
            repeat(20_003) { add("s$it.js" to "") }
        }
        val error = assertThrows(IllegalArgumentException::class.java) {
            import(entries)
        }
        assertTrue(error.message?.contains("条目过多") == true)
    }

    @Test
    fun acceptsValidBackupWithFoldersAndVersions() {
        val count = import(
            listOf(
                "index.json" to
                    """[{"id":"a","name":"签到","createdAt":1,"updatedAt":2}]""",
                "folders.json" to
                    """[{"id":"f1","name":"工作","createdAt":1}]""",
                "a.js" to "log(1);",
                "versions.json" to
                    """[{"id":"v1","scriptId":"a","version":1,"note":"","createdAt":1}]""",
                "versions/a/v1.js" to "log(0);"
            )
        )

        assertEquals(1, count)
        val target = temp.root.listFiles()!!.first { it.isDirectory }
        assertEquals("log(1);", File(target, "a.js").readText())
        assertEquals("log(0);", File(target, "versions/a/v1.js").readText())
    }

    @Test
    fun acceptsBackupWithoutOptionalIndexes() {
        val count = import(
            listOf(
                "index.json" to
                    """[{"id":"a","name":"签到","createdAt":1,"updatedAt":2}]""",
                "a.js" to "log(1);"
            )
        )

        assertEquals(1, count)
        val target = temp.root.listFiles()!!.first { it.isDirectory }
        assertFalse(File(target, "folders.json").exists())
        assertFalse(File(target, "versions.json").exists())
    }

    @Test
    fun rejectsIndexWithSkippedCorruptRecord() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            import(
                listOf(
                    "index.json" to
                        """[{"id":"a","name":"正常","createdAt":1,"updatedAt":2},
                           {"id":"../evil","name":"坏","createdAt":1,"updatedAt":2}]""",
                    "a.js" to "log(1);"
                )
            )
        }
        assertTrue(error.message?.contains("损坏记录") == true)
    }

    @Test
    fun rejectsDuplicateVersionId() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            import(
                listOf(
                    "index.json" to
                        """[{"id":"a","name":"签到","createdAt":1,"updatedAt":2}]""",
                    "a.js" to "log(1);",
                    "versions.json" to
                        """[{"id":"v1","scriptId":"a","version":1,"note":"","createdAt":1},
                           {"id":"v1","scriptId":"a","version":2,"note":"","createdAt":2}]""",
                    "versions/a/v1.js" to "log(0);"
                )
            )
        }
        assertTrue(error.message?.contains("重复版本 ID") == true)
    }

    @Test
    fun rejectsVersionForUnknownScript() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            import(
                listOf(
                    "index.json" to
                        """[{"id":"a","name":"签到","createdAt":1,"updatedAt":2}]""",
                    "a.js" to "log(1);",
                    "versions.json" to
                        """[{"id":"v1","scriptId":"b","version":1,"note":"","createdAt":1}]""",
                    "versions/b/v1.js" to "log(0);"
                )
            )
        }
        assertTrue(error.message?.contains("不存在的脚本") == true)
    }

    @Test
    fun rejectsNulInScriptId() {
        val badId = "a" + '\u0000' + "b"
        val error = assertThrows(IllegalArgumentException::class.java) {
            import(
                listOf(
                    "index.json" to
                        """[{"id":"$badId","name":"坏","createdAt":1,"updatedAt":2}]""",
                    "a.js" to "log(1);"
                )
            )
        }
        assertTrue(error.message?.contains("损坏") == true)
    }

    @Test
    fun rejectsZipSlipEntry() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            import(
                listOf(
                    "../evil.js" to "log(1);",
                    "index.json" to "[]"
                )
            )
        }
        assertTrue(error.message?.contains("非法备份路径") == true)
    }

    @Test
    fun rejectsAbsolutePathEntry() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            import(listOf("/index.json" to "[]"))
        }
        assertTrue(error.message?.contains("非法备份路径") == true)
    }

    @Test
    fun rejectsBackslashAndDotSegmentEntries() {
        for (name in listOf("versions\\a\\v1.js", "versions/./a/v1.js", "a/../b.js")) {
            val error = assertThrows(IllegalArgumentException::class.java) {
                import(listOf(name to "x"))
            }
            assertTrue(
                "entry $name should be rejected",
                error.message?.contains("非法备份路径") == true
            )
        }
    }

    @Test
    fun rejectsOversizedIndexEntry() {
        val huge = "[" + " ".repeat(2 * 1024 * 1024 + 16) + "]"
        val error = assertThrows(IllegalArgumentException::class.java) {
            import(listOf("index.json" to huge))
        }
        assertTrue(error.message?.contains("条目过大") == true)
    }

    @Test
    fun rejectsTooManyIndexRecords() {
        val records = buildString {
            append("[")
            repeat(10_001) { index ->
                if (index > 0) append(",")
                append("""{"id":"s$index","name":"脚本$index","createdAt":1,"updatedAt":2}""")
            }
            append("]")
        }
        val error = assertThrows(IllegalArgumentException::class.java) {
            import(listOf("index.json" to records))
        }
        assertTrue(error.message?.contains("数量过多") == true)
    }
}
