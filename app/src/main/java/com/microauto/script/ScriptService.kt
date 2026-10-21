package com.microauto.script

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.microauto.core.ScriptLimits
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
        const val EXTRA_RUN_SOURCE = "run_source"
        const val EXTRA_RUN_TOKEN = "run_token"

        private const val NOTIFICATION_ID = 1001
        private const val NO_TOKEN = Long.MIN_VALUE

        fun start(
            context: Context,
            scriptId: String?,
            scriptName: String,
            script: String,
            source: RunSource = RunSource.MANUAL,
        ) {
            ScriptLimits.scriptError(script)?.let { throw IllegalArgumentException(it) }
            val intent = Intent(context, ScriptService::class.java).apply {
                action = ACTION_RUN
                putExtra(EXTRA_SCRIPT, script)
                putExtra(EXTRA_SCRIPT_ID, scriptId)
                putExtra(EXTRA_SCRIPT_NAME, scriptName)
                putExtra(EXTRA_RUN_SOURCE, source.name)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context, token: Long? = null) {
            val intent = Intent(context, ScriptService::class.java).setAction(ACTION_STOP)
                .putExtra(EXTRA_RUN_TOKEN, token ?: NO_TOKEN)
            context.startService(intent)
        }
    }

    private class RunSession(
        val owner: RunTokenRegistry.Token,
        val scriptId: String?,
        val scriptName: String,
        val source: RunSource,
        var job: Job? = null,
        var wakeLock: PowerManager.WakeLock? = null,
        var stoppedByUser: Boolean = false,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tokens = RunTokenRegistry()

    // All fields below are only touched on the main thread.
    private var currentRun: RunSession? = null
    private var latestStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        RunNotifications.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        when (intent?.action) {
            ACTION_STOP -> stopRun(
                startId,
                intent.getLongExtra(EXTRA_RUN_TOKEN, NO_TOKEN)
            )

            ACTION_RUN -> startRun(intent, startId)
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun startRun(intent: Intent, startId: Int) {
        val script = intent.getStringExtra(EXTRA_SCRIPT)
        if (script == null) {
            stopSelf(startId)
            return
        }
        val scriptId = intent.getStringExtra(EXTRA_SCRIPT_ID)
        val scriptName = intent.getStringExtra(EXTRA_SCRIPT_NAME) ?: "脚本"
        if (currentRun != null) {
            RunNotifications.notifyBusy(this, scriptName, "已有脚本在运行")
            // Do not stopSelf(startId) synchronously: that would stop the active
            // session. If the active session ends first, finishRun() consumes the
            // newest startId; this posted check is only a safety net.
            mainHandler.post {
                if (currentRun == null) stopSelf(startId)
            }
            return
        }
        if (ScriptRunner.isRunning()) {
            RunNotifications.notifyBusy(this, scriptName, "正在收尾，请稍后")
            stopSelf(startId)
            return
        }

        val source = intent.getStringExtra(EXTRA_RUN_SOURCE)
            ?.let { name -> runCatching { RunSource.valueOf(name) }.getOrNull() }
            ?: RunSource.MANUAL
        val session = RunSession(tokens.begin(startId), scriptId, scriptName, source)
        currentRun = session
        val startedAt = System.currentTimeMillis()
        RunState.start(scriptId, session.owner.id, scriptName, source, startedAt)
        RunLog.clear()
        RunLog.begin(scriptId, scriptName, source, startedAt)

        try {
            startForegroundCompat("正在运行：$scriptName", session.owner.id)
            session.wakeLock = acquireWakeLock()
        } catch (throwable: Throwable) {
            currentRun = null
            tokens.finish(session.owner)
            releaseWakeLock(session)
            val message = "启动运行服务失败：${throwable.message}"
            RunState.recordError(null, message, startedAt)
            RunState.complete(RunPhase.FAILED)
            RunLog.recordError(null, message, startedAt)
            RunLog.complete(RunPhase.FAILED)
            stopForeground(STOP_FOREGROUND_REMOVE)
            RunLog.append("[错误] $message")
            stopSelf(latestStartId)
            return
        }

        session.job = scope.launch {
            var success = false
            try {
                success = ScriptRunner.run(this@ScriptService, script)
            } finally {
                mainHandler.post { finishRun(session.owner, success) }
            }
        }
    }

    private fun finishRun(owner: RunTokenRegistry.Token, success: Boolean) {
        val session = currentRun ?: return
        if (session.owner != owner || !tokens.finish(owner)) return
        currentRun = null
        releaseWakeLock(session)
        val phase = when {
            session.stoppedByUser -> RunPhase.STOPPED
            success -> RunPhase.SUCCESS
            else -> RunPhase.FAILED
        }
        RunState.complete(phase)
        RunLog.complete(phase)
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (!session.stoppedByUser) {
            RunNotifications.notifyResult(
                context = this,
                scriptName = session.scriptName,
                success = success,
                scriptId = session.scriptId,
                errorMessage = RunLog.meta.value?.errorMessage,
            )
        }
        stopSelf(latestStartId)
    }

    private fun stopRun(startId: Int, token: Long) {
        val session = currentRun
        if (session == null) {
            stopSelf(startId)
            return
        }
        if (!tokens.isCurrentId(token) || session.owner.id != token) return
        session.stoppedByUser = true
        session.job?.cancel()
        ScriptRunner.cancel()
        finishRun(session.owner, success = false)
    }

    private fun startForegroundCompat(text: String, token: Long) {
        val notification = buildNotification(text, true, token)
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

    private fun buildNotification(text: String, ongoing: Boolean, token: Long): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, ScriptService::class.java)
            .setAction(ACTION_STOP)
            .putExtra(EXTRA_RUN_TOKEN, token)
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

    private fun acquireWakeLock(): PowerManager.WakeLock? {
        val powerManager = getSystemService(PowerManager::class.java) ?: return null
        return powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MicroAuto:script"
        ).apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock(session: RunSession) {
        runCatching { session.wakeLock?.release() }
        session.wakeLock = null
    }

    override fun onDestroy() {
        val session = currentRun
        if (session != null) {
            session.stoppedByUser = true
            session.job?.cancel()
            ScriptRunner.cancel()
            releaseWakeLock(session)
            RunState.stop()
            RunLog.complete(RunPhase.STOPPED)
        }
        currentRun = null
        tokens.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
