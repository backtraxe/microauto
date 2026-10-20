package com.microauto.accessibility

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager

object ScreenState {
    fun isInteractive(context: Context): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
        return powerManager.isInteractive
    }

    fun isLocked(context: Context): Boolean {
        val keyguardManager = context.getSystemService(KeyguardManager::class.java) ?: return false
        return keyguardManager.isKeyguardLocked
    }
}
