package com.microauto.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.microauto.accessibility.AutomationService
import com.microauto.core.ScheduleTime
import com.microauto.databinding.ActivityMainBinding
import com.microauto.schedule.SchedulePrefs
import com.microauto.schedule.ScriptScheduler
import com.microauto.script.RunLog
import com.microauto.script.RunState
import com.microauto.script.ScriptService
import com.microauto.script.ScriptStore
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var store: ScriptStore

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        store = ScriptStore(this)

        binding.scriptEditor.setText(store.read())
        loadSchedule()
        bindActions()
        observeState()
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun bindActions() {
        binding.btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnBattery.setOnClickListener {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        binding.btnExactAlarm.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:$packageName")
                    )
                )
            } else {
                toast("当前系统无需授权精确闹钟")
            }
        }
        binding.btnSave.setOnClickListener {
            saveScript()
            toast("已保存")
        }
        binding.btnRun.setOnClickListener {
            saveScript()
            if (!isAccessibilityEnabled()) {
                toast("请先开启无障碍服务")
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return@setOnClickListener
            }
            ScriptService.start(this, store.read())
        }
        binding.btnStop.setOnClickListener {
            ScriptService.stop(this)
        }
        binding.btnDump.setOnClickListener {
            if (!isAccessibilityEnabled()) {
                toast("请先开启无障碍服务")
                return@setOnClickListener
            }
            ScriptService.start(this, "dump();")
        }
        binding.btnSchedule.setOnClickListener {
            saveSchedule()
        }
        binding.scheduleSwitch.setOnCheckedChangeListener { _, checked ->
            if (!checked) {
                SchedulePrefs.setEnabled(this, false)
                ScriptScheduler.cancel(this)
                updateStatus()
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    RunLog.text.collect { text ->
                        binding.logText.text = text
                        binding.logScroll.post {
                            binding.logScroll.fullScroll(View.FOCUS_DOWN)
                        }
                    }
                }
                launch {
                    RunState.running.collect { running ->
                        binding.btnRun.isEnabled = !running
                        binding.btnStop.isEnabled = running
                        binding.btnDump.isEnabled = !running
                    }
                }
            }
        }
    }

    private fun saveScript() {
        store.write(binding.scriptEditor.text.toString())
    }

    private fun loadSchedule() {
        binding.scheduleTime.setText(
            String.format(
                Locale.US,
                "%02d:%02d",
                SchedulePrefs.hour(this),
                SchedulePrefs.minute(this)
            )
        )
        binding.scheduleSwitch.isChecked = SchedulePrefs.isEnabled(this)
    }

    private fun saveSchedule() {
        val parsed = ScheduleTime.parseTime(binding.scheduleTime.text.toString())
        if (parsed == null) {
            toast("时间格式应为 HH:mm，例如 08:05")
            return
        }
        val enabled = binding.scheduleSwitch.isChecked
        SchedulePrefs.save(this, enabled, parsed.first, parsed.second)
        if (enabled) {
            ScriptScheduler.scheduleNext(this)
            toast("已设置每天 ${String.format(Locale.US, "%02d:%02d", parsed.first, parsed.second)}")
        } else {
            ScriptScheduler.cancel(this)
            toast("已关闭定时")
        }
        updateStatus()
    }

    private fun updateStatus() {
        val accessibility = if (isAccessibilityEnabled()) "无障碍：已开启" else "无障碍：未开启"
        val exactAlarm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(android.app.AlarmManager::class.java)
            if (manager != null && manager.canScheduleExactAlarms()) {
                "精确闹钟：可用"
            } else {
                "精确闹钟：未授权（将使用非精确）"
            }
        } else {
            "精确闹钟：系统无需授权"
        }
        val schedule = if (SchedulePrefs.isEnabled(this)) {
            "定时：每天 ${
                String.format(
                    Locale.US,
                    "%02d:%02d",
                    SchedulePrefs.hour(this),
                    SchedulePrefs.minute(this)
                )
            }"
        } else {
            "定时：未启用"
        }
        binding.statusText.text = "$accessibility\n$exactAlarm\n$schedule"
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val expected = "$packageName/${AutomationService::class.java.name}"
        return enabledServices.split(':').any {
            it.equals(expected, ignoreCase = true) ||
                it.endsWith(AutomationService::class.java.name)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
