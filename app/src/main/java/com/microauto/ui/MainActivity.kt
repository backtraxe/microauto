package com.microauto.ui

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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
import com.microauto.script.FolderMeta
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
    private lateinit var adapter: LibraryAdapter
    private var currentFolderId: String? = null

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
        currentFolderId = savedInstanceState?.getString(KEY_FOLDER)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdge.apply(this, binding.root)

        repository = ScriptRepository(this)
        adapter = LibraryAdapter(
            onFolderClick = { openFolder(it) },
            onFolderMenu = { folder, anchor -> showFolderMenu(folder, anchor) },
            onScriptClick = { openEditor(it.id) },
            onScriptMenu = { meta, anchor -> showScriptMenu(meta, anchor) },
            subtitle = { subtitle(it) },
            schedule = { scheduleText(it) }
        )
        binding.scriptList.layoutManager = LinearLayoutManager(this)
        binding.scriptList.adapter = adapter

        repository.ensureInitialized()
        val savedFolder = currentFolderId
        if (savedFolder != null && repository.folder(savedFolder) == null) {
            currentFolderId = null
        }
        setupUi()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentFolderId != null) {
                    goUp()
                } else {
                    finish()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun setupUi() {
        binding.toolbar.inflateMenu(R.menu.menu_main)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_tutorial -> {
                    startActivity(Intent(this, TutorialActivity::class.java))
                    true
                }

                R.id.action_new_folder -> {
                    newFolder()
                    true
                }

                R.id.action_import -> {
                    importLauncher.launch(arrayOf("*/*"))
                    true
                }

                R.id.action_restore_builtins -> {
                    restoreBuiltins()
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
        binding.btnEmptyFolder.setOnClickListener { newFolder() }
        binding.chipAccessibility.setOnClickListener { openAccessibilitySettings() }
        binding.chipAlarm.setOnClickListener { openExactAlarmSettings() }
        binding.chipBattery.setOnClickListener { openBatterySettings() }
        binding.chipSchedule.setOnClickListener { openScheduledScript() }
    }

    private fun refresh() {
        val folderId = currentFolderId
        if (folderId != null && repository.folder(folderId) == null) {
            currentFolderId = null
        }
        val folders = if (currentFolderId == null) repository.folders() else emptyList()
        val scripts = repository.list(currentFolderId)
        val items = buildList<LibraryItem> {
            folders.forEach { add(LibraryItem.Folder(it, repository.count(it.id))) }
            scripts.forEach { add(LibraryItem.Script(it)) }
        }
        adapter.submit(items)
        val empty = items.isEmpty()
        binding.emptyState.isVisible = empty
        binding.scriptList.isVisible = !empty
        binding.emptyTitle.text = if (currentFolderId == null) {
            "还没有脚本"
        } else {
            "这个文件夹是空的"
        }
        binding.emptyHint.text = if (currentFolderId == null) {
            "新建一个脚本，或从文件导入 .js / .txt"
        } else {
            "新建一个脚本，或从文件导入到当前文件夹"
        }
        binding.btnEmptyFolder.isVisible = currentFolderId == null
        updateToolbar()
        updateStatus()
    }

    private fun updateToolbar() {
        val folder = currentFolderId?.let { repository.folder(it) }
        binding.toolbar.title = folder?.name ?: "脚本"
        if (folder != null) {
            binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            binding.toolbar.setNavigationContentDescription("返回")
            binding.toolbar.setNavigationOnClickListener { goUp() }
        } else {
            binding.toolbar.navigationIcon = null
            binding.toolbar.setNavigationContentDescription(null as CharSequence?)
            binding.toolbar.setNavigationOnClickListener(null)
        }
        binding.toolbar.menu.findItem(R.id.action_new_folder)?.isVisible = folder == null
    }

    private fun updateStatus() {
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
        val powerManager = getSystemService(PowerManager::class.java)
        val ignoringBattery = powerManager?.isIgnoringBatteryOptimizations(packageName) == true
        binding.chipBattery.text = if (ignoringBattery) {
            "省电：已允许"
        } else {
            "省电：未允许"
        }
        val scheduled = SchedulePrefs.scriptId(this)?.let { repository.find(it) }
        binding.chipSchedule.text = if (SchedulePrefs.isEnabled(this) && scheduled != null) {
            "定时 ${timeText()} · ${scheduled.name}"
        } else {
            "定时：未启用"
        }
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

    private fun newScript() {
        val meta = repository.create("新脚本", ScriptRepository.DEFAULT_SCRIPT, currentFolderId)
        refresh()
        openEditor(meta.id)
    }

    private fun newFolder() {
        if (currentFolderId != null) return
        showNameDialog("新建文件夹", "文件夹名称", "") { name ->
            val folder = repository.createFolder(name)
            if (folder == null) {
                toast("名称不能为空")
            } else {
                refresh()
                toast("已创建文件夹")
            }
        }
    }

    private fun restoreBuiltins() {
        val added = repository.ensureBuiltins()
        refresh()
        toast(if (added > 0) "已添加 $added 个内置示例" else "内置示例已存在")
    }

    private fun openFolder(folder: FolderMeta) {
        currentFolderId = folder.id
        refresh()
    }

    private fun goUp() {
        currentFolderId = null
        refresh()
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

    private fun showScriptMenu(meta: ScriptMeta, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "重命名")
        popup.menu.add(0, 2, 1, "移动到…")
        popup.menu.add(0, 3, 2, "导出")
        popup.menu.add(0, 4, 3, "删除")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    showRenameDialog(meta)
                    true
                }

                2 -> {
                    showMoveDialog(meta)
                    true
                }

                3 -> {
                    pendingExportId = meta.id
                    exportLauncher.launch("${meta.name}.js")
                    true
                }

                4 -> {
                    confirmDelete(meta)
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    private fun showFolderMenu(folder: FolderMeta, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "重命名")
        popup.menu.add(0, 2, 1, "删除")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    showRenameFolderDialog(folder)
                    true
                }

                2 -> {
                    confirmDeleteFolder(folder)
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    private fun showNameDialog(
        title: String,
        hint: String,
        initial: String,
        onConfirm: (String) -> Unit
    ) {
        val dialogBinding = DialogRenameBinding.inflate(layoutInflater)
        dialogBinding.renameLayout.hint = hint
        dialogBinding.renameInput.setText(initial)
        dialogBinding.renameInput.setSelection(initial.length)
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(dialogBinding.root)
            .setPositiveButton("确定") { _, _ ->
                onConfirm(dialogBinding.renameInput.text.toString())
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showRenameDialog(meta: ScriptMeta) {
        showNameDialog("重命名", "脚本名称", meta.name) { name ->
            if (repository.rename(meta.id, name)) {
                refresh()
                toast("已重命名")
            } else {
                toast("名称不能为空")
            }
        }
    }

    private fun showRenameFolderDialog(folder: FolderMeta) {
        showNameDialog("重命名文件夹", "文件夹名称", folder.name) { name ->
            if (repository.renameFolder(folder.id, name)) {
                refresh()
                toast("已重命名")
            } else {
                toast("名称不能为空")
            }
        }
    }

    private fun showMoveDialog(meta: ScriptMeta) {
        val destinations = buildList<Pair<String?, String>> {
            add(null to "根目录")
            repository.folders().forEach { add(it.id to it.name) }
        }
        val labels = destinations.map { it.second }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("移动到")
            .setItems(labels) { _, which ->
                val target = destinations[which].first
                if (repository.moveToFolder(meta.id, target)) {
                    refresh()
                    toast("已移动")
                }
            }
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

    private fun confirmDeleteFolder(folder: FolderMeta) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除文件夹")
            .setMessage("「${folder.name}」内的脚本会移回根目录。")
            .setPositiveButton("删除") { _, _ ->
                if (repository.deleteFolder(folder.id)) {
                    if (currentFolderId == folder.id) currentFolderId = null
                    refresh()
                    toast("已删除文件夹")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun importScript(uri: Uri) {
        val folderId = currentFolderId
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { importFromUri(uri, folderId) }
            result.onSuccess { meta ->
                refresh()
                openEditor(meta.id)
            }.onFailure {
                toast("导入失败：${it.message}")
            }
        }
    }

    private fun importFromUri(uri: Uri, folderId: String?): Result<ScriptMeta> = runCatching {
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
        repository.create(name, content, folderId)
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
        outState.putString(KEY_FOLDER, currentFolderId)
    }

    private companion object {
        const val MAX_IMPORT_CHARS = 1_000_000
        const val KEY_PENDING_EXPORT = "pending_export_id"
        const val KEY_FOLDER = "current_folder_id"
    }
}
