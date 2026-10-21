package com.microauto.script

class RunTokenRegistry {
    data class Token(val id: Long, val startId: Int)

    private var nextId = 0L
    private var current: Token? = null

    fun begin(startId: Int): Token {
        val token = Token(++nextId, startId)
        current = token
        return token
    }

    fun isCurrent(token: Token): Boolean = current?.id == token.id

    fun isCurrentId(tokenId: Long?): Boolean = tokenId != null && current?.id == tokenId

    fun finish(token: Token): Boolean {
        if (!isCurrent(token)) return false
        current = null
        return true
    }

    fun clear(): Token? {
        val token = current
        current = null
        return token
    }
}
