package com.microauto.script

import android.content.Context
import com.microauto.accessibility.AutomationService
import com.microauto.api.AutomationBridge
import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.ScriptableObject

object ScriptEngine {
    private const val PRELUDE_ASSET = "prelude.js"

    @Volatile
    private var cachedPrelude: String? = null

    fun execute(context: Context, service: AutomationService, script: String) {
        val rhino = RhinoContext.enter()
        try {
            rhino.optimizationLevel = -1
            rhino.languageVersion = RhinoContext.VERSION_1_8
            val scope = rhino.initStandardObjects()
            val bridge = AutomationBridge(service, context.applicationContext)
            ScriptableObject.putProperty(scope, "native", RhinoContext.javaToJS(bridge, scope))
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
