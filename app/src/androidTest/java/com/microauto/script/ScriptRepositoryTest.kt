package com.microauto.script

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.microauto.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ScriptRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repository: ScriptRepository

    @Before
    fun setUp() {
        TestData.reset()
        repository = ScriptRepository(context)
    }

    @Test
    fun createsBuiltinsOnFirstRun() {
        val scripts = repository.ensureInitialized()

        assertEquals(BuiltinScripts.all.size, scripts.size)
        val sample = scripts.first { it.name == "示例：节点树" }
        assertNotNull(repository.read(sample.id))
        assertTrue(repository.read(sample.id)!!.contains("toast"))
    }

    @Test
    fun migratesLegacyScript() {
        val legacy = File(context.filesDir, "script.js")
        legacy.writeText("log('legacy');")

        val scripts = repository.ensureInitialized()

        val migrated = scripts.first { it.name == "默认脚本" }
        assertEquals("log('legacy');", repository.read(migrated.id))
        assertFalse(legacy.exists())
        assertEquals(BuiltinScripts.all.size + 1, scripts.size)
    }

    @Test
    fun createsSavesRenamesAndDeletes() {
        val created = repository.create("测试脚本", "log(1);")
        assertEquals("测试脚本", created.name)
        assertEquals("log(1);", repository.read(created.id))

        assertTrue(repository.save(created.id, "log(2);"))
        assertEquals("log(2);", repository.read(created.id))

        assertTrue(repository.rename(created.id, "重命名后"))
        assertEquals("重命名后", repository.find(created.id)?.name)

        assertTrue(repository.delete(created.id))
        assertEquals(0, repository.list().size)
    }

    @Test
    fun duplicateNamesGetSuffix() {
        val first = repository.create("脚本", "1")
        val second = repository.create("脚本", "2")

        assertEquals("脚本", first.name)
        assertEquals("脚本 (2)", second.name)
        assertEquals(2, repository.list().size)
    }

    @Test
    fun emptyLibraryStaysEmptyAfterInitialization() {
        val created = repository.create("临时", "1")
        repository.delete(created.id)

        assertTrue(repository.indexExists())
        assertTrue(repository.ensureInitialized().isEmpty())
    }

    @Test
    fun createsRenamesAndDeletesFolders() {
        val folder = repository.createFolder("工作")
        assertNotNull(folder)
        assertEquals("工作", folder!!.name)
        assertEquals(1, repository.folders().size)

        assertTrue(repository.renameFolder(folder.id, "日常"))
        assertEquals("日常", repository.folder(folder.id)?.name)

        assertTrue(repository.deleteFolder(folder.id))
        assertTrue(repository.folders().isEmpty())
    }

    @Test
    fun duplicateFolderNamesGetSuffix() {
        val first = repository.createFolder("项目")!!
        val second = repository.createFolder("项目")!!

        assertEquals("项目", first.name)
        assertEquals("项目 (2)", second.name)
    }

    @Test
    fun movesScriptsBetweenRootAndFolders() {
        val folder = repository.createFolder("工作")!!
        val script = repository.create("脚本", "log(1);")

        assertTrue(repository.moveToFolder(script.id, folder.id))
        assertEquals(1, repository.list(folder.id).size)
        assertEquals(0, repository.list().size)
        assertEquals(1, repository.count(folder.id))

        assertTrue(repository.moveToFolder(script.id, null))
        assertEquals(1, repository.list().size)
        assertEquals(0, repository.list(folder.id).size)
    }

    @Test
    fun deletingFolderMovesScriptsToRoot() {
        val folder = repository.createFolder("工作")!!
        val script = repository.create("脚本", "log(1);", folder.id)

        assertTrue(repository.deleteFolder(folder.id))

        assertNull(repository.find(script.id)?.folderId)
        assertEquals(1, repository.list().size)
        assertTrue(repository.list(folder.id).isEmpty())
    }

    @Test
    fun publishesAndRestoresVersions() {
        val script = repository.create("版本脚本", "log(1);")
        val v1 = repository.publishVersion(script.id, "第一版")!!
        assertEquals(1, v1.version)
        assertEquals("第一版", v1.note)

        repository.save(script.id, "log(2);")
        val v2 = repository.publishVersion(script.id, "")!!
        assertEquals(2, v2.version)
        assertEquals(2, repository.versions(script.id).size)
        assertEquals("log(1);", repository.readVersion(v1.id))
        assertEquals("log(2);", repository.readVersion(v2.id))

        assertTrue(repository.restoreVersion(v1.id))
        assertEquals("log(1);", repository.read(script.id))
    }

    @Test
    fun deletesVersionsAndCleansUpWithScript() {
        val script = repository.create("版本脚本", "log(1);")
        val v1 = repository.publishVersion(script.id, "")!!

        assertTrue(repository.deleteVersion(v1.id))
        assertTrue(repository.versions(script.id).isEmpty())

        repository.publishVersion(script.id, "")
        assertTrue(repository.delete(script.id))
        assertTrue(repository.versions(script.id).isEmpty())
    }

    @Test
    fun restoreSnapshotsCurrentContentFirst() {
        val script = repository.create("版本脚本", "log(1);")
        val v1 = repository.publishVersion(script.id, "第一版")!!
        repository.save(script.id, "log(2);")

        assertTrue(repository.restoreVersion(v1.id))

        val versions = repository.versions(script.id)
        assertTrue(versions.any { it.note.contains("恢复前") })
        assertEquals("log(1);", repository.read(script.id))
    }

    @Test
    fun newRepositoryInstanceSeesWritesFromAnotherInstance() {
        val first = ScriptRepository(context)
        val script = first.create("共享脚本", "log(1);")
        val second = ScriptRepository(context)

        assertEquals(1, second.list().size)
        assertTrue(second.save(script.id, "log(2);"))

        assertEquals("log(2);", first.read(script.id))
        assertEquals("共享脚本", first.find(script.id)?.name)
    }

    @Test
    fun recoversFromCorruptIndexAndKeepsBackup() {
        val scriptsDir = File(context.filesDir, "scripts").apply { mkdirs() }
        File(scriptsDir, "index.json").writeText("{not json")
        RepositoryHealth.clear()

        val scripts = repository.ensureInitialized()

        assertTrue(scripts.isEmpty())
        assertNotNull(RepositoryHealth.message.value)
        val backups = scriptsDir.listFiles { file -> file.name.startsWith("index.json.corrupt-") }
        assertTrue(backups?.isNotEmpty() == true)
    }

    @Test
    fun keepsValidEntriesWhenOneIndexRecordIsMalformed() {
        val scriptsDir = File(context.filesDir, "scripts").apply { mkdirs() }
        File(scriptsDir, "index.json").writeText(
            """[{"id":"a","name":"保留","createdAt":1,"updatedAt":2},{"name":"坏记录"}]"""
        )
        File(scriptsDir, "a.js").writeText("log(1);")
        RepositoryHealth.clear()

        val scripts = repository.ensureInitialized()

        assertEquals(1, scripts.size)
        assertEquals("保留", scripts[0].name)
        assertNotNull(RepositoryHealth.message.value)
        assertEquals("log(1);", repository.read("a"))
    }
}
