package com.microauto.script

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.microauto.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ScriptRepositoryPathTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repository: ScriptRepository

    @Before
    fun setUp() {
        TestData.reset()
        repository = ScriptRepository(context)
    }

    @Test
    fun outOfBoundsIdsNeverTouchFilesOutsideScripts() {
        val outside = File(context.filesDir, "outside.js").apply { writeText("secret") }

        assertNull(repository.read("../outside"))
        assertFalse(repository.save("../outside", "overwritten"))
        assertFalse(repository.delete("../outside"))
        assertNull(repository.publishVersion("../outside", "note"))
        assertNull(repository.readVersion("../outside"))
        assertFalse(repository.restoreVersion("../outside"))
        assertFalse(repository.deleteVersion("../outside"))

        assertEquals("secret", outside.readText())
        assertTrue(outside.exists())
    }
}
