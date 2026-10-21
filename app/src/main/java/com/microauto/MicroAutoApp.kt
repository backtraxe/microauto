package com.microauto

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.microauto.script.RunLog
import com.microauto.script.RunNotifications
import com.microauto.script.ScriptBackup
import com.microauto.script.ScriptRepository

class MicroAutoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        RunLog.init(this)
        RunNotifications.ensureChannel(this)
        ScriptBackup.recoverInterruptedRestore(this)
        ScriptRepository(this).ensureInitialized()
    }
}
