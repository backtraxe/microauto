package com.microauto.script

import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject

object ScriptScope {
    fun create(context: Context): ScriptableObject = context.initSafeStandardObjects()
}
