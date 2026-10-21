package com.microauto.script

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.microauto.core.ScriptLimits
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScriptServiceStartGuardTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun oversizedScriptIsRejectedBeforeServiceStart() {
        val script = "a".repeat(ScriptLimits.MAX_SCRIPT_BYTES + 1)

        val error = assertThrows(IllegalArgumentException::class.java) {
            ScriptService.start(context, "script-id", "测试脚本", script)
        }

        assertTrue(error.message?.contains("128 KB") == true)
    }
}
