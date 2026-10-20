package com.microauto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.regex.Pattern

class SelectorMatcherTest {

    private class FakeNode(
        override val text: String? = null,
        override val desc: String? = null,
        override val viewId: String? = null,
        override val className: String? = null,
        override val clickable: Boolean = false,
        override val enabled: Boolean = true,
        override val selected: Boolean = false,
        override val bounds: Bounds? = null,
        override val children: List<NodeLike> = emptyList()
    ) : NodeLike

    @Test
    fun findsNodeByText() {
        val target = FakeNode(text = "签到", clickable = true)
        val root = FakeNode(children = listOf(FakeNode(text = "首页"), target))

        assertEquals(target, SelectorMatcher.findFirst(root, Selector(text = "签到")))
    }

    @Test
    fun combinesConditions() {
        val target = FakeNode(
            text = "签到",
            viewId = "com.demo:id/sign",
            clickable = true
        )
        val root = FakeNode(
            children = listOf(FakeNode(text = "签到"), target)
        )

        val selector = Selector(
            text = "签到",
            id = "com.demo:id/sign",
            clickable = true
        )

        assertEquals(target, SelectorMatcher.findFirst(root, selector))
    }

    @Test
    fun matchesTextPattern() {
        val target = FakeNode(text = "每日签到")
        val root = FakeNode(children = listOf(target))

        val selector = Selector(textPattern = Pattern.compile("签到"))

        assertEquals(target, SelectorMatcher.findFirst(root, selector))
    }

    @Test
    fun returnsNullWhenNoMatch() {
        val root = FakeNode(children = listOf(FakeNode(text = "首页")))

        assertNull(SelectorMatcher.findFirst(root, Selector(text = "签到")))
    }

    @Test
    fun findsShallowestMatchFirst() {
        val shallow = FakeNode(text = "签到")
        val deep = FakeNode(text = "签到")
        val root = FakeNode(
            children = listOf(
                FakeNode(children = listOf(FakeNode(children = listOf(deep)))),
                shallow
            )
        )

        assertEquals(shallow, SelectorMatcher.findFirst(root, Selector(text = "签到")))
    }
}
