package com.microauto.script

import java.util.concurrent.atomic.AtomicLong

class RunTokenRegistry {
    data class Token(val id: Long, val startId: Int)

    private var current: Token? = null

    fun begin(startId: Int): Token {
        val token = Token(NEXT_ID.incrementAndGet(), startId)
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

    private companion object {
        val NEXT_ID = AtomicLong(0L)
    }
}
