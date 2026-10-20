package com.microauto.accessibility

import android.content.Context
import android.provider.Settings

object AccessibilityUtils {
    fun isEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val expected = "${context.packageName}/${AutomationService::class.java.name}"
        return enabledServices.split(':').any {
            it.equals(expected, ignoreCase = true) ||
                it.endsWith(AutomationService::class.java.name)
        }
    }
}
