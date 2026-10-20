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

    @Test
    fun matchesIdAndDesc() {
        val target = FakeNode(viewId = "com.demo:id/sign", desc = "签到按钮")
        val root = FakeNode(
            children = listOf(FakeNode(viewId = "com.demo:id/other"), target)
        )

        val selector = Selector(id = "com.demo:id/sign", desc = "签到按钮")

        assertEquals(target, SelectorMatcher.findFirst(root, selector))
    }

    @Test
    fun matchesStateConditions() {
        val target = FakeNode(clickable = true, enabled = false, selected = true)
        val root = FakeNode(
            children = listOf(FakeNode(clickable = true), target)
        )

        val selector = Selector(clickable = true, enabled = false, selected = true)

        assertEquals(target, SelectorMatcher.findFirst(root, selector))
    }

    @Test
    fun matchesTextContains() {
        val target = FakeNode(text = "每日签到领积分")
        val root = FakeNode(children = listOf(target))

        assertEquals(
            target,
            SelectorMatcher.findFirst(root, Selector(textContains = "签到"))
        )
    }

    @Test
    fun findAllRespectsLimit() {
        val root = FakeNode(children = (1..5).map { FakeNode(text = "签到") })

        assertEquals(
            3,
            SelectorMatcher.findAll(root, Selector(text = "签到"), limit = 3).size
        )
    }

    @Test
    fun findFirstRespectsMaxNodes() {
        val target = FakeNode(text = "签到")
        val root = FakeNode(
            children = listOf(
                FakeNode(children = listOf(FakeNode(children = listOf(target))))
            )
        )

        assertNull(SelectorMatcher.findFirst(root, Selector(text = "签到"), maxNodes = 2))
    }

    @Test
    fun emptySelectorMatchesRoot() {
        val root = FakeNode(text = "root")

        assertEquals(root, SelectorMatcher.findFirst(root, Selector()))
    }
}
