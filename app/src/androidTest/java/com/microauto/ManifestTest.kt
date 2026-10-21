package com.microauto

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.microauto.accessibility.AutomationService
import com.microauto.schedule.BootReceiver
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManifestTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val packageManager = context.packageManager

    @Test
    fun bootReceiverIsUnexportedAndKeepsSystemFilters() {
        val info = packageManager.getReceiverInfo(
            ComponentName(context, BootReceiver::class.java),
            0
        )
        assertFalse(info.exported)

        for (action in listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )) {
            val receivers = packageManager.queryBroadcastReceivers(
                Intent(action).setPackage(context.packageName),
                0
            )
            assertTrue(
                "$action 未解析到 BootReceiver",
                receivers.any { it.activityInfo.name == BootReceiver::class.java.name }
            )
        }
    }

    @Test
    fun automationServiceRemainsExportedForSystemBinding() {
        val info = packageManager.getServiceInfo(
            ComponentName(context, AutomationService::class.java),
            0
        )
        assertTrue(info.exported)
    }
}
