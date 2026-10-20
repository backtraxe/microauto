package com.microauto

import android.app.Application
import com.microauto.script.RunLog

class MicroAutoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RunLog.init(this)
    }
}
