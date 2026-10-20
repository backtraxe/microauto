package com.microauto

import android.app.Application
import com.microauto.script.RunLog
import com.microauto.script.RunNotifications
import com.microauto.script.ScriptRepository

class MicroAutoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RunLog.init(this)
        RunNotifications.ensureChannel(this)
        ScriptRepository(this).ensureInitialized()
    }
}
