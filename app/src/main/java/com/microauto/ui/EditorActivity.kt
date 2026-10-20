package com.microauto.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.microauto.accessibility.AccessibilityUtils
import com.microauto.core.ScheduleTime
import com.microauto.databinding.ActivityEditorBinding
import com.microauto.schedule.SchedulePrefs
import com.microauto.schedule.ScriptScheduler
import com.microauto.script.RunLog
import com.microauto.script.RunState
import com.microauto.script.ScriptRepository
import com.microauto.script.ScriptService
import kotlinx.coroutines.launch
import java.util.Locale

class EditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SCRIPT_ID = "script_id"
    }

    private lateinit var binding: ActivityEditorBinding
    private lateinit var repository: ScriptRepository
    private lateinit var scriptId: String
    private var lastSavedContent = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scriptId = intent.getStringExtra(EXTRA_SCRIPT_ID) ?: run {
            finish()
            return
        }
        repository = ScriptRepository(this)
        val meta = repository.find(scriptId) ?: run {
            finish()
            return
        }
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = meta.name
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        lastSavedContent = repository.read(scriptId).orEmpty()
        binding.scriptEditor.setText(lastSavedContent)
        loadSchedule()
        bindActions()
        observeState()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                requestFinish()
            }
        })
    }

    override fun onSupportNavigateUp(): Boolean {
        requestFinish()
        return true
    }

    private fun bindActions() {
        binding.btnSave.setOnClickListener {
            saveScript()
            toast("已保存")
        }
        binding.btnRun.setOnClickListener {
            saveScript()
            if (!AccessibilityUtils.isEnabled(this)) {
                toast("请先开启无障碍服务")
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return@setOnClickListener
            }
            ScriptService.start(this, binding.scriptEditor.text.toString())
        }
        binding.btnStop.setOnClickListener {
            ScriptService.stop(this)
        }
        binding.btnDump.setOnClickListener {
            if (!AccessibilityUtils.isEnabled(this)) {
                toast("请先开启无障碍服务")
                return@setOnClickListener
            }
            ScriptService.start(
                this,
                "toast(\"5 秒内切到目标界面\"); sleep(5000); dump();"
            )
        }
        binding.btnSchedule.setOnClickListener {
            saveSchedule()
        }
        binding.scheduleSwitch.setOnCheckedChangeListener { _, checked ->
            if (!checked && SchedulePrefs.scriptId(this) == scriptId) {
                SchedulePrefs.setEnabled(this, false)
                ScriptScheduler.cancel(this)
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
        lastSavedContent = binding.scriptEditor.text.toString()
        repository.save(scriptId, lastSavedContent)
    }

    private fun requestFinish() {
        val current = binding.scriptEditor.text.toString()
        if (current == lastSavedContent) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("未保存的修改")
            .setMessage("是否保存当前脚本？")
            .setPositiveButton("保存并退出") { _, _ ->
                saveScript()
                finish()
            }
            .setNegativeButton("放弃修改") { _, _ -> finish() }
            .setNeutralButton("取消", null)
            .show()
    }

    private fun loadSchedule() {
        val scheduledHere = SchedulePrefs.scriptId(this) == scriptId
        binding.scheduleSwitch.isChecked = scheduledHere && SchedulePrefs.isEnabled(this)
        binding.scheduleTime.setText(
            if (scheduledHere) {
                String.format(
                    Locale.US,
                    "%02d:%02d",
                    SchedulePrefs.hour(this),
                    SchedulePrefs.minute(this)
                )
            } else {
                "08:00"
            }
        )
    }

    private fun saveSchedule() {
        val parsed = ScheduleTime.parseTime(binding.scheduleTime.text.toString())
        if (parsed == null) {
            toast("时间格式应为 HH:mm，例如 08:05")
            return
        }
        val enabled = binding.scheduleSwitch.isChecked
        val scheduledHere = SchedulePrefs.scriptId(this) == scriptId
        if (!enabled && !scheduledHere) {
            toast("当前脚本未启用定时")
            return
        }
        if (enabled) {
            SchedulePrefs.save(this, scriptId, true, parsed.first, parsed.second)
            ScriptScheduler.scheduleNext(this)
            toast("已设置每天 ${String.format(Locale.US, "%02d:%02d", parsed.first, parsed.second)}")
        } else {
            SchedulePrefs.setEnabled(this, false)
            SchedulePrefs.setScriptId(this, null)
            ScriptScheduler.cancel(this)
            toast("已关闭定时")
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
