package com.microauto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.microauto.script.ScriptRepository
import java.io.File

object TestData {
    fun context(): Context = ApplicationProvider.getApplicationContext()

    fun reset() {
        val context = context()
        File(context.filesDir, "scripts").deleteRecursively()
        File(context.filesDir, "script.js").delete()
        context.getSharedPreferences("schedule", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    fun repository(): ScriptRepository = ScriptRepository(context())
}
