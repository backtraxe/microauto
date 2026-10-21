package com.microauto.script

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.microauto.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScriptService : Service() {

    companion object {
        const val ACTION_RUN = "com.microauto.action.RUN"
        const val ACTION_STOP = "com.microauto.action.STOP"
        const val EXTRA_SCRIPT = "script"
        const val EXTRA_SCRIPT_ID = "script_id"
        const val EXTRA_SCRIPT_NAME = "script_name"

        private const val NOTIFICATION_ID = 1001

        fun start(context: Context, scriptId: String?, scriptName: String, script: String) {
            val intent = Intent(context, ScriptService::class.java).apply {
                action = ACTION_RUN
                putExtra(EXTRA_SCRIPT, script)
                putExtra(EXTRA_SCRIPT_ID, scriptId)
                putExtra(EXTRA_SCRIPT_NAME, scriptName)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScriptService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var runningName = "脚本"

    @Volatile
    private var active = false

    @Volatile
    private var stoppedByUser = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        RunNotifications.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRun()
                return START_NOT_STICKY
            }

            ACTION_RUN -> {
                val script = intent.getStringExtra(EXTRA_SCRIPT)
                if (script == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (active || ScriptRunner.isRunning()) {
                    RunLog.append("[跳过] 已有脚本在运行")
                    return START_NOT_STICKY
                }
                val scriptId = intent.getStringExtra(EXTRA_SCRIPT_ID)
                runningName = intent.getStringExtra(EXTRA_SCRIPT_NAME) ?: "脚本"
                stoppedByUser = false
                active = true
                RunState.start(scriptId)
                startForegroundCompat("正在运行：$runningName")
                RunLog.clear()
                acquireWakeLock()
                job = scope.launch {
                    var success = false
                    try {
                        success = ScriptRunner.run(this@ScriptService, script)
                    } finally {
                        active = false
                        finishRun(success)
                    }
                }
            }

            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun finishRun(success: Boolean) {
        releaseWakeLock()
        RunState.finish()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (!stoppedByUser) {
            RunNotifications.notifyResult(this, runningName, success)
        }
        stopSelf()
    }

    private fun stopRun() {
        stoppedByUser = true
        active = false
        job?.cancel()
        ScriptRunner.cancel()
        releaseWakeLock()
        RunState.finish()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundCompat(text: String) {
        val notification = buildNotification(text, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(text: String, ongoing: Boolean): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, ScriptService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, RunNotifications.CHANNEL_ID)
            .setContentTitle("MicroAuto")
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(ongoing)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setLocalOnly(true)
            .addAction(android.R.drawable.ic_media_pause, "停止", stopPending)
            .build()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MicroAuto:script"
        ).apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        active = false
        job?.cancel()
        ScriptRunner.cancel()
        releaseWakeLock()
        RunState.finish()
        super.onDestroy()
    }
}
