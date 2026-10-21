package com.microauto.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunTokenRegistryTest {

    @Test
    fun onlyCurrentTokenCanFinish() {
        val registry = RunTokenRegistry()
        val first = registry.begin(startId = 1)
        val second = registry.begin(startId = 2)

        assertNotEquals(first.id, second.id)
        assertFalse(registry.finish(first))
        assertTrue(registry.isCurrent(second))
        assertTrue(registry.finish(second))
        assertFalse(registry.finish(second))
        assertFalse(registry.isCurrent(first))
    }

    @Test
    fun onlyCurrentTokenIdCanStop() {
        val registry = RunTokenRegistry()
        val old = registry.begin(startId = 1)
        val current = registry.begin(startId = 2)

        assertFalse(registry.isCurrentId(old.id))
        assertTrue(registry.isCurrentId(current.id))
        assertFalse(registry.isCurrentId(null))
        assertFalse(registry.isCurrentId(-1L))
    }

    @Test
    fun clearInvalidatesCurrentToken() {
        val registry = RunTokenRegistry()
        val token = registry.begin(startId = 3)

        assertTrue(registry.isCurrent(token))
        assertEquals(token, registry.clear())
        assertFalse(registry.isCurrent(token))
        assertFalse(registry.finish(token))
    }

    @Test
    fun tokenIdsAreUniqueAcrossRuns() {
        val registry = RunTokenRegistry()
        val ids = (1..100).map { registry.begin(it).id }

        assertTrue(ids.toSet().size == ids.size)
    }

    @Test
    fun beginAfterClearStartsFreshOwnership() {
        val registry = RunTokenRegistry()
        val old = registry.begin(startId = 1)
        registry.clear()
        val fresh = registry.begin(startId = 2)

        assertFalse(registry.finish(old))
        assertTrue(registry.isCurrent(fresh))
        assertTrue(registry.finish(fresh))
    }

    @Test
    fun tokenCarriesItsStartId() {
        val registry = RunTokenRegistry()
        val token = registry.begin(startId = 42)

        assertEquals(42, token.startId)
        assertEquals(1L, token.id)
    }

    @Test
    fun clearOnEmptyRegistryReturnsNull() {
        val registry = RunTokenRegistry()

        assertTrue(registry.clear() == null)
    }

    @Test
    fun currentIdCheckRejectsNullAndStaleIds() {
        val registry = RunTokenRegistry()
        val token = registry.begin(startId = 5)

        assertTrue(registry.isCurrentId(token.id))
        assertFalse(registry.isCurrentId(null))
        assertFalse(registry.isCurrentId(token.id + 1))

        registry.clear()
        assertFalse(registry.isCurrentId(token.id))
    }
}
