package com.microauto.script

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.microauto.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    fun createsSampleOnFirstRun() {
        val scripts = repository.ensureInitialized()

        assertEquals(1, scripts.size)
        assertEquals("示例", scripts[0].name)
        assertNotNull(repository.read(scripts[0].id))
        assertTrue(repository.read(scripts[0].id)!!.contains("toast"))
    }

    @Test
    fun migratesLegacyScript() {
        val legacy = File(context.filesDir, "script.js")
        legacy.writeText("log('legacy');")

        val scripts = repository.ensureInitialized()

        assertEquals(1, scripts.size)
        assertEquals("默认脚本", scripts[0].name)
        assertEquals("log('legacy');", repository.read(scripts[0].id))
        assertFalse(legacy.exists())
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
}
