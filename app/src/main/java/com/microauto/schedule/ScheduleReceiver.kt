package com.microauto.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.microauto.script.RunLog
import com.microauto.script.ScriptService
import com.microauto.script.ScriptStore

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!SchedulePrefs.isEnabled(context)) return
        val script = ScriptStore(context).read()
        runCatching {
            ScriptService.start(context, script)
        }.onFailure {
            RunLog.append("[错误] 定时任务启动失败: ${it.message}")
        }
        ScriptScheduler.scheduleNext(context)
    }
}
