package com.microauto.script

import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory

object ScriptContextFactory : ContextFactory() {
    override fun observeInstructionCount(cx: Context, instructionCount: Int) {
        ScriptExecutionGuard.check()
    }
}
