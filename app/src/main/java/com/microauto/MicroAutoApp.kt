package com.microauto

import android.app.Application
import com.microauto.schedule.SchedulePrefs
import com.microauto.schedule.ScriptScheduler
import com.microauto.script.RunLog
import com.microauto.script.ScriptRepository

class MicroAutoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RunLog.init(this)
        val scripts = ScriptRepository(this).ensureInitialized()
        if (SchedulePrefs.isEnabled(this) &&
            SchedulePrefs.scriptId(this) == null &&
            scripts.isNotEmpty()
        ) {
            SchedulePrefs.setScriptId(this, scripts.first().id)
            ScriptScheduler.scheduleNext(this)
        }
    }
}
