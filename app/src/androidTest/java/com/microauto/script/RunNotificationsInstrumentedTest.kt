package com.microauto.script

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RunNotificationsInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager: NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                    context.packageName,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }
        manager.cancelAll()
        RunLog.clear()
    }

    @After
    fun tearDown() {
        manager.cancelAll()
        RunLog.clear()
    }

    @Test
    fun resultNotificationIsSecretLocalAndLogFree() {
        RunLog.append("secret-screen-text")
        RunNotifications.notifyResult(context, "签到脚本", true)

        val result = awaitNotification(1002)
        assertEquals(
            Notification.VISIBILITY_SECRET,
            NotificationCompat.getVisibility(result.notification)
        )
        assertTrue(NotificationCompat.getLocalOnly(result.notification))
        val text = result.notification.extras
            .getCharSequence(Notification.EXTRA_TEXT)
            ?.toString()
            .orEmpty()
        assertEquals("签到脚本 · 运行完成", text)
        assertFalse(text.contains("secret-screen-text"))
    }

    @Test
    fun skippedAndFailureNotificationsAreSecretAndLogFree() {
        RunLog.append("secret-screen-text")
        RunNotifications.notifySkipped(context, "签到脚本", "屏幕已锁定")

        val skipped = awaitNotification(1003, "签到脚本 · 屏幕已锁定")
        assertEquals(
            Notification.VISIBILITY_SECRET,
            NotificationCompat.getVisibility(skipped.notification)
        )
        assertTrue(NotificationCompat.getLocalOnly(skipped.notification))
        assertEquals("签到脚本 · 屏幕已锁定", notificationText(skipped))

        RunNotifications.notifyFailure(context, "签到脚本", "无法启动运行服务")
        val failure = awaitNotification(1003, "签到脚本 · 无法启动运行服务")
        assertEquals(
            Notification.VISIBILITY_SECRET,
            NotificationCompat.getVisibility(failure.notification)
        )
        assertEquals("签到脚本 · 无法启动运行服务", notificationText(failure))
        assertFalse(notificationText(failure).contains("secret-screen-text"))
    }

    private fun notificationText(notification: StatusBarNotification): String =
        notification.notification.extras
            .getCharSequence(Notification.EXTRA_TEXT)
            ?.toString()
            .orEmpty()

    private fun awaitNotification(
        id: Int,
        expectedText: String? = null
    ): StatusBarNotification {
        val deadline = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < deadline) {
            val notification = manager.activeNotifications.firstOrNull { it.id == id }
            if (notification != null &&
                (expectedText == null || notificationText(notification) == expectedText)
            ) {
                return notification
            }
            Thread.sleep(50)
        }
        error("未找到通知 $id")
    }
}
