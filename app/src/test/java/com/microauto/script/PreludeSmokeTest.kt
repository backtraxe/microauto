package com.microauto.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import java.io.File

class PreludeSmokeTest {

    class FakeSelector {
        var textValue: String? = null
        var textContainsValue: String? = null
        var patternSource: String? = null
        var patternFlags: String? = null
        var idValue: String? = null
        var idContainsValue: String? = null
        var descValue: String? = null
        var descContainsValue: String? = null
        var classNameValue: String? = null
        var clickableValue: Boolean? = null
        var enabledValue: Boolean? = null
        var selectedValue: Boolean? = null

        fun text(value: String): FakeSelector {
            textValue = value
            return this
        }

        fun textContains(value: String): FakeSelector {
            textContainsValue = value
            return this
        }

        fun textMatches(source: String, flags: String): FakeSelector {
            patternSource = source
            patternFlags = flags
            return this
        }

        fun id(value: String): FakeSelector {
            idValue = value
            return this
        }

        fun idContains(value: String): FakeSelector {
            idContainsValue = value
            return this
        }

        fun desc(value: String): FakeSelector {
            descValue = value
            return this
        }

        fun descContains(value: String): FakeSelector {
            descContainsValue = value
            return this
        }

        fun className(value: String): FakeSelector {
            classNameValue = value
            return this
        }

        fun clickable(value: Boolean): FakeSelector {
            clickableValue = value
            return this
        }

        fun enabled(value: Boolean): FakeSelector {
            enabledValue = value
            return this
        }

        fun selected(value: Boolean): FakeSelector {
            selectedValue = value
            return this
        }
    }

    class FakeUiObject {
        var clicks = 0

        fun click(): Boolean {
            clicks++
            return true
        }
    }

    class FakeNative {
        var findOneCalled = false
        var findOneTimeout: Long = -1
        var findAllCalled = false
        var returnNull = false
        var tapArgs: Triple<Int, Int, Long>? = null
        var swipeArgs: List<Long>? = null
        var sleepMs: Long = -1
        var launchPackage: String? = null
        var waitPackage: String? = null
        var waitTimeout: Long = -1
        var backCalled = false
        var homeCalled = false
        var recentsCalled = false
        var dumpCalled = false
        val logs = mutableListOf<String>()
        val toasts = mutableListOf<String>()
        val selector = FakeSelector()

        fun selector(): FakeSelector = selector

        fun findOne(selector: FakeSelector, timeout: Long): FakeUiObject? {
            findOneCalled = true
            findOneTimeout = timeout
            return if (returnNull) null else FakeUiObject()
        }

        fun findAll(selector: FakeSelector): Array<FakeUiObject> {
            findAllCalled = true
            return arrayOf(FakeUiObject(), FakeUiObject())
        }

        fun tap(x: Int, y: Int, duration: Long): Boolean {
            tapArgs = Triple(x, y, duration)
            return true
        }

        fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long): Boolean {
            swipeArgs = listOf(x1.toLong(), y1.toLong(), x2.toLong(), y2.toLong(), duration)
            return true
        }

        fun sleep(ms: Long) {
            sleepMs = ms
        }

        fun toast(message: String) {
            toasts.add(message)
        }

        fun log(message: String) {
            logs.add(message)
        }

        fun launchApp(pkg: String): Boolean {
            launchPackage = pkg
            return true
        }

        fun currentPackage(): String = "com.demo"

        fun waitForPackage(pkg: String, timeout: Long): Boolean {
            waitPackage = pkg
            waitTimeout = timeout
            return true
        }

        fun back(): Boolean {
            backCalled = true
            return true
        }

        fun home(): Boolean {
            homeCalled = true
            return true
        }

        fun recents(): Boolean {
            recentsCalled = true
            return true
        }

        fun dump(): String {
            dumpCalled = true
            return "TREE"
        }

        fun exit(): Boolean {
            throw RuntimeException("exit")
        }
    }

    private fun run(fake: FakeNative, script: String) {
        val rhino = Context.enter()
        try {
            rhino.optimizationLevel = -1
            val scope = rhino.initStandardObjects()
            ScriptableObject.putProperty(scope, "native", Context.javaToJS(fake, scope))
            rhino.evaluateString(scope, prelude(), "prelude.js", 1, null)
            rhino.evaluateString(scope, script, "test.js", 1, null)
        } finally {
            Context.exit()
        }
    }

    private fun prelude(): String {
        val candidates = listOf(
            File("src/main/assets/prelude.js"),
            File("app/src/main/assets/prelude.js")
        )
        return candidates.first { it.exists() }.readText()
    }

    @Test
    fun selectorChainingAndFindOneWork() {
        val fake = FakeNative()
        run(
            fake,
            """
            var node = text('签到')
              .id('com.demo:id/sign')
              .desc('签到按钮')
              .className('android.widget.Button')
              .clickable(true)
              .enabled(true)
              .selected(false)
              .findOne(1234);
            log(node.click());
            """.trimIndent()
        )

        assertTrue(fake.findOneCalled)
        assertEquals(1234L, fake.findOneTimeout)
        assertEquals("签到", fake.selector.textValue)
        assertEquals("com.demo:id/sign", fake.selector.idValue)
        assertEquals("签到按钮", fake.selector.descValue)
        assertEquals("android.widget.Button", fake.selector.classNameValue)
        assertEquals(true, fake.selector.clickableValue)
        assertEquals(true, fake.selector.enabledValue)
        assertEquals(false, fake.selector.selectedValue)
        assertEquals(listOf("true"), fake.logs)
    }

    @Test
    fun existsWaitForFindAllAndTextMatchesWork() {
        val fake = FakeNative()
        fake.returnNull = true
        run(
            fake,
            """
            log(text('不存在').exists());
            log(text('不存在').waitFor(300));
            textMatches(/签到|打卡/i).findOne(1000);
            """.trimIndent()
        )

        assertEquals(listOf("false", "false"), fake.logs)
        assertEquals("签到|打卡", fake.selector.patternSource)
        assertTrue(fake.selector.patternFlags?.contains("i") == true)

        val fakeAll = FakeNative()
        run(
            fakeAll,
            """
            var all = text('x').findAll();
            log('count=' + all.length);
            """.trimIndent()
        )
        assertTrue(fakeAll.findAllCalled)
        assertEquals(listOf("count=2"), fakeAll.logs)
    }

    @Test
    fun gesturesAndAppActionsWork() {
        val fake = FakeNative()
        run(
            fake,
            """
            click(10, 20);
            swipe(1, 2, 3, 4, 300);
            sleep(50);
            toast('hi');
            log(launchApp('com.demo'));
            log(currentPackage());
            log(waitForPackage('com.demo', 1500));
            back();
            home();
            recents();
            dump();
            """.trimIndent()
        )

        assertEquals(Triple(10, 20, 50L), fake.tapArgs)
        assertEquals(listOf(1L, 2L, 3L, 4L, 300L), fake.swipeArgs)
        assertEquals(50L, fake.sleepMs)
        assertEquals(listOf("hi"), fake.toasts)
        assertEquals("com.demo", fake.launchPackage)
        assertEquals("com.demo", fake.waitPackage)
        assertEquals(1500L, fake.waitTimeout)
        assertTrue(fake.backCalled)
        assertTrue(fake.homeCalled)
        assertTrue(fake.recentsCalled)
        assertTrue(fake.dumpCalled)
        assertTrue(fake.logs.contains("true"))
        assertTrue(fake.logs.contains("com.demo"))
        assertTrue(fake.logs.contains("TREE"))
    }

    @Test
    fun exitStopsTheScript() {
        val fake = FakeNative()
        val error = assertThrows(RuntimeException::class.java) {
            run(fake, "exit(); log('should not run');")
        }
        assertFalse(fake.logs.contains("should not run"))
        assertTrue(error.message?.contains("exit") == true)
    }
}
