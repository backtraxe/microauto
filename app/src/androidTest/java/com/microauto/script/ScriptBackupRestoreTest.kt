package com.microauto.script

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.microauto.TestData
import com.microauto.schedule.SchedulePrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class ScriptBackupRestoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repository: ScriptRepository

    @Before
    fun setUp() {
        TestData.reset()
        File(context.filesDir, "scripts-before-restore").deleteRecursively()
        context.filesDir.listFiles { file -> file.name.startsWith("scripts-restore-") }
            ?.forEach { it.deleteRecursively() }
        repository = ScriptRepository(context)
    }

    private fun backupFile(entries: List<Pair<String, String>>): Uri {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        val file = File(context.cacheDir, "restore-test-${System.nanoTime()}.zip")
        file.writeBytes(output.toByteArray())
        return Uri.fromFile(file)
    }

    @Test
    fun restoreClearsEverySchedule() {
        val script = repository.create("旧脚本", "log(1);")
        SchedulePrefs.save(context, script.id, true, 8, 0)
        SchedulePrefs.setPending(context, script.id, System.currentTimeMillis() + 60_000)
        assertTrue(SchedulePrefs.enabled(context).isNotEmpty())

        val count = ScriptBackup.restore(
            context,
            backupFile(
                listOf(
                    "index.json" to
                        """[{"id":"${script.id}","name":"旧脚本","createdAt":1,"updatedAt":2}]""",
                    "${script.id}.js" to "log('new');"
                )
            )
        )

        assertEquals(1, count)
        assertEquals("log('new');", ScriptRepository(context).read(script.id))
        assertTrue(SchedulePrefs.enabled(context).isEmpty())
        assertTrue(SchedulePrefs.schedules(context).isEmpty())
        assertTrue(SchedulePrefs.pendingScriptIds(context).isEmpty())
    }

    @Test
    fun invalidBackupKeepsCurrentScripts() {
        val script = repository.create("保留脚本", "log('keep');")

        assertThrows(IllegalArgumentException::class.java) {
            ScriptBackup.restore(
                context,
                backupFile(
                    listOf(
                        "index.json" to
                            """[{"id":"${script.id}","name":"保留脚本","createdAt":1,"updatedAt":2}]"""
                    )
                )
            )
        }

        assertEquals("log('keep');", ScriptRepository(context).read(script.id))
        assertEquals("保留脚本", ScriptRepository(context).find(script.id)?.name)
    }

    @Test
    fun validBackupRestoresScriptsFoldersAndVersions() {
        val count = ScriptBackup.restore(
            context,
            backupFile(
                listOf(
                    "index.json" to
                        """[{"id":"s1","name":"脚本一","createdAt":1,"updatedAt":2},
                           {"id":"s2","name":"脚本二","createdAt":3,"updatedAt":4}]""",
                    "folders.json" to
                        """[{"id":"f1","name":"工作","createdAt":1}]""",
                    "s1.js" to "log('one');",
                    "s2.js" to "log('two');",
                    "versions.json" to
                        """[{"id":"v1","scriptId":"s1","version":1,"note":"快照","createdAt":1}]""",
                    "versions/s1/v1.js" to "log('old');"
                )
            )
        )

        assertEquals(2, count)
        val restored = ScriptRepository(context)
        assertEquals(setOf("脚本一", "脚本二"), restored.listAll().map { it.name }.toSet())
        assertEquals("log('one');", restored.read("s1"))
        assertEquals("log('two');", restored.read("s2"))
        assertEquals("工作", restored.folders().single().name)
        assertEquals("log('old');", restored.readVersion("v1"))
        assertEquals("快照", restored.versions("s1").single().note)
    }

    @Test
    fun traversalEntryIsRejectedAndDataKept() {
        val script = repository.create("保留脚本", "log('keep');")

        assertThrows(Exception::class.java) {
            ScriptBackup.restore(
                context,
                backupFile(
                    listOf(
                        "../evil.js" to "log('bad');",
                        "index.json" to "[]"
                    )
                )
            )
        }

        assertEquals("log('keep');", ScriptRepository(context).read(script.id))
    }

    @Test
    fun missingScriptFileIsRejectedAndDataKept() {
        val script = repository.create("保留脚本", "log('keep');")

        assertThrows(IllegalArgumentException::class.java) {
            ScriptBackup.restore(
                context,
                backupFile(
                    listOf(
                        "index.json" to
                            """[{"id":"other","name":"别的","createdAt":1,"updatedAt":2}]""",
                        "unrelated.js" to "log('x');"
                    )
                )
            )
        }

        assertEquals("log('keep');", ScriptRepository(context).read(script.id))
    }

    @Test
    fun recoversInterruptedSwapBeforeApplyingBackup() {
        val old = repository.create("旧脚本", "log('old');")
        val scripts = File(context.filesDir, "scripts")
        val previous = File(context.filesDir, "scripts-before-restore")
        assertTrue(scripts.renameTo(previous))
        ScriptRepository.clearCache()

        val count = ScriptBackup.restore(
            context,
            backupFile(
                listOf(
                    "index.json" to
                        """[{"id":"new","name":"新脚本","createdAt":1,"updatedAt":2}]""",
                    "new.js" to "log('new');"
                )
            )
        )

        assertEquals(1, count)
        assertEquals("log('new');", ScriptRepository(context).read("new"))
        assertNull(ScriptRepository(context).read(old.id))
        assertFalse(previous.exists())
    }

    @Test
    fun staleRestoreTempDirectoriesAreRemoved() {
        val stale = File(context.filesDir, "scripts-restore-stale")
        stale.mkdirs()
        File(stale, "junk").writeText("junk")

        ScriptBackup.restore(
            context,
            backupFile(
                listOf(
                    "index.json" to
                        """[{"id":"a","name":"A","createdAt":1,"updatedAt":2}]""",
                    "a.js" to "log(1);"
                )
            )
        )

        assertFalse(stale.exists())
    }

    @Test
    fun restoreClearsLegacyScheduleKeys() {
        SchedulePrefs.prefs(context).edit()
            .putBoolean("enabled", true)
            .putInt("hour", 8)
            .putInt("minute", 0)
            .putString("script_id", "legacy")
            .putString("pending_script_id", "legacy")
            .putLong("pending_deadline", System.currentTimeMillis() + 60_000)
            .commit()

        ScriptBackup.restore(
            context,
            backupFile(
                listOf(
                    "index.json" to
                        """[{"id":"a","name":"A","createdAt":1,"updatedAt":2}]""",
                    "a.js" to "log(1);"
                )
            )
        )

        val prefs = SchedulePrefs.prefs(context)
        assertFalse(prefs.contains("enabled"))
        assertFalse(prefs.contains("script_id"))
        assertFalse(prefs.contains("pending_script_id"))
        assertTrue(SchedulePrefs.schedules(context).isEmpty())
        assertTrue(SchedulePrefs.pendingScriptIds(context).isEmpty())
    }

    @Test
    fun recoversInterruptedRestoreBeforeRepositoryInit() {
        val script = repository.create("旧脚本", "log('old');")
        val current = File(context.filesDir, "scripts")
        val previous = File(context.filesDir, "scripts-before-restore")
        assertTrue(current.renameTo(previous))
        current.mkdirs()
        File(current, "index.json").writeText("[]")

        assertTrue(ScriptBackup.recoverInterruptedRestore(context))

        val restored = ScriptRepository(context)
        assertEquals("log('old');", restored.read(script.id))
        assertFalse(previous.exists())
    }

    @Test
    fun keepsValidCurrentAndDropsStalePrevious() {
        val script = repository.create("新脚本", "log('new');")
        val previous = File(context.filesDir, "scripts-before-restore")
        previous.mkdirs()
        File(previous, "index.json").writeText(
            """[{"id":"old","name":"旧脚本","createdAt":1,"updatedAt":2}]"""
        )
        File(previous, "old.js").writeText("log('old');")

        assertFalse(ScriptBackup.recoverInterruptedRestore(context))
        assertFalse(previous.exists())
        assertEquals("新脚本", ScriptRepository(context).find(script.id)?.name)
    }
}
