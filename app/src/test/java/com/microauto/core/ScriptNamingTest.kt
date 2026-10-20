package com.microauto.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ScriptNamingTest {

    @Test
    fun stripsKnownExtensions() {
        assertEquals("签到脚本", ScriptNaming.fromFileName("签到脚本.js"))
        assertEquals("签到脚本", ScriptNaming.fromFileName("签到脚本.txt"))
        assertEquals("签到脚本", ScriptNaming.fromFileName("签到脚本.mjs"))
    }

    @Test
    fun keepsNameWithoutExtension() {
        assertEquals("签到脚本", ScriptNaming.fromFileName("签到脚本"))
    }

    @Test
    fun handlesPathsAndEmptyNames() {
        assertEquals("checkin", ScriptNaming.fromFileName("dir/checkin.js"))
        assertEquals("导入脚本", ScriptNaming.fromFileName(".js"))
    }

    @Test
    fun sanitizesWhitespaceAndLength() {
        assertEquals("签到", ScriptNaming.sanitize("  签到\n "))
        assertEquals(60L, ScriptNaming.sanitize("a".repeat(80)).length.toLong())
    }

    @Test
    fun addsSuffixForDuplicateName() {
        assertEquals("脚本 (2)", ScriptNaming.uniqueName("脚本", setOf("脚本")))
        assertEquals(
            "脚本 (3)",
            ScriptNaming.uniqueName("脚本", setOf("脚本", "脚本 (2)"))
        )
    }

    @Test
    fun ignoresExcludedNameWhenRenaming() {
        assertEquals("脚本", ScriptNaming.uniqueName("脚本", setOf("脚本"), exclude = "脚本"))
    }

    @Test
    fun fallsBackForBlankUniqueName() {
        assertEquals("未命名", ScriptNaming.uniqueName("   ", emptySet()))
    }

    @Test
    fun handlesCaseInsensitiveExtension() {
        assertEquals("CHECKIN", ScriptNaming.fromFileName("CHECKIN.JS"))
    }

    @Test
    fun keepsUnknownExtension() {
        assertEquals("archive.tar.gz", ScriptNaming.fromFileName("archive.tar.gz"))
    }

    @Test
    fun addsNextSuffixWhenManyDuplicatesExist() {
        assertEquals(
            "脚本 (4)",
            ScriptNaming.uniqueName("脚本", setOf("脚本", "脚本 (2)", "脚本 (3)"))
        )
    }

    @Test
    fun blankNameFallsBackToSuffixWhenBaseExists() {
        assertEquals("未命名 (2)", ScriptNaming.uniqueName(" ", setOf("未命名")))
    }

    @Test
    fun replacesNewlinesInSanitize() {
        assertEquals("签到 脚本", ScriptNaming.sanitize("  签到\n脚本  "))
    }
}
