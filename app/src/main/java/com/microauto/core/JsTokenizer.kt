package com.microauto.core

enum class JsTokenType {
    COMMENT,
    STRING,
    KEYWORD,
    NUMBER,
    BUILTIN
}

data class JsToken(
    val start: Int,
    val end: Int,
    val type: JsTokenType
)

object JsTokenizer {
    private val tokenPattern = Regex(
        """(//[^\n]*|/\*[\s\S]*?\*/)""" +
            """|("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\])*`)""" +
            """|(\b(?:const|let|var|function|return|if|else|for|while|do|switch|case|break|continue|new|try|catch|finally|throw|class|extends|async|await|of|in|typeof|instanceof|delete|void|yield|true|false|null|undefined|this)\b)""" +
            """|(\b\d+(?:\.\d+)?\b)""" +
            """|(\b(?:textContains|textMatches|descContains|idContains|waitForPackage|scrollForward|currentPackage|launchApp|findOne|findAll|longClick|className|clickable|selected|enabled|waitFor|setText|bounds|exists|press|swipe|sleep|toast|click|text|desc|exit|dump|back|home|recents|log|id)\b)"""
    )

    fun tokenize(text: String): List<JsToken> {
        if (text.isEmpty()) return emptyList()
        val tokens = ArrayList<JsToken>()
        for (match in tokenPattern.findAll(text)) {
            val type = when {
                match.groups[1] != null -> JsTokenType.COMMENT
                match.groups[2] != null -> JsTokenType.STRING
                match.groups[3] != null -> JsTokenType.KEYWORD
                match.groups[4] != null -> JsTokenType.NUMBER
                else -> JsTokenType.BUILTIN
            }
            tokens += JsToken(match.range.first, match.range.last + 1, type)
        }
        return tokens
    }
}
