package com.microauto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeDumpTest {

    private data class FakeNode(
        override val text: String? = null,
        override val desc: String? = null,
        override val viewId: String? = null,
        override val className: String? = null,
        override val clickable: Boolean = false,
        override val enabled: Boolean = true,
        override val selected: Boolean = false,
        override val isPassword: Boolean = false,
        override val bounds: Bounds? = null,
        override val children: List<NodeLike> = emptyList()
    ) : NodeLike

    @Test
    fun masksPasswordText() {
        val dump = NodeDump.format(
            FakeNode(
                className = "android.widget.EditText",
                text = "hunter2",
                isPassword = true,
                children = listOf(FakeNode(text = "visible"))
            )
        )

        assertTrue(dump.contains("text=<hidden>"))
        assertFalse(dump.contains("hunter2"))
        assertTrue(dump.contains("text=visible"))
    }

    @Test
    fun masksPasswordDescription() {
        val dump = NodeDump.format(
            FakeNode(
                className = "android.widget.EditText",
                text = "hunter2",
                desc = "password-secret",
                isPassword = true
            )
        )

        assertTrue(dump.contains("text=<hidden>"))
        assertTrue(dump.contains("desc=<hidden>"))
        assertFalse(dump.contains("hunter2"))
        assertFalse(dump.contains("password-secret"))
    }

    @Test
    fun keepsNormalTextAndBounds() {
        val dump = NodeDump.format(
            FakeNode(
                text = "签到",
                desc = "签到按钮",
                className = "android.widget.Button",
                bounds = Bounds(1, 2, 3, 4)
            )
        )

        assertTrue(dump.contains("text=签到"))
        assertTrue(dump.contains("desc=签到按钮"))
        assertTrue(dump.contains("[1,2][3,4]"))    }

    @Test
    fun masksEveryPasswordNodeInTree() {
        val dump = NodeDump.format(
            FakeNode(
                className = "root",
                children = listOf(
                    FakeNode(text = "secret1", isPassword = true),
                    FakeNode(
                        children = listOf(
                            FakeNode(text = "secret2", isPassword = true)
                        )
                    )
                )
            )
        )

        assertEquals(2, "text=<hidden>".toRegex().findAll(dump).count())
        assertFalse(dump.contains("secret1"))
        assertFalse(dump.contains("secret2"))
    }
}
