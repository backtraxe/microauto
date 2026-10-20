package com.microauto.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.microauto.script.RunLog
import com.microauto.script.ScriptRepository
import com.microauto.script.ScriptService

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!SchedulePrefs.isEnabled(context)) return
        val scriptId = SchedulePrefs.scriptId(context)
        if (scriptId == null) {
            RunLog.append("[错误] 定时任务没有绑定脚本")
            SchedulePrefs.setEnabled(context, false)
            return
        }
        val script = ScriptRepository(context).read(scriptId)
        if (script == null) {
            RunLog.append("[错误] 定时脚本不存在，已关闭定时")
            SchedulePrefs.setEnabled(context, false)
            SchedulePrefs.setScriptId(context, null)
            ScriptScheduler.cancel(context)
            return
        }
        runCatching {
            ScriptService.start(context, script)
        }.onFailure {
            RunLog.append("[错误] 定时任务启动失败: ${it.message}")
        }
        ScriptScheduler.scheduleNext(context)
    }
}
