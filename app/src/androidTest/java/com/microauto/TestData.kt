package com.microauto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.microauto.script.RepositoryHealth
import com.microauto.script.RunLog
import com.microauto.script.RunState
import com.microauto.script.ScriptRepository
import java.io.File

object TestData {
    fun context(): Context = ApplicationProvider.getApplicationContext()

    fun reset() {
        val context = context()
        File(context.filesDir, "scripts").deleteRecursively()
        File(context.filesDir, "script.js").delete()
        File(context.filesDir, "latest.log").delete()
        context.getSharedPreferences("schedule", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences("run_log", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        RunLog.clear()
        RunState.finish()
        RepositoryHealth.clear()
        ScriptRepository.clearCache()
    }

    fun repository(): ScriptRepository = ScriptRepository(context())
}
