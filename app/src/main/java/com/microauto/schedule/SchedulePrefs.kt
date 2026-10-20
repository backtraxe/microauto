package com.microauto.schedule

import android.content.Context
import android.content.SharedPreferences

object SchedulePrefs {
    private const val NAME = "schedule"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_HOUR = "hour"
    private const val KEY_MINUTE = "minute"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun hour(context: Context): Int =
        prefs(context).getInt(KEY_HOUR, 8)

    fun minute(context: Context): Int =
        prefs(context).getInt(KEY_MINUTE, 0)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun save(context: Context, enabled: Boolean, hour: Int, minute: Int) {
        prefs(context).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putInt(KEY_HOUR, hour)
            .putInt(KEY_MINUTE, minute)
            .apply()
    }
}
