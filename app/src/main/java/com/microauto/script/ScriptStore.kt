package com.microauto.script

import android.content.Context
import java.io.File

class ScriptStore(context: Context) {
    private val file = File(context.filesDir, "script.js")

    fun read(): String = if (file.exists()) file.readText() else DEFAULT_SCRIPT

    fun write(script: String) {
        file.writeText(script)
    }

    companion object {
        val DEFAULT_SCRIPT = """
            // 示例：打印当前界面节点树
            toast("MicroAuto 已运行");
            log("前台包名: " + currentPackage());
            dump();
        """.trimIndent()
    }
}
