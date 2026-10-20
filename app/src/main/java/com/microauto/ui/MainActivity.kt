package com.microauto.ui

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.microauto.R
import com.microauto.accessibility.AccessibilityUtils
import com.microauto.core.ScriptNaming
import com.microauto.databinding.ActivityMainBinding
import com.microauto.databinding.DialogRenameBinding
import com.microauto.schedule.SchedulePrefs
import com.microauto.schedule.ScriptScheduler
import com.microauto.script.ScriptMeta
import com.microauto.script.ScriptRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: ScriptRepository
    private lateinit var adapter: ScriptAdapter

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importScript(uri)
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) exportScript(uri)
    }

    private var pendingExportId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingExportId = savedInstanceState?.getString(KEY_PENDING_EXPORT)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdge.apply(this, binding.root)

        repository = ScriptRepository(this)
        adapter = ScriptAdapter(
            onClick = { openEditor(it.id) },
            onMenu = { meta, anchor -> showMenu(meta, anchor) },
            subtitle = { subtitle(it) },
            schedule = { scheduleText(it) }
        )
        binding.scriptList.layoutManager = LinearLayoutManager(this)
        binding.scriptList.adapter = adapter

        repository.ensureInitialized()
        setupUi()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun setupUi() {
        binding.toolbar.inflateMenu(R.menu.menu_main)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_import -> {
                    importLauncher.launch(arrayOf("*/*"))
                    true
                }

                R.id.action_accessibility -> {
                    openAccessibilitySettings()
                    true
                }

                R.id.action_battery -> {
                    openBatterySettings()
                    true
                }

                R.id.action_alarm -> {
                    openExactAlarmSettings()
                    true
                }

                else -> false
            }
        }
        binding.fabNew.setOnClickListener { newScript() }
        binding.btnEmptyNew.setOnClickListener { newScript() }
        binding.btnEmptyImport.setOnClickListener {
            importLauncher.launch(arrayOf("*/*"))
        }
        binding.chipAccessibility.setOnClickListener { openAccessibilitySettings() }
        binding.chipAlarm.setOnClickListener { openExactAlarmSettings() }
        binding.chipSchedule.setOnClickListener { openScheduledScript() }
    }

    private fun refresh() {
        val scripts = repository.list()
        adapter.submit(scripts)
        val empty = scripts.isEmpty()
        binding.emptyState.isVisible = empty
        binding.scriptList.isVisible = !empty
        updateStatus(scripts)
    }

    private fun subtitle(meta: ScriptMeta): String {
        val updated = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(meta.updatedAt))
        return "修改于 $updated"
    }

    private fun scheduleText(meta: ScriptMeta): String? {
        if (!SchedulePrefs.isEnabled(this) || SchedulePrefs.scriptId(this) != meta.id) {
            return null
        }
        return "每天 ${timeText()}"
    }

    private fun timeText(): String = String.format(
        Locale.US,
        "%02d:%02d",
        SchedulePrefs.hour(this),
        SchedulePrefs.minute(this)
    )

    private fun updateStatus(scripts: List<ScriptMeta>) {
        binding.chipAccessibility.text = if (AccessibilityUtils.isEnabled(this)) {
            "无障碍：已开启"
        } else {
            "无障碍：未开启"
        }
        binding.chipAlarm.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(AlarmManager::class.java)
            if (manager != null && manager.canScheduleExactAlarms()) {
                "精确闹钟：可用"
            } else {
                "精确闹钟：未授权"
            }
        } else {
            "精确闹钟：无需授权"
        }
        val scheduledId = SchedulePrefs.scriptId(this)
        val scheduled = scripts.firstOrNull { it.id == scheduledId }
        binding.chipSchedule.text = if (SchedulePrefs.isEnabled(this) && scheduled != null) {
            "定时 ${timeText()} · ${scheduled.name}"
        } else {
            "定时：未启用"
        }
    }

    private fun newScript() {
        val meta = repository.create("新脚本", ScriptRepository.DEFAULT_SCRIPT)
        refresh()
        openEditor(meta.id)
    }

    private fun openScheduledScript() {
        val id = SchedulePrefs.scriptId(this)
        val meta = id?.let { repository.find(it) }
        if (meta != null && SchedulePrefs.isEnabled(this)) {
            openEditor(meta.id)
        } else {
            toast("未启用定时")
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openBatterySettings() {
        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    private fun openExactAlarmSettings() {
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

    private fun openEditor(id: String) {
        startActivity(
            Intent(this, EditorActivity::class.java)
                .putExtra(EditorActivity.EXTRA_SCRIPT_ID, id)
        )
    }

    private fun showMenu(meta: ScriptMeta, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "重命名")
        popup.menu.add(0, 2, 1, "删除")
        popup.menu.add(0, 3, 2, "导出")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    showRenameDialog(meta)
                    true
                }

                2 -> {
                    confirmDelete(meta)
                    true
                }

                3 -> {
                    pendingExportId = meta.id
                    exportLauncher.launch("${meta.name}.js")
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    private fun showRenameDialog(meta: ScriptMeta) {
        val dialogBinding = DialogRenameBinding.inflate(layoutInflater)
        dialogBinding.renameInput.setText(meta.name)
        dialogBinding.renameInput.setSelection(meta.name.length)
        MaterialAlertDialogBuilder(this)
            .setTitle("重命名")
            .setView(dialogBinding.root)
            .setPositiveButton("确定") { _, _ ->
                if (repository.rename(meta.id, dialogBinding.renameInput.text.toString())) {
                    refresh()
                    toast("已重命名")
                } else {
                    toast("名称不能为空")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDelete(meta: ScriptMeta) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除脚本")
            .setMessage("确定删除「${meta.name}」？")
            .setPositiveButton("删除") { _, _ ->
                repository.delete(meta.id)
                if (SchedulePrefs.scriptId(this) == meta.id) {
                    SchedulePrefs.setEnabled(this, false)
                    SchedulePrefs.setScriptId(this, null)
                    ScriptScheduler.cancel(this)
                }
                refresh()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun importScript(uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { importFromUri(uri) }
            result.onSuccess { meta ->
                refresh()
                openEditor(meta.id)
            }.onFailure {
                toast("导入失败：${it.message}")
            }
        }
    }

    private fun importFromUri(uri: Uri): Result<ScriptMeta> = runCatching {
        val displayName = contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        val content = contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("无法读取文件")
        if (content.length > MAX_IMPORT_CHARS) error("文件超过 1 MB")
        if (content.contains('\u0000')) error("不是文本文件")
        val name = ScriptNaming.fromFileName(displayName ?: "导入脚本")
        repository.create(name, content)
    }

    private fun exportScript(uri: Uri) {
        val scriptId = pendingExportId ?: return
        pendingExportId = null
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val content = repository.read(scriptId) ?: error("脚本不存在")
                    contentResolver.openOutputStream(uri)
                        ?.bufferedWriter()
                        ?.use { it.write(content) }
                        ?: error("无法写入文件")
                }
            }
            result.onSuccess {
                toast("已导出")
            }.onFailure {
                toast("导出失败：${it.message}")
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PENDING_EXPORT, pendingExportId)
    }

    private companion object {
        const val MAX_IMPORT_CHARS = 1_000_000
        const val KEY_PENDING_EXPORT = "pending_export_id"
    }
}
