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

    @Test
    fun resultBodyPrefersErrorSummary() {
        assertEquals(
            "ReferenceError: foo is not defined",
            RunNotifications.resultBody("签到脚本", false, "ReferenceError: foo is not defined")
        )
    }

    @Test
    fun resultBodyFallsBackToGenericStatusWithoutLogText() {
        RunLog.append("secret token from screen")
        try {
            val body = RunNotifications.resultBody("签到脚本", false, null)

            assertEquals("签到脚本 · 运行失败", body)
            assertFalse(body.contains("secret token from screen"))
        } finally {
            RunLog.clear()
        }
    }

    @Test
    fun blankErrorSummaryFallsBackToGenericStatus() {
        assertEquals(
            "签到脚本 · 运行完成",
            RunNotifications.resultBody("签到脚本", true, "   ")
        )
    }
}
