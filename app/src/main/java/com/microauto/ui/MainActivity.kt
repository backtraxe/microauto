package com.microauto.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.microauto.R
import com.microauto.accessibility.AccessibilityUtils
import com.microauto.core.DateFormats
import com.microauto.core.ScriptNaming
import com.microauto.databinding.ActivityMainBinding
import com.microauto.databinding.DialogRenameBinding
import com.microauto.schedule.SchedulePrefs
import com.microauto.schedule.ScriptScheduler
import com.microauto.script.FolderMeta
import com.microauto.script.RepositoryHealth
import com.microauto.script.RunState
import com.microauto.script.ScriptBackup
import com.microauto.script.ScriptMeta
import com.microauto.script.ScriptRepository
import com.microauto.script.ScriptService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: ScriptRepository
    private lateinit var adapter: LibraryAdapter
    private var currentFolderId: String? = null
    private val searchHandler = Handler(Looper.getMainLooper())
    private val searchRefresh = Runnable { refresh() }

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

    private val backupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) backupAll(uri)
    }

    private val restoreLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) confirmRestore(uri)
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingExportId = savedInstanceState?.getString(KEY_PENDING_EXPORT)
        currentFolderId = savedInstanceState?.getString(KEY_FOLDER)
        val savedTab = savedInstanceState?.getInt(KEY_TAB, R.id.nav_scripts)
            ?.takeIf { it != R.id.nav_create }
            ?: R.id.nav_scripts
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdge.apply(this, binding.root)
        // BottomNavigationView 会自行叠加系统栏与 IME 内边距，这里已由 EdgeToEdge 统一处理。
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav) { _, insets -> insets }

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
        showRepositoryHealth()
        val savedFolder = currentFolderId
        if (savedFolder != null && repository.folder(savedFolder) == null) {
            currentFolderId = null
        }
        setupUi()
        binding.bottomNav.selectedItemId = savedTab
        if (savedTab == R.id.nav_tutorial) {
            showTutorialTab()
        } else {
            showScriptsTab()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.bottomNav.selectedItemId == R.id.nav_tutorial -> {
                        binding.bottomNav.selectedItemId = R.id.nav_scripts
                    }

                    currentFolderId != null -> goUp()

                    else -> finish()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun showRepositoryHealth() {
        val message = RepositoryHealth.message.value ?: return
        RepositoryHealth.clear()
        MaterialAlertDialogBuilder(this)
            .setTitle("本地数据已恢复")
            .setMessage(message)
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun setupUi() {
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_accessibility -> {
                    openAccessibilitySettings()
                    true
                }

                R.id.action_alarm -> {
                    openExactAlarmSettings()
                    true
                }

                R.id.action_battery -> {
                    openBatterySettings()
                    true
                }

                R.id.action_schedule -> {
                    openScheduledScript()
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

                R.id.action_backup -> {
                    backupLauncher.launch(BACKUP_FILE_NAME)
                    true
                }

                R.id.action_restore -> {
                    restoreLauncher.launch(arrayOf("*/*"))
                    true
                }

                R.id.action_restore_builtins -> {
                    restoreBuiltins()
                    true
                }

                else -> false
            }
        }
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_scripts -> {
                    showScriptsTab()
                    true
                }

                R.id.nav_create -> {
                    val anchor = binding.bottomNav.findViewById<View>(R.id.nav_create)
                        ?: binding.bottomNav
                    showCreateMenu(anchor)
                    false
                }

                R.id.nav_tutorial -> {
                    showTutorialTab()
                    true
                }

                else -> false
            }
        }
        binding.searchInput.doAfterTextChanged {
            searchHandler.removeCallbacks(searchRefresh)
            searchHandler.postDelayed(searchRefresh, SEARCH_DEBOUNCE_MS)
        }
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                true
            } else {
                false
            }
        }
        binding.tutorialContent.btnRestoreBuiltins.setOnClickListener { restoreBuiltins() }
        binding.btnEmptyNew.setOnClickListener { newScript() }
        binding.btnEmptyImport.setOnClickListener {
            importLauncher.launch(arrayOf("*/*"))
        }
        binding.btnEmptyFolder.setOnClickListener { newFolder() }
    }

    private fun showScriptsTab() {
        showPage(binding.scriptsPage, true)
        showPage(binding.tutorialPage, false)
        binding.toolbar.menu.clear()
        binding.toolbar.inflateMenu(R.menu.menu_main)
        updateToolbar()
        refresh()
    }

    private fun showTutorialTab() {
        showPage(binding.scriptsPage, false)
        showPage(binding.tutorialPage, true)
        binding.toolbar.menu.clear()
        binding.toolbar.title = "使用教程"
        binding.toolbar.navigationIcon = null
        binding.toolbar.setNavigationOnClickListener(null)
    }

    private fun showPage(page: View, visible: Boolean) {
        page.animate().cancel()
        if (visible) {
            page.alpha = 0f
            page.isVisible = true
            page.animate()
                .alpha(1f)
                .setDuration(160)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            page.isVisible = false
            page.alpha = 1f
        }
    }

    private fun refresh() {
        val query = binding.searchInput.text?.toString()?.trim().orEmpty()
        val searching = query.isNotEmpty()
        val folderId = currentFolderId
        if (!searching && folderId != null && repository.folder(folderId) == null) {
            currentFolderId = null
        }
        val folders = if (!searching && currentFolderId == null) {
            repository.folders()
        } else {
            emptyList()
        }
        val scripts = if (searching) {
            repository.listAll().filter { it.name.contains(query, ignoreCase = true) }
        } else {
            repository.list(currentFolderId)
        }
        val items = buildList<LibraryItem> {
            folders.forEach { add(LibraryItem.Folder(it, repository.count(it.id))) }
            scripts.forEach { add(LibraryItem.Script(it)) }
        }
        adapter.submit(items)
        val empty = items.isEmpty()
        binding.emptyState.isVisible = empty
        binding.scriptList.isVisible = !empty
        binding.emptyTitle.text = when {
            searching -> "没有找到匹配的脚本"
            currentFolderId != null -> "这个文件夹是空的"
            else -> "还没有脚本"
        }
        binding.emptyHint.text = when {
            searching -> "换个关键词试试"
            currentFolderId != null -> "新建一个脚本，或从文件导入到当前文件夹"
            else -> "新建一个脚本，或从文件导入 .js / .txt"
        }
        binding.btnEmptyFolder.isVisible = !searching && currentFolderId == null
        updateToolbar()
        updateStatus()
    }

    private fun updateToolbar() {
        if (binding.bottomNav.selectedItemId == R.id.nav_tutorial) return
        val query = binding.searchInput.text?.toString()?.trim().orEmpty()
        val folder = currentFolderId?.let { repository.folder(it) }
        binding.toolbar.title = when {
            query.isNotEmpty() -> "搜索结果"
            folder != null -> folder.name
            else -> "脚本"
        }
        if (folder != null && query.isEmpty()) {
            binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            binding.toolbar.setNavigationContentDescription("返回")
            binding.toolbar.setNavigationOnClickListener { goUp() }
        } else {
            binding.toolbar.navigationIcon = null
            binding.toolbar.setNavigationContentDescription(null as CharSequence?)
            binding.toolbar.setNavigationOnClickListener(null)
        }
        binding.toolbar.menu.findItem(R.id.action_new_folder)?.isVisible =
            folder == null && query.isEmpty()
    }

    private fun updateStatus() {
        val menu = binding.toolbar.menu
        menu.findItem(R.id.action_accessibility)?.title = if (AccessibilityUtils.isEnabled(this)) {
            "无障碍：已开启"
        } else {
            "无障碍：未开启"
        }
        menu.findItem(R.id.action_alarm)?.title = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
        menu.findItem(R.id.action_battery)?.title = if (ignoringBattery) {
            "省电：已允许"
        } else {
            "省电：未允许"
        }
        val schedules = SchedulePrefs.enabled(this)
        menu.findItem(R.id.action_schedule)?.title = when {
            schedules.isEmpty() -> "定时：未启用"
            schedules.size == 1 -> {
                val schedule = schedules.first()
                val name = repository.find(schedule.scriptId)?.name ?: "脚本"
                "定时 ${timeText(schedule.hour, schedule.minute)} · $name"
            }

            else -> "定时：${schedules.size} 个脚本"
        }
    }

    private fun subtitle(meta: ScriptMeta): String {
        val updated = DateFormats.format(meta.updatedAt)
        val folder = meta.folderId?.let { repository.folderName(it) }
        return if (folder != null) {
            "在 $folder · 修改于 $updated"
        } else {
            "修改于 $updated"
        }
    }

    private fun scheduleText(meta: ScriptMeta): String? {
        val schedule = SchedulePrefs.scheduleFor(this, meta.id)?.takeIf { it.enabled }
            ?: return null
        return "每天 ${timeText(schedule.hour, schedule.minute)}"
    }

    private fun timeText(hour: Int, minute: Int): String = String.format(
        Locale.US,
        "%02d:%02d",
        hour,
        minute
    )

    private fun showCreateMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "新建脚本")
        if (currentFolderId == null) {
            popup.menu.add(0, 2, 1, "新建文件夹")
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    ensureScriptsTab()
                    newScript()
                    true
                }

                2 -> {
                    ensureScriptsTab()
                    newFolder()
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    private fun ensureScriptsTab() {
        if (binding.bottomNav.selectedItemId != R.id.nav_scripts) {
            binding.bottomNav.selectedItemId = R.id.nav_scripts
        }
    }

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
        val schedules = SchedulePrefs.enabled(this)
        when (schedules.size) {
            0 -> toast("未启用定时")
            1 -> openEditor(schedules.first().scriptId)
            else -> {
                val labels = schedules.map { schedule ->
                    val name = repository.find(schedule.scriptId)?.name ?: "脚本"
                    "$name · ${timeText(schedule.hour, schedule.minute)}"
                }.toTypedArray()
                MaterialAlertDialogBuilder(this)
                    .setTitle("定时脚本")
                    .setItems(labels) { _, which ->
                        openEditor(schedules[which].scriptId)
                    }
                    .show()
            }
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
                    "package:$packageName".toUri()
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
        popup.menu.add(0, 6, 0, "运行")
        popup.menu.add(0, 1, 1, "重命名")
        popup.menu.add(0, 7, 2, "复制")
        popup.menu.add(0, 2, 3, "移动到…")
        popup.menu.add(0, 3, 4, "版本历史")
        popup.menu.add(0, 4, 5, "导出")
        popup.menu.add(0, 5, 6, "删除")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                6 -> {
                    runScript(meta)
                    true
                }

                7 -> {
                    duplicateScript(meta)
                    true
                }

                1 -> {
                    showRenameDialog(meta)
                    true
                }

                2 -> {
                    showMoveDialog(meta)
                    true
                }

                3 -> {
                    startActivity(
                        Intent(this, VersionsActivity::class.java)
                            .putExtra(VersionsActivity.EXTRA_SCRIPT_ID, meta.id)
                    )
                    true
                }

                4 -> {
                    pendingExportId = meta.id
                    exportLauncher.launch("${meta.name}.js")
                    true
                }

                5 -> {
                    confirmDelete(meta)
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    private fun runScript(meta: ScriptMeta) {
        if (RunState.running.value) {
            toast("已有脚本在运行")
            return
        }
        if (!AccessibilityUtils.isEnabled(this)) {
            toast("请先开启无障碍服务")
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        val content = repository.read(meta.id)
        if (content == null) {
            toast("脚本不存在")
            return
        }
        ensureNotificationPermission()
        runCatching {
            ScriptService.start(this, meta.id, meta.name, content)
        }.onFailure {
            toast("启动失败：${it.message}")
        }
    }

    private fun duplicateScript(meta: ScriptMeta) {
        val content = repository.read(meta.id)
        if (content == null) {
            toast("脚本不存在")
            return
        }
        val copy = repository.create("${meta.name} (副本)", content, meta.folderId)
        refresh()
        openEditor(copy.id)
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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
                val schedule = SchedulePrefs.scheduleFor(this, meta.id)
                if (schedule != null) {
                    ScriptScheduler.cancelScript(this, schedule)
                    SchedulePrefs.remove(this, meta.id)
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

    private fun backupAll(uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ScriptBackup.export(this@MainActivity, uri) }
            }
            result.onSuccess { count ->
                toast("已备份 $count 个脚本")
            }.onFailure {
                toast("备份失败：${it.message}")
            }
        }
    }

    private fun confirmRestore(uri: Uri) {
        MaterialAlertDialogBuilder(this)
            .setTitle("恢复备份")
            .setMessage("会覆盖当前所有脚本、文件夹和版本历史，确定继续？")
            .setPositiveButton("恢复") { _, _ -> restoreAll(uri) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun restoreAll(uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ScriptBackup.restore(this@MainActivity, uri) }
            }
            result.onSuccess { count ->
                currentFolderId = null
                refresh()
                showRepositoryHealth()
                toast("已恢复 $count 个脚本")
            }.onFailure {
                toast("恢复失败：${it.message}")
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
        binding.searchInput.clearFocus()
    }

    override fun onDestroy() {
        searchHandler.removeCallbacks(searchRefresh)
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PENDING_EXPORT, pendingExportId)
        outState.putString(KEY_FOLDER, currentFolderId)
        outState.putInt(KEY_TAB, binding.bottomNav.selectedItemId)
    }

    private companion object {
        const val MAX_IMPORT_CHARS = 1_000_000
        const val SEARCH_DEBOUNCE_MS = 250L
        const val BACKUP_FILE_NAME = "MicroAuto-backup.zip"
        const val KEY_PENDING_EXPORT = "pending_export_id"
        const val KEY_FOLDER = "current_folder_id"
        const val KEY_TAB = "current_tab"
    }
}
