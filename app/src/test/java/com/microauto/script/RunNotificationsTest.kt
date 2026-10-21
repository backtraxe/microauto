package com.microauto.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RunNotificationsTest {

    @Test
    fun resultTextOnlyContainsScriptNameAndStatus() {
        RunLog.append("secret token from screen")
        try {
            val success = RunNotifications.resultText("签到脚本", true)
            val failure = RunNotifications.resultText("签到脚本", false)

            assertEquals("签到脚本 · 运行完成", success)
            assertEquals("签到脚本 · 运行失败", failure)
            assertFalse(success.contains("secret"))
            assertFalse(failure.contains("secret"))
            assertFalse(success.contains("secret token from screen"))
        } finally {
            RunLog.clear()
        }
    }
}
