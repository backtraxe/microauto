package com.microauto.script

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.microauto.ui.EditorActivity
import com.microauto.ui.MainActivity

object RunNotifications {
    const val CHANNEL_ID = "script_run"
    private const val RESULT_NOTIFICATION_ID = 1002
    private const val ALERT_NOTIFICATION_ID = 1003
    private const val MAX_TEXT_CHARS = 200

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "脚本运行",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    fun notifyResult(
        context: Context,
        scriptName: String,
        success: Boolean,
        scriptId: String? = null,
        errorMessage: String? = null,
    ) {
        notify(
            context = context,
            id = RESULT_NOTIFICATION_ID,
            title = "「$scriptName」${if (success) "运行完成" else "运行失败"}",
            text = resultBody(scriptName, success, errorMessage),
            contentIntent = resultIntent(context, scriptId),
            requestCode = 1,
        )
    }

    fun resultText(scriptName: String, success: Boolean): String =
        "$scriptName · ${if (success) "运行完成" else "运行失败"}"

    fun resultBody(scriptName: String, success: Boolean, errorMessage: String?): String {
        val summary = errorMessage?.trim()?.takeIf { it.isNotEmpty() }
        return summary ?: resultText(scriptName, success)
    }

    fun notifySkipped(context: Context, scriptName: String, reason: String) {
        notify(
            context = context,
            id = ALERT_NOTIFICATION_ID,
            title = "定时任务已跳过",
            text = "$scriptName · $reason",
            contentIntent = Intent(context, MainActivity::class.java),
            requestCode = 0,
        )
    }

    fun notifyFailure(context: Context, scriptName: String, message: String) {
        notify(
            context = context,
            id = ALERT_NOTIFICATION_ID,
            title = "定时任务失败",
            text = "$scriptName · $message",
            contentIntent = Intent(context, MainActivity::class.java),
            requestCode = 0,
        )
    }

    fun notifyBusy(context: Context, scriptName: String, reason: String) {
        notify(
            context = context,
            id = ALERT_NOTIFICATION_ID,
            title = "脚本未启动",
            text = "$scriptName · $reason",
            contentIntent = Intent(context, MainActivity::class.java),
            requestCode = 0,
        )
    }

    fun notifyWaiting(context: Context, scriptName: String, reason: String) {
        notify(
            context = context,
            id = ALERT_NOTIFICATION_ID,
            title = "定时任务等待中",
            text = "$scriptName · $reason",
            contentIntent = Intent(context, MainActivity::class.java),
            requestCode = 0,
        )
    }

    private fun resultIntent(context: Context, scriptId: String?): Intent {
        val target = scriptId
            ?.let { id -> runCatching { ScriptRepository(context).find(id) }.getOrNull() }
            ?: return Intent(context, MainActivity::class.java)
        return Intent(context, EditorActivity::class.java).apply {
            putExtra(EditorActivity.EXTRA_SCRIPT_ID, target.id)
            putExtra(EditorActivity.EXTRA_OPEN_LOG, true)
        }
    }

    private fun notify(
        context: Context,
        id: Int,
        title: String,
        text: String,
        contentIntent: Intent,
        requestCode: Int,
    ) {
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = text.take(MAX_TEXT_CHARS)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("MicroAuto · $title")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setLocalOnly(true)
            .build()
        manager.notify(id, notification)
    }
}
