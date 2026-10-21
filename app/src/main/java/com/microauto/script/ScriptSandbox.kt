package com.microauto.script

import com.microauto.api.AutomationBridge
import com.microauto.api.UiObject
import com.microauto.api.UiSelector
import com.microauto.core.Bounds
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeJavaArray
import org.mozilla.javascript.NativeJavaObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.WrapFactory
import java.lang.reflect.Array as JavaArray
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * Wraps Java values exposed to scripts into objects whose members are restricted
 * to the public API of known script classes. Object/reflection members never
 * become reachable, so `native.getClass()` and similar chains return NOT_FOUND.
 */
object ScriptSandbox {
    private val allowedClasses = setOf(
        AutomationBridge::class.java,
        UiSelector::class.java,
        UiObject::class.java,
        Bounds::class.java,
        String::class.java,
        Boolean::class.java,
        Byte::class.java,
        Short::class.java,
        Int::class.javaObjectType,
        Long::class.javaObjectType,
        Float::class.javaObjectType,
        Double::class.javaObjectType,
        Char::class.javaObjectType
    )

    private val blockedNames = setOf(
        "getClass",
        "class",
        "forName",
        "getClassLoader",
        "getDeclaredField",
        "getDeclaredFields",
        "getDeclaredMethod",
        "getDeclaredMethods",
        "getDeclaredConstructor",
        "getDeclaredConstructors",
        "getField",
        "getFields",
        "getMethod",
        "getMethods",
        "getConstructor",
        "getConstructors",
        "setAccessible",
        "trySetAccessible",
        "newInstance",
        "wait",
        "notify",
        "notifyAll",
        "clone",
        "finalize",
        "equals",
        "hashCode",
        "toString"
    )

    private data class MemberKey(
        val type: Class<*>,
        val extraClasses: Set<Class<*>>
    )

    private val cache = ConcurrentHashMap<MemberKey, Set<String>>()

    val production = SandboxWrapFactory()

    fun install(context: Context) {
        context.wrapFactory = production
    }

    fun allowedMembers(type: Class<*>, extraClasses: Set<Class<*>> = emptySet()): Set<String> {
        if (type !in allowedClasses && type !in extraClasses) return emptySet()
        return cache.getOrPut(MemberKey(type, extraClasses)) {
            val names = LinkedHashSet<String>()
            var current: Class<*>? = type
            while (current != null && current != Any::class.java) {
                current.declaredMethods
                    .filter {
                        Modifier.isPublic(it.modifiers) &&
                            !Modifier.isStatic(it.modifiers) &&
                            '$' !in it.name
                    }
                    .forEach { method ->
                        names += method.name
                        beanPropertyName(method.name)?.let { names += it }
                    }
                current = current.superclass
            }
            names.removeAll(blockedNames)
            names
        }
    }

    private fun beanPropertyName(methodName: String): String? {
        if (methodName.length <= 3) return null
        if (!methodName.startsWith("get") && !methodName.startsWith("is")) return null
        val first = methodName[3]
        if (!first.isUpperCase()) return null
        return first.lowercaseChar() + methodName.substring(4)
    }
}

class SandboxWrapFactory(
    private val extraClasses: Set<Class<*>> = emptySet()
) : WrapFactory() {

    override fun wrap(
        cx: Context,
        scope: Scriptable,
        obj: Any?,
        staticType: Class<*>?
    ): Any? {
        if (obj == null || obj === Undefined.instance || obj is Scriptable) return obj
        if (staticType != null && staticType.isPrimitive) {
            return super.wrap(cx, scope, obj, staticType)
        }
        val type = obj.javaClass
        if (type.isArray) return SandboxNativeJavaArray(scope, obj)
        return SandboxNativeJavaObject(
            scope,
            obj,
            staticType,
            ScriptSandbox.allowedMembers(type, extraClasses)
        )
    }
}

private class SandboxNativeJavaObject(
    scope: Scriptable,
    javaObject: Any,
    staticType: Class<*>?,
    private val allowed: Set<String>
) : NativeJavaObject(scope, javaObject, staticType) {

    override fun get(name: String, start: Scriptable): Any =
        if (name in allowed) super.get(name, start) else NOT_FOUND

    override fun has(name: String, start: Scriptable): Boolean =
        name in allowed && super.has(name, start)

    override fun getIds(): Array<Any> =
        super.getIds().filter { it is String && it in allowed }.toTypedArray()

    override fun put(name: String, start: Scriptable, value: Any?) = Unit

    override fun delete(name: String) = Unit

    override fun getDefaultValue(typeHint: Class<*>?): Any {
        val value = unwrap()
        if (value is String || value is Number || value is Boolean || value is Char) {
            return value
        }
        return when (typeHint) {
            null, String::class.java, Any::class.java -> ""
            else -> super.getDefaultValue(typeHint)
        }
    }
}

private class SandboxNativeJavaArray(
    scope: Scriptable,
    javaObject: Any
) : NativeJavaArray(scope, javaObject) {

    override fun get(name: String, start: Scriptable): Any =
        if (name == "length") super.get(name, start) else NOT_FOUND

    override fun has(name: String, start: Scriptable): Boolean = name == "length"

    override fun getIds(): Array<Any> {
        val size = JavaArray.getLength(unwrap())
        return Array(size) { it }
    }

    override fun put(name: String, start: Scriptable, value: Any?) = Unit

    override fun delete(name: String) = Unit

    override fun getDefaultValue(typeHint: Class<*>?): Any {
        val value = unwrap()
        if (value is String || value is Number || value is Boolean || value is Char) {
            return value
        }
        return when (typeHint) {
            null, String::class.java, Any::class.java -> ""
            else -> super.getDefaultValue(typeHint)
        }
    }
}
