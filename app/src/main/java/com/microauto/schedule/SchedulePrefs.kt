package com.microauto.schedule

import android.content.Context
import android.content.SharedPreferences

object SchedulePrefs {
    private const val NAME = "schedule"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_HOUR = "hour"
    private const val KEY_MINUTE = "minute"
    private const val KEY_SCRIPT_ID = "script_id"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun hour(context: Context): Int =
        prefs(context).getInt(KEY_HOUR, 8)

    fun minute(context: Context): Int =
        prefs(context).getInt(KEY_MINUTE, 0)

    fun scriptId(context: Context): String? =
        prefs(context).getString(KEY_SCRIPT_ID, null)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun setScriptId(context: Context, scriptId: String?) {
        val editor = prefs(context).edit()
        if (scriptId == null) editor.remove(KEY_SCRIPT_ID) else editor.putString(KEY_SCRIPT_ID, scriptId)
        editor.apply()
    }

    fun save(context: Context, scriptId: String, enabled: Boolean, hour: Int, minute: Int) {
        prefs(context).edit()
            .putString(KEY_SCRIPT_ID, scriptId)
            .putBoolean(KEY_ENABLED, enabled)
            .putInt(KEY_HOUR, hour)
            .putInt(KEY_MINUTE, minute)
            .apply()
    }
}
