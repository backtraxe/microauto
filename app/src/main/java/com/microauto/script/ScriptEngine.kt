package com.microauto.script

import android.content.Context
import com.microauto.accessibility.AutomationService
import com.microauto.api.AutomationBridge
import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.ScriptableObject

object ScriptEngine {
    private const val PRELUDE_ASSET = "prelude.js"
    private const val OBSERVER_THRESHOLD = 10_000

    @Volatile
    private var cachedPrelude: String? = null

    init {
        runCatching {
            ContextFactory.initGlobal(ScriptContextFactory)
        }.onFailure {
            android.util.Log.e("MicroAuto", "无法安装 Rhino ContextFactory", it)
        }
    }

    fun execute(context: Context, service: AutomationService, script: String) {
        val rhino = RhinoContext.enter()
        try {
            rhino.optimizationLevel = -1
            rhino.languageVersion = RhinoContext.VERSION_1_8
            rhino.instructionObserverThreshold = OBSERVER_THRESHOLD
            ScriptSandbox.install(rhino)
            val scope = ScriptScope.create(rhino)
            val bridge = AutomationBridge(service, context.applicationContext)
            ScriptableObject.putProperty(
                scope,
                "native",
                rhino.wrapFactory.wrap(rhino, scope, bridge, AutomationBridge::class.java)
            )
            rhino.evaluateString(scope, prelude(context), "prelude.js", 1, null)
            rhino.evaluateString(scope, script, "script.js", 1, null)
        } finally {
            RhinoContext.exit()
        }
    }

    private fun prelude(context: Context): String {
        cachedPrelude?.let { return it }
        val loaded = context.assets.open(PRELUDE_ASSET).bufferedReader().use { it.readText() }
        cachedPrelude = loaded
        return loaded
    }
}
