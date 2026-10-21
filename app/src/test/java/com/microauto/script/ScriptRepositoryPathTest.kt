package com.microauto.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class ScriptRepositoryPathTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun rejectsIdsThatEscapeTheScriptsDirectory() {
        val scripts = temp.newFolder("scripts")
        val outside = File(temp.root, "outside.js").apply { writeText("secret") }

        for (id in listOf(
            "../outside",
            "..",
            "a/b",
            "a\\b",
            "a\u0000b",
            "",
            ".",
            "a".repeat(65)
        )) {
            assertNull("id should be rejected: $id", ScriptPaths.scriptFile(scripts, id))
        }
        assertEquals("secret", outside.readText())
        assertTrue(
            ScriptPaths.scriptFile(scripts, "valid-id_1")!!
                .path.startsWith(scripts.canonicalFile.path)
        )
    }

    @Test
    fun rejectsVersionPathsThatEscapeTheVersionsDirectory() {
        val versions = temp.newFolder("versions")

        assertNull(ScriptPaths.versionFile(versions, "../scripts", "v1"))
        assertNull(ScriptPaths.versionFile(versions, "a", "../../outside"))
        assertNull(ScriptPaths.versionDir(versions, "../scripts"))
        assertTrue(
            ScriptPaths.versionFile(versions, "script-1", "version-1")!!
                .path.startsWith(versions.canonicalFile.path)
        )
    }

    @Test
    fun onlyAcceptsSafeIdCharacters() {
        assertTrue(ScriptPaths.isValidId("A-Z_a-z-0"))
        assertTrue(ScriptPaths.isValidId("a".repeat(64)))
        assertNull(ScriptPaths.scriptFile(temp.newFolder("scripts"), "a".repeat(65)))
        for (id in listOf("../a", "a/b", "a\\b", "a b", "a.b", "/a", "a\u0000b")) {
            assertNull(ScriptPaths.scriptFile(temp.newFolder(), id))
        }
    }

    @Test
    fun rejectsSymlinkedFileThatEscapesRoot() {
        val scripts = temp.newFolder("scripts")
        val outside = File(temp.root, "outside-secret.js").apply { writeText("secret") }
        Files.createSymbolicLink(File(scripts, "link.js").toPath(), outside.toPath())

        assertNull(ScriptPaths.scriptFile(scripts, "link"))
    }

    @Test
    fun rejectsSiblingDirectorySharingRootPrefix() {
        val root = temp.newFolder("scripts")
        temp.newFolder("scripts-evil")

        assertNull(ScriptPaths.resolveWithin(root, "../scripts-evil/x.js"))
    }
}
