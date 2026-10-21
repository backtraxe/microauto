package com.microauto.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.microauto.R
import com.microauto.accessibility.AccessibilityUtils
import com.microauto.core.BoundedTextReader
import com.microauto.core.DateFormats
import com.microauto.core.ScriptLimits
import com.microauto.core.ScriptNaming
import com.microauto.databinding.ActivityMainBinding
import com.microauto.databinding.DialogRenameBinding
import com.microauto.databinding.SheetEnvironmentBinding
import com.microauto.schedule.SchedulePrefs
import com.microauto.schedule.ScriptScheduler
import com.microauto.script.FolderMeta
import com.microauto.script.RepositoryHealth
import com.microauto.script.RunPhase
import com.microauto.script.RunSnapshot
import com.microauto.script.RunSource
import com.microauto.script.RunState
import com.microauto.script.ScriptBackup
import com.microauto.script.ScriptMeta
import com.microauto.script.ScriptRepository
import com.microauto.script.ScriptService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: ScriptRepository
    private lateinit var adapter: LibraryAdapter
    private var currentFolderId: String? = null
    private var accessibilityWasEnabled = false
    private var environmentDialog: BottomSheetDialog? = null
    private var accessibilityDialog: AlertDialog? = null
    private val searchHandler = Handler(Looper.getMainLooper())
    private val searchRefresh = Runnable { refresh() }
    private val statusHandler = Handler(Looper.getMainLooper())
    private var runStatusShown = false
    private var observedAnySnapshot = false
    private var lastRenderedStartedAt: Long? = null
    private var lastAnnouncedPhase: RunPhase? = null
    private val statusTicker = object : Runnable {
        override fun run() {
            val snapshot = RunState.snapshot.value ?: return
            if (!snapshot.phase.isActive() || !runStatusShown) return
            binding.runStatusMeta.text = activeMeta(snapshot)
            statusHandler.postDelayed(this, STATUS_TICK_MS)
        }
    }
    private var statusHide: Runnable? = null

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
        accessibilityWasEnabled = AccessibilityUtils.isEnabled(this)
        adapter = LibraryAdapter(
            onFolderClick = { openFolder(it) },
            onFolderMenu = { folder, anchor -> showFolderMenu(folder, anchor) },
            onScriptClick = { openEditor(it.id) },
            onScriptMenu = { meta, anchor -> showScriptMenu(meta, anchor) },
            onScriptRun = { runScript(it) },
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
        observeRunState()
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
        val enabled = AccessibilityUtils.isEnabled(this)
        if (!accessibilityWasEnabled && enabled) {
            Snackbar.make(binding.root, "无障碍服务已开启", Snackbar.LENGTH_LONG)
                .setAnchorView(binding.navCard)
                .show()
        }
        accessibilityWasEnabled = enabled
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
                R.id.action_environment -> {
                    showEnvironmentSheet()
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
        binding.setupBanner.setOnClickListener { showEnvironmentSheet() }
        binding.btnEmptyNew.setOnClickListener { newScript() }
        binding.btnEmptyImport.setOnClickListener {
            importLauncher.launch(arrayOf("*/*"))
        }
        binding.btnEmptyFolder.setOnClickListener { newFolder() }
        binding.bottomStack.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            binding.bottomStack.post { updateListPadding() }
        }
        setupTutorialHeadings()
    }

    private fun setupTutorialHeadings() {
        listOf(
            binding.tutorialContent.tutorialQuickStartTitle,
            binding.tutorialContent.tutorialFirstScriptTitle,
            binding.tutorialContent.tutorialScheduleTitle,
            binding.tutorialContent.tutorialFolderTitle,
            binding.tutorialContent.tutorialVersionsTitle,
            binding.tutorialContent.tutorialSearchTitle,
            binding.tutorialContent.tutorialImportExportTitle,
            binding.tutorialContent.tutorialLimitsTitle,
            binding.tutorialContent.tutorialBuiltinsTitle,
        ).forEach { ViewCompat.setAccessibilityHeading(it, true) }
    }

    private fun observeRunState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    RunState.running,
                    RunState.activeScriptId,
                    RunState.snapshot,
                ) { running, scriptId, snapshot ->
                    Triple(running, scriptId, snapshot)
                }.collect { (running, scriptId, snapshot) ->
                    adapter.setRunState(running, scriptId)
                    renderRunStatus(snapshot)
                }
            }
        }
    }

    private fun renderRunStatus(snapshot: RunSnapshot?) {
        val firstObservation = !observedAnySnapshot
        observedAnySnapshot = true
        if (snapshot == null || snapshot.source == RunSource.DUMP) {
            statusHandler.removeCallbacks(statusTicker)
            cancelStatusHide()
            setRunStatusVisible(false)
            if (firstObservation) {
                lastRenderedStartedAt = snapshot?.startedAt
                lastAnnouncedPhase = snapshot?.phase
            }
            return
        }
        if (snapshot.phase.isRestorableResult() &&
            snapshot.finishedAt != null &&
            System.currentTimeMillis() - snapshot.finishedAt > RESULT_RESTORE_WINDOW_MS
        ) {
            RunState.dismiss(snapshot.startedAt)
            return
        }

        if (firstObservation) {
            lastRenderedStartedAt = snapshot.startedAt
            lastAnnouncedPhase = snapshot.phase
        } else if (lastRenderedStartedAt != snapshot.startedAt) {
            lastRenderedStartedAt = snapshot.startedAt
            lastAnnouncedPhase = null
        }

        binding.runStatusTitle.text = statusTitle(snapshot)
        binding.runStatusMeta.text = statusMeta(snapshot)
        bindStatusIcon(snapshot.phase)
        bindStatusAction(snapshot)
        setRunStatusVisible(true)

        if (snapshot.phase != lastAnnouncedPhase) {
            announcementFor(snapshot)?.let { binding.root.announceForAccessibility(it) }
            lastAnnouncedPhase = snapshot.phase
        }

        statusHandler.removeCallbacks(statusTicker)
        cancelStatusHide()
        when (snapshot.phase) {
            RunPhase.STARTING, RunPhase.RUNNING -> {
                statusHandler.postDelayed(statusTicker, STATUS_TICK_MS)
            }

            RunPhase.SUCCESS -> {
                scheduleStatusHide(snapshot.startedAt, SUCCESS_VISIBLE_MS)
            }

            RunPhase.STOPPED -> {
                scheduleStatusHide(snapshot.startedAt, STOPPED_VISIBLE_MS)
            }

            RunPhase.FAILED -> Unit
        }
    }

    private fun scheduleStatusHide(expectedStartedAt: Long, delayMillis: Long) {
        cancelStatusHide()
        val action = Runnable { RunState.dismiss(expectedStartedAt) }
        statusHide = action
        statusHandler.postDelayed(action, delayMillis)
    }

    private fun cancelStatusHide() {
        statusHide?.let { statusHandler.removeCallbacks(it) }
        statusHide = null
    }

    private fun bindStatusIcon(phase: RunPhase) {
        val (icon, colorAttr) = when (phase) {
            RunPhase.STARTING, RunPhase.RUNNING ->
                R.drawable.ic_play_arrow to androidx.appcompat.R.attr.colorPrimary

            RunPhase.SUCCESS ->
                R.drawable.ic_check to androidx.appcompat.R.attr.colorPrimary

            RunPhase.FAILED ->
                R.drawable.ic_warning to androidx.appcompat.R.attr.colorError

            RunPhase.STOPPED ->
                R.drawable.ic_stop to com.google.android.material.R.attr.colorOnSurfaceVariant
        }
        binding.runStatusIcon.setImageResource(icon)
        binding.runStatusIcon.imageTintList = ColorStateList.valueOf(
            MaterialColors.getColor(binding.runStatusCard, colorAttr)
        )
    }

    private fun bindStatusAction(snapshot: RunSnapshot) {
        val active = snapshot.phase == RunPhase.STARTING || snapshot.phase == RunPhase.RUNNING
        binding.runStatusClose.isVisible = !active
        binding.runStatusClose.contentDescription =
            "关闭「${snapshot.scriptName}」的运行状态"
        binding.runStatusAction.text = if (active) "停止" else "查看日志"
        binding.runStatusAction.contentDescription = if (active) {
            "停止运行「${snapshot.scriptName}」"
        } else {
            "查看「${snapshot.scriptName}」的运行日志"
        }
        binding.runStatusAction.setOnClickListener {
            if (active) {
                RunState.activeToken.value?.let { token -> ScriptService.stop(this, token) }
            } else {
                openRunLog(snapshot)
            }
        }
        binding.runStatusClose.setOnClickListener {
            RunState.dismiss(snapshot.startedAt)
        }
    }

    private fun statusTitle(snapshot: RunSnapshot): String = when (snapshot.phase) {
        RunPhase.STARTING -> "正在启动「${snapshot.scriptName}」"
        RunPhase.RUNNING -> "正在运行「${snapshot.scriptName}」"
        RunPhase.SUCCESS -> "运行完成"
        RunPhase.FAILED -> "运行失败"
        RunPhase.STOPPED -> "已停止"
    }

    private fun statusMeta(snapshot: RunSnapshot): String = when (snapshot.phase) {
        RunPhase.STARTING -> "准备中…"
        RunPhase.RUNNING -> activeMeta(snapshot)
        else -> finishedMeta(snapshot)
    }

    private fun activeMeta(snapshot: RunSnapshot): String =
        "已运行 ${clockText(snapshot.startedAt, System.currentTimeMillis())} · " +
            sourceText(snapshot.source)

    private fun finishedMeta(snapshot: RunSnapshot): String {
        val duration = ((snapshot.finishedAt ?: System.currentTimeMillis()) - snapshot.startedAt)
            .coerceAtLeast(0)
        val base = when (snapshot.phase) {
            RunPhase.SUCCESS ->
                "「${snapshot.scriptName}」· 用时 ${decimalSeconds(duration)} 秒"

            RunPhase.STOPPED ->
                "「${snapshot.scriptName}」· 用时 ${wholeSeconds(duration)} 秒"

            RunPhase.FAILED -> buildString {
                append("「${snapshot.scriptName}」· ")
                snapshot.errorLine?.let { append("第 $it 行 ") }
                append(snapshot.errorMessage?.takeIf { it.isNotBlank() } ?: "运行出错")
            }

            else -> ""
        }
        return if (snapshot.source == RunSource.SCHEDULE) "$base · 定时任务" else base
    }

    private fun sourceText(source: RunSource): String = when (source) {
        RunSource.MANUAL -> "手动运行"
        RunSource.SCHEDULE -> "定时任务"
        RunSource.DUMP -> "看界面"
    }

    private fun announcementFor(snapshot: RunSnapshot): String? = when (snapshot.phase) {
        RunPhase.STARTING -> "开始运行"
        RunPhase.RUNNING -> null
        RunPhase.SUCCESS -> "运行完成"
        RunPhase.FAILED -> snapshot.errorLine
            ?.let { "运行失败，第 $it 行" }
            ?: "运行失败"

        RunPhase.STOPPED -> "已停止"
    }

    private fun setRunStatusVisible(visible: Boolean) {
        if (runStatusShown == visible) return
        runStatusShown = visible
        updateListPadding()
        binding.runStatusCard.animate().cancel()
        if (visible) {
            binding.runStatusCard.alpha = 0f
            binding.runStatusCard.isVisible = true
            binding.runStatusCard.animate()
                .alpha(1f)
                .setDuration(160)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            binding.runStatusCard.isVisible = false
            binding.runStatusCard.alpha = 1f
        }
        binding.bottomStack.post { updateListPadding() }
    }

    private fun updateListPadding() {
        val stackHeight = binding.bottomStack.height
        val dynamic = if (stackHeight > 0) stackHeight + dp(BOTTOM_STACK_GAP_DP) else 0
        val listBottom = maxOf(
            dynamic,
            dp(if (runStatusShown) LIST_PADDING_WITH_STATUS_DP else LIST_PADDING_DP),
        )
        if (binding.scriptList.paddingBottom != listBottom) {
            binding.scriptList.setPadding(
                binding.scriptList.paddingLeft,
                binding.scriptList.paddingTop,
                binding.scriptList.paddingRight,
                listBottom,
            )
        }
        val tutorialBottom = maxOf(
            dynamic,
            dp(if (runStatusShown) TUTORIAL_PADDING_WITH_STATUS_DP else TUTORIAL_PADDING_DP),
        )
        if (binding.tutorialContent.root.paddingBottom != tutorialBottom) {
            binding.tutorialContent.root.setPadding(
                binding.tutorialContent.root.paddingLeft,
                binding.tutorialContent.root.paddingTop,
                binding.tutorialContent.root.paddingRight,
                tutorialBottom,
            )
        }
        val emptyBottom = maxOf(
            dynamic,
            dp(if (runStatusShown) EMPTY_PADDING_WITH_STATUS_DP else EMPTY_PADDING_DP),
        )
        if (binding.emptyState.paddingBottom != emptyBottom) {
            binding.emptyState.setPadding(
                binding.emptyState.paddingLeft,
                binding.emptyState.paddingTop,
                binding.emptyState.paddingRight,
                emptyBottom,
            )
        }
    }

    private fun openRunLog(snapshot: RunSnapshot) {
        val scriptId = snapshot.scriptId
        if (scriptId == null || repository.find(scriptId) == null) {
            Snackbar.make(binding.root, "脚本已删除", Snackbar.LENGTH_SHORT).show()
            refresh()
            RunState.dismiss(snapshot.startedAt)
            return
        }
        startActivity(
            Intent(this, EditorActivity::class.java)
                .putExtra(EditorActivity.EXTRA_SCRIPT_ID, scriptId)
                .putExtra(EditorActivity.EXTRA_OPEN_LOG, true)
        )
    }

    private fun RunPhase.isActive(): Boolean =
        this == RunPhase.STARTING || this == RunPhase.RUNNING

    private fun RunPhase.isRestorableResult(): Boolean =
        this == RunPhase.SUCCESS || this == RunPhase.STOPPED

    private fun clockText(startedAt: Long, now: Long): String {
        val totalSeconds = ((now - startedAt).coerceAtLeast(0)) / 1_000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun decimalSeconds(millis: Long): String =
        String.format(Locale.US, "%.1f", millis / 1_000.0)

    private fun wholeSeconds(millis: Long): Long =
        (millis / 1_000.0).let { kotlin.math.round(it).toLong() }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun showEnvironmentSheet() {
        environmentDialog?.dismiss()
        val sheetBinding = SheetEnvironmentBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(sheetBinding.root)
        updateEnvironmentSheet(sheetBinding)

        fun dismissAnd(action: () -> Unit) {
            dialog.dismiss()
            action()
        }

        sheetBinding.rowAccessibility.setOnClickListener {
            dismissAnd { openAccessibilitySettings() }
        }
        sheetBinding.btnAccessibility.setOnClickListener {
            dismissAnd { openAccessibilitySettings() }
        }
        sheetBinding.rowNotification.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !hasNotificationPermission()
            ) {
                dismissAnd { ensureNotificationPermission() }
            }
        }
        sheetBinding.btnNotification.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !hasNotificationPermission()
            ) {
                dismissAnd { ensureNotificationPermission() }
            }
        }
        sheetBinding.rowAlarm.setOnClickListener {
            dismissAnd { openExactAlarmSettings() }
        }
        sheetBinding.btnAlarm.setOnClickListener {
            dismissAnd { openExactAlarmSettings() }
        }
        sheetBinding.rowBattery.setOnClickListener {
            dismissAnd { openBatterySettings() }
        }
        sheetBinding.btnBattery.setOnClickListener {
            dismissAnd { openBatterySettings() }
        }
        environmentDialog = dialog
        dialog.setOnDismissListener {
            if (environmentDialog === dialog) environmentDialog = null
        }
        dialog.show()
    }

    private fun updateEnvironmentSheet(sheet: SheetEnvironmentBinding) {
        val primaryAttr = androidx.appcompat.R.attr.colorPrimary
        val mutedAttr = com.google.android.material.R.attr.colorOnSurfaceVariant
        val errorAttr = androidx.appcompat.R.attr.colorError

        val accessibilityEnabled = AccessibilityUtils.isEnabled(this)
        setEnvironmentStatus(
            sheet.accessibilityStatus,
            if (accessibilityEnabled) "已开启" else "未开启",
            if (accessibilityEnabled) primaryAttr else errorAttr
        )
        sheet.btnAccessibility.isEnabled = !accessibilityEnabled

        val notificationSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val notificationGranted = !notificationSupported || hasNotificationPermission()
        setEnvironmentStatus(
            sheet.notificationStatus,
            when {
                !notificationSupported -> "无需设置"
                notificationGranted -> "已允许"
                else -> "未允许"
            },
            if (notificationGranted) primaryAttr else mutedAttr
        )
        sheet.btnNotification.isEnabled = notificationSupported && !notificationGranted

        val alarmSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val alarmAvailable = alarmSupported &&
            getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
        setEnvironmentStatus(
            sheet.alarmStatus,
            when {
                !alarmSupported -> "无需授权"
                alarmAvailable -> "可用"
                else -> "未授权"
            },
            if (!alarmSupported || alarmAvailable) primaryAttr else mutedAttr
        )
        sheet.btnAlarm.isEnabled = alarmSupported

        val ignoringBattery = getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(packageName) == true
        setEnvironmentStatus(
            sheet.batteryStatus,
            if (ignoringBattery) "已允许" else "未允许",
            if (ignoringBattery) primaryAttr else mutedAttr
        )
    }

    private fun setEnvironmentStatus(view: TextView, text: String, colorAttr: Int) {
        view.text = text
        view.setTextColor(MaterialColors.getColor(view, colorAttr))
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

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
        val schedules = SchedulePrefs.enabled(this)
        binding.toolbar.menu.findItem(R.id.action_schedule)?.title = when {
            schedules.isEmpty() -> "定时：未启用"
            schedules.size == 1 -> {
                val schedule = schedules.first()
                val name = repository.find(schedule.scriptId)?.name ?: "脚本"
                "定时 ${timeText(schedule.hour, schedule.minute)} · $name"
            }

            else -> "定时：${schedules.size} 个脚本"
        }
        updateSetupBanner()
    }

    private fun updateSetupBanner() {
        val accessibilityEnabled = AccessibilityUtils.isEnabled(this)
        val exactAlarmUnavailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() != true
        val hasEnabledSchedule = SchedulePrefs.enabled(this).isNotEmpty()
        when {
            !accessibilityEnabled -> {
                binding.setupIcon.setImageResource(R.drawable.ic_warning)
                binding.setupIcon.imageTintList = ColorStateList.valueOf(
                    MaterialColors.getColor(
                        binding.setupBanner,
                        androidx.appcompat.R.attr.colorError
                    )
                )
                binding.setupTitle.setText(R.string.setup_accessibility_title)
                binding.setupHint.setText(R.string.setup_accessibility_hint)
                binding.setupAction.setText(R.string.setup_accessibility_action)
                binding.setupAction.setOnClickListener {
                    openAccessibilitySettings()
                }
                setBannerVisible(true)
            }

            exactAlarmUnavailable && hasEnabledSchedule -> {
                binding.setupIcon.setImageResource(R.drawable.ic_clock)
                binding.setupIcon.imageTintList = ColorStateList.valueOf(
                    MaterialColors.getColor(
                        binding.setupBanner,
                        androidx.appcompat.R.attr.colorPrimary
                    )
                )
                binding.setupTitle.setText(R.string.setup_alarm_title)
                binding.setupHint.setText(R.string.setup_alarm_hint)
                binding.setupAction.setText(R.string.setup_alarm_action)
                binding.setupAction.setOnClickListener {
                    openExactAlarmSettings()
                }
                setBannerVisible(true)
            }

            else -> setBannerVisible(false)
        }
    }

    private fun setBannerVisible(visible: Boolean) {
        val banner = binding.setupBanner
        banner.animate().cancel()
        if (visible) {
            if (!banner.isVisible) {
                banner.alpha = 0f
                banner.isVisible = true
                banner.animate()
                    .alpha(1f)
                    .setDuration(160)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            } else {
                banner.alpha = 1f
            }
        } else if (banner.isVisible) {
            banner.animate()
                .alpha(0f)
                .setDuration(160)
                .withEndAction { banner.isVisible = false }
                .start()
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
            Snackbar.make(binding.root, "已有脚本在运行", Snackbar.LENGTH_SHORT).show()
            return
        }
        if (!AccessibilityUtils.isEnabled(this)) {
            showAccessibilityRequiredDialog()
            return
        }
        val content = repository.read(meta.id)
        if (content == null) {
            Snackbar.make(binding.root, "脚本不存在，可能已被删除", Snackbar.LENGTH_SHORT).show()
            refresh()
            return
        }
        ScriptLimits.scriptError(content)?.let { message ->
            Snackbar.make(binding.root, "无法运行：$message", Snackbar.LENGTH_LONG).show()
            return
        }
        ensureNotificationPermission()
        runCatching {
            ScriptService.start(this, meta.id, meta.name, content)
        }.onFailure {
            RunState.failStart(
                it.message ?: "无法启动运行服务",
                meta.id,
                meta.name,
                RunSource.MANUAL,
            )
            Snackbar.make(binding.root, "启动失败：${it.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun showAccessibilityRequiredDialog() {
        accessibilityDialog?.dismiss()
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("需要开启无障碍服务")
            .setMessage("MicroAuto 通过无障碍服务读取界面和执行点击。开启后返回即可运行当前脚本。")
            .setPositiveButton("去开启") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("取消", null)
            .create()
        accessibilityDialog = dialog
        dialog.setOnDismissListener {
            if (accessibilityDialog === dialog) accessibilityDialog = null
        }
        dialog.show()
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
            ?.use { BoundedTextReader.read(it) }
            ?: error("无法读取文件")
        ScriptLimits.scriptError(content)?.let { error(it) }
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
            .setMessage(
                "会覆盖当前所有脚本、文件夹和版本历史；已有定时会被取消，需要重新开启。确定继续？"
            )
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

    override fun onStop() {
        statusHandler.removeCallbacks(statusTicker)
        cancelStatusHide()
        super.onStop()
    }

    override fun onDestroy() {
        searchHandler.removeCallbacks(searchRefresh)
        statusHandler.removeCallbacks(statusTicker)
        cancelStatusHide()
        environmentDialog?.dismiss()
        accessibilityDialog?.dismiss()
        environmentDialog = null
        accessibilityDialog = null
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PENDING_EXPORT, pendingExportId)
        outState.putString(KEY_FOLDER, currentFolderId)
        outState.putInt(KEY_TAB, binding.bottomNav.selectedItemId)
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
        const val BACKUP_FILE_NAME = "MicroAuto-backup.zip"
        const val KEY_PENDING_EXPORT = "pending_export_id"
        const val KEY_FOLDER = "current_folder_id"
        const val KEY_TAB = "current_tab"
        const val STATUS_TICK_MS = 1_000L
        const val SUCCESS_VISIBLE_MS = 4_000L
        const val STOPPED_VISIBLE_MS = 3_000L
        const val RESULT_RESTORE_WINDOW_MS = 30_000L
        const val BOTTOM_STACK_GAP_DP = 24
        const val LIST_PADDING_DP = 104
        const val LIST_PADDING_WITH_STATUS_DP = 176
        const val TUTORIAL_PADDING_DP = 120
        const val TUTORIAL_PADDING_WITH_STATUS_DP = 176
        const val EMPTY_PADDING_DP = 120
        const val EMPTY_PADDING_WITH_STATUS_DP = 176
    }
}
