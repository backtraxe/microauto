package com.microauto.script

import com.microauto.api.AutomationBridge
import com.microauto.api.UiObject
import com.microauto.api.UiSelector
import com.microauto.core.Bounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptRuntime
import org.mozilla.javascript.ScriptableObject
import java.io.File

class ScriptSandboxTest {

    class FakeSelector {
        fun text(value: String): FakeSelector = this
    }

    class FakeBounds {
        fun left(): Int = 10
        fun top(): Int = 20
    }

    class FakeUiObject {
        fun click(): Boolean = true
        fun text(): String = "签到"
        fun bounds(): FakeBounds = FakeBounds()
    }

    class FakeSecret {
        fun leak(): String = "secret"
        fun value(): Int = 42
    }

    class FakeNative {
        val logs = mutableListOf<String>()
        val selector = FakeSelector()

        fun selector(): FakeSelector = selector

        fun findOne(selector: FakeSelector, timeout: Long): FakeUiObject = FakeUiObject()

        fun findAll(selector: FakeSelector): Array<FakeUiObject> =
            arrayOf(FakeUiObject(), FakeUiObject())

        fun tap(x: Int, y: Int, duration: Long): Boolean = true

        fun secret(): FakeSecret = FakeSecret()

        fun boom(): Boolean = throw IllegalStateException("boom")

        fun log(message: String) {
            logs.add(message)
        }
    }

    private fun run(script: String): Pair<String, FakeNative> {
        val fake = FakeNative()
        val rhino = Context.enter()
        try {
            rhino.optimizationLevel = -1
            rhino.wrapFactory = SandboxWrapFactory(
                setOf(
                    FakeNative::class.java,
                    FakeSelector::class.java,
                    FakeUiObject::class.java,
                    FakeBounds::class.java
                )
            )
            val scope = ScriptScope.create(rhino)
            ScriptableObject.putProperty(
                scope,
                "native",
                rhino.wrapFactory.wrap(rhino, scope, fake, FakeNative::class.java)
            )
            rhino.evaluateString(scope, prelude(), "prelude.js", 1, null)
            val result = rhino.evaluateString(scope, script, "script.js", 1, null)
            return ScriptRuntime.toString(result) to fake
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
    fun javaGlobalsAreUnavailable() {
        val (result, _) = run(
            """
            [
                typeof Packages,
                typeof java,
                typeof getClass,
                typeof JavaAdapter,
                typeof importClass,
                typeof importPackage
            ].join("|");
            """.trimIndent()
        )

        assertEquals("undefined|undefined|undefined|undefined|undefined|undefined", result)
    }

    @Test
    fun javaPackagesAndReflectionMembersAreHidden() {
        val (result, _) = run(
            """
            var blocked = [];
            for (var key in native) blocked.push(key);
            [
              typeof Packages,
              typeof java,
              typeof native.getClass,
              typeof native.class,
              typeof native["get" + "Class"],
              blocked.join(",")
            ].join("|");
            """.trimIndent()
        )

        val parts = result.split("|")
        assertEquals("undefined", parts[0])
        assertEquals("undefined", parts[1])
        assertEquals("undefined", parts[2])
        assertEquals("undefined", parts[3])
        assertEquals("undefined", parts[4])
        val keys = parts[5].split(",").toSet()
        assertTrue("findOne" in keys)
        assertTrue("selector" in keys)
        for (name in listOf(
            "getClass",
            "class",
            "wait",
            "notify",
            "notifyAll",
            "equals",
            "hashCode",
            "toString",
            "clone",
            "finalize",
            "forName",
            "getClassLoader",
            "getDeclaredField",
            "getMethod",
            "newInstance",
            "setAccessible"
        )) {
            assertFalse("$name should be hidden", name in keys)
        }
    }

    @Test
    fun reflectionCallThroughNativeFails() {
        assertThrows(Exception::class.java) {
            run(
                """
                native.getClass().forName("java.lang.Runtime")
                    .getMethod("getRuntime").invoke(null).exec("id");
                """.trimIndent()
            )
        }
    }

    @Test
    fun normalSelectorAndUiObjectApiStillWorks() {
        val (result, fake) = run(
            """
            var node = text("签到").findOne(0);
            log(typeof node.getClass);
            log(typeof node.class);
            log(String(node.text()));
            log(node.click());
            var all = text("签到").findAll();
            log(all.length);
            log(typeof all.getClass);
            log(typeof all[0].getClass);
            log(String(all[0].text()));
            var keys = [];
            for (var k in all) keys.push(k);
            log(keys.join(","));
            """.trimIndent()
        )

        assertEquals(
            listOf(
                "undefined",
                "undefined",
                "签到",
                "true",
                "2",
                "undefined",
                "undefined",
                "签到",
                "0,1"
            ),
            fake.logs
        )
        assertEquals("undefined", result)
    }

    @Test
    fun boundsResultAlsoHidesReflection() {
        val (_, fake) = run(
            """
            var node = text("签到").findOne(0);
            var bounds = node.bounds();
            log(bounds.left());
            log(bounds.top());
            log(typeof bounds.getClass);
            log(typeof bounds.class);
            log(typeof bounds["get" + "Class"]);
            """.trimIndent()
        )

        assertEquals(
            listOf("10", "20", "undefined", "undefined", "undefined"),
            fake.logs
        )
    }

    @Test
    fun productionAllowlistOnlyCoversScriptApi() {
        val bridge = ScriptSandbox.allowedMembers(AutomationBridge::class.java)
        val selector = ScriptSandbox.allowedMembers(UiSelector::class.java)
        val uiObject = ScriptSandbox.allowedMembers(UiObject::class.java)
        val bounds = ScriptSandbox.allowedMembers(Bounds::class.java)

        assertTrue("findOne" in bridge)
        assertTrue("selector" in bridge)
        assertTrue("text" in selector)
        assertTrue("bounds" in uiObject)
        assertTrue("left" in bounds)
        for (members in listOf(bridge, selector, uiObject, bounds)) {
            assertFalse("getClass" in members)
            assertFalse("class" in members)
            assertFalse("wait" in members)
            assertFalse("equals" in members)
            assertFalse("toString" in members)
        }
        assertTrue(ScriptSandbox.allowedMembers(Any::class.java).isEmpty())
        assertTrue(ScriptSandbox.allowedMembers(File::class.java).isEmpty())
    }

    @Test
    fun objectPrototypeMembersAndMutationAreBlocked() {
        val (result, _) = run(
            """
            var r = [];
            r.push(typeof native.toString);
            r.push(typeof native.valueOf);
            r.push(typeof native.constructor);
            r.push("getClass" in native);
            r.push("class" in native);
            native.getClass = function () { return "pwned"; };
            r.push(typeof native.getClass);
            delete native.findOne;
            r.push(typeof native.findOne);
            r.join("|");
            """.trimIndent()
        )

        assertEquals(
            "undefined|undefined|undefined|false|false|undefined|function",
            result
        )
    }

    @Test
    fun caughtJavaExceptionIsSandboxed() {
        val (result, _) = run(
            """
            var r = [];
            try {
                native.boom();
                r.push("no-error");
            } catch (e) {
                var je = e.javaException;
                r.push(typeof e.getClass);
                r.push(typeof je);
                r.push(typeof je.getClass);
                r.push(typeof je.class);
                r.push(typeof je["get" + "Class"]);
            }
            r.join("|");
            """.trimIndent()
        )

        assertEquals("undefined|object|undefined|undefined|undefined", result)
    }

    @Test
    fun caughtJavaExceptionCannotReachRuntime() {
        assertThrows(Exception::class.java) {
            run(
                """
                try { native.boom(); } catch (e) {
                    e.javaException.getClass().forName("java.lang.Runtime")
                        .getMethod("getRuntime").invoke(null).exec("id");
                }
                """.trimIndent()
            )
        }
    }

    @Test
    fun arrayElementsAndPrototypeCallsStaySandboxed() {
        val (result, _) = run(
            """
            var all = native.findAll(native.selector());
            var mapped = Array.prototype.map.call(all, function (node) {
                return typeof node.getClass + "," + typeof node.class + "," +
                    typeof node["get" + "Class"];
            });
            [
                all.constructor === Array,
                typeof all.getClass,
                typeof all["getClass"],
                typeof all[0].getClass,
                all.length,
                mapped.join(";")
            ].join("|");
            """.trimIndent()
        )

        assertEquals(
            "true|undefined|undefined|undefined|2|" +
                "undefined,undefined,undefined;undefined,undefined,undefined",
            result
        )
    }

    @Test
    fun unknownJavaReturnValuesAreOpaque() {
        val (result, _) = run(
            """
            var secret = native.secret();
            [
                typeof secret,
                typeof secret.leak,
                typeof secret.value,
                typeof secret.getClass,
                typeof secret.class,
                typeof secret["leak"]
            ].join("|");
            """.trimIndent()
        )

        assertEquals("object|undefined|undefined|undefined|undefined|undefined", result)
    }

    @Test
    fun allowedNamesWorkThroughComputedAccess() {
        val (result, _) = run(
            """
            var sel = native["selector"]()["text"]("签到");
            var node = native["find" + "One"](sel, 0);
            [node.click(), String(node.text()), String(node.bounds().left())].join("|");
            """.trimIndent()
        )

        assertEquals("true|签到|10", result)
    }
}
