package com.microauto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsTokenizerTest {

    private fun JsToken.text(source: String): String = source.substring(start, end)

    @Test
    fun `tokenizes keywords strings comments and numbers`() {
        val source = "// note\nconst x = \"hello\";\nif (x) log(42);"
        val result = JsTokenizer.tokenize(source)
        assertTrue(result.any { it.type == JsTokenType.COMMENT && it.text(source) == "// note" })
        assertTrue(result.any { it.type == JsTokenType.KEYWORD && it.text(source) == "const" })
        assertTrue(result.any { it.type == JsTokenType.KEYWORD && it.text(source) == "if" })
        assertTrue(result.any { it.type == JsTokenType.STRING && it.text(source) == "\"hello\"" })
        assertTrue(result.any { it.type == JsTokenType.NUMBER && it.text(source) == "42" })
        assertTrue(result.any { it.type == JsTokenType.BUILTIN && it.text(source) == "log" })
    }

    @Test
    fun `does not tokenize inside strings or comments`() {
        val source = "const s = \"return 42\"; // if 7"
        val result = JsTokenizer.tokenize(source)
        assertTrue(result.none { it.type == JsTokenType.KEYWORD && it.text(source) == "return" })
        assertTrue(result.none { it.type == JsTokenType.NUMBER && it.text(source) == "42" })
        assertTrue(result.none { it.type == JsTokenType.KEYWORD && it.text(source) == "if" })
        assertTrue(result.none { it.type == JsTokenType.NUMBER && it.text(source) == "7" })
    }

    @Test
    fun `handles block comments and template strings`() {
        val source = "/* multi\nline */ const t = `v=${'$'}{x}`;"
        val result = JsTokenizer.tokenize(source)
        assertTrue(result.any { it.type == JsTokenType.COMMENT && it.text(source).startsWith("/*") })
        assertTrue(result.any { it.type == JsTokenType.STRING && it.text(source).startsWith("`") })
        assertTrue(result.any { it.type == JsTokenType.KEYWORD && it.text(source) == "const" })
    }

    @Test
    fun `empty text has no tokens`() {
        assertEquals(emptyList<JsToken>(), JsTokenizer.tokenize(""))
    }
}
