package com.microauto.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiDocsFilterTest {

    private val fixtures = listOf(
        ApiSection(
            "选择器",
            listOf(
                ApiEntry(
                    "text(value)",
                    "按文本精确匹配。",
                    "text(\"登录\").click();",
                ),
                ApiEntry(
                    "id(value)",
                    "按资源 id 匹配。",
                    "id(\"com.example:id/input\").setText(\"x\");",
                ),
            ),
        ),
        ApiSection(
            "手势",
            listOf(
                ApiEntry("click(x, y)", "点击坐标。", "click(540, 1200);"),
            ),
        ),
    )

    @Test
    fun emptyQueryReturnsAllSections() {
        assertEquals(fixtures, ApiDocsFilter.filter(fixtures, ""))
        assertEquals(fixtures, ApiDocsFilter.filter(fixtures, "   "))
    }

    @Test
    fun matchesSignatureIgnoringCase() {
        val result = ApiDocsFilter.filter(fixtures, "CLICK(X")

        assertEquals(1, result.size)
        assertEquals("手势", result[0].title)
        assertEquals(1, result[0].entries.size)
        assertEquals("click(x, y)", result[0].entries[0].signature)
    }

    @Test
    fun matchesDescription() {
        val result = ApiDocsFilter.filter(fixtures, "资源 ID")

        assertEquals(1, result.size)
        assertEquals("id(value)", result[0].entries.single().signature)
    }

    @Test
    fun matchesExample() {
        val result = ApiDocsFilter.filter(fixtures, "com.example")

        assertEquals(1, result.size)
        assertEquals("id(value)", result[0].entries.single().signature)
    }

    @Test
    fun sectionTitleMatchKeepsWholeSection() {
        val result = ApiDocsFilter.filter(fixtures, "选择器")

        assertEquals(1, result.size)
        assertEquals(2, result[0].entries.size)
    }

    @Test
    fun noResultReturnsEmptyList() {
        assertTrue(ApiDocsFilter.filter(fixtures, "zzzz-not-found").isEmpty())
    }

    @Test
    fun entryCountCountsFilteredEntries() {
        val result = ApiDocsFilter.filter(fixtures, "文本")

        assertEquals(1, ApiDocsFilter.entryCount(result))
        assertEquals(3, ApiDocsFilter.entryCount(fixtures))
    }

    @Test
    fun realDocsFindOneMatchesExpectedEntries() {
        val result = ApiDocsFilter.filter(ApiDocs.sections, "findOne")

        assertFalse(result.isEmpty())
        assertTrue(result.all { section -> section.entries.isNotEmpty() })
        assertTrue(
            result.any { section ->
                section.entries.any { it.signature == ".findOne(timeout = 10000)" }
            }
        )
        assertEquals(
            result.sumOf { it.entries.size },
            ApiDocsFilter.entryCount(result),
        )
    }

    @Test
    fun realDocsExampleSearchMatchesLaunchApp() {
        val result = ApiDocsFilter.filter(ApiDocs.sections, "com.tencent.mm")

        assertTrue(
            result.any { section ->
                section.entries.any { it.signature == "launchApp(pkg)" }
            }
        )
    }
}
