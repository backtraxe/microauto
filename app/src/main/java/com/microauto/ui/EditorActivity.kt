package com.microauto.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.microauto.R
import com.microauto.accessibility.AccessibilityUtils
import com.microauto.core.FindMatch
import com.microauto.core.FindReplaceEngine
import com.microauto.core.ScheduleTime
import com.microauto.databinding.ActivityEditorBinding
import com.microauto.databinding.DialogVersionNoteBinding
import com.microauto.databinding.SheetFindReplaceBinding
import com.microauto.databinding.SheetScheduleBinding
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

        private val CLOSING = mapOf(
            "{" to "}",
            "(" to ")",
            "[" to "]",
            "\"" to "\"",
            "'" to "'",
            "`" to "`"
        )
    }

    private lateinit var binding: ActivityEditorBinding
    private lateinit var repository: ScriptRepository
    private lateinit var scriptId: String
    private lateinit var scriptName: String
    private var lastSavedContent = ""
    private var highlighter: JsSyntaxHighlighter? = null
    private var lastLineCount = 0
    private var undoManager: EditorUndoManager? = null
    private var scheduleDialog: BottomSheetDialog? = null
    private var findDialog: BottomSheetDialog? = null
    private var findSheet: SheetFindReplaceBinding? = null
    private var findMatches: List<FindMatch> = emptyList()
    private var findIndex = -1
    private var findQuery = ""
    private var findCaseSensitive = false
    private val findMatchColor = Color.argb(90, 255, 193, 7)
    private val findCurrentColor = Color.argb(150, 255, 152, 0)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val versionsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            reloadScript()
        }
    }

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
        scriptName = meta.name
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdge.apply(this, binding.root)
        binding.logScroll.clipToOutline = true

        binding.toolbar.title = meta.name
        binding.toolbar.setNavigationOnClickListener { requestFinish() }
        binding.toolbar.inflateMenu(R.menu.menu_editor)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_save -> {
                    saveScript()
                    toast("已保存")
                    true
                }

                R.id.action_undo -> {
                    undoManager?.undo()
                    true
                }

                R.id.action_redo -> {
                    undoManager?.redo()
                    true
                }

                R.id.action_find_replace -> {
                    showFindReplace()
                    true
                }

                R.id.action_publish -> {
                    showPublishDialog()
                    true
                }

                R.id.action_versions -> {
                    openVersions()
                    true
                }

                else -> false
            }
        }

        lastSavedContent = repository.read(scriptId).orEmpty()
        binding.scriptEditor.setText(lastSavedContent)
        setupEditor()
        undoManager = EditorUndoManager(binding.scriptEditor) { updateUndoRedo() }
        undoManager?.start()
        updateScheduleSummary()
        bindActions()
        observeState()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                requestFinish()
            }
        })
    }

    override fun onDestroy() {
        scheduleDialog?.dismiss()
        findDialog?.dismiss()
        undoManager?.stop()
        highlighter?.release()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN &&
            (event.isCtrlPressed || event.isMetaPressed)
        ) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_Z -> {
                    if (event.isShiftPressed) undoManager?.redo() else undoManager?.undo()
                    return true
                }

                KeyEvent.KEYCODE_Y -> {
                    undoManager?.redo()
                    return true
                }

                KeyEvent.KEYCODE_F -> {
                    showFindReplace()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun setupEditor() {
        highlighter = JsSyntaxHighlighter(binding.scriptEditor, this).also { it.install() }
        binding.scriptEditor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) = Unit

            override fun afterTextChanged(s: Editable?) {
                binding.scriptEditor.post { updateLineNumbers() }
                if (findDialog?.isShowing == true) {
                    refreshFindMatches(fromCursor = false)
                }
            }
        })
        updateLineNumbers()
        binding.scriptEditor.post { updateLineNumbers() }
        setupSymbolBar()
    }

    private fun updateLineNumbers() {
        val count = binding.scriptEditor.lineCount.coerceAtLeast(1)
        if (count == lastLineCount) return
        lastLineCount = count
        binding.lineNumbers.text = (1..count).joinToString("\n")
    }

    private fun setupSymbolBar() {
        val symbols = listOf(
            "{" to "左花括号", "}" to "右花括号",
            "(" to "左圆括号", ")" to "右圆括号",
            "[" to "左方括号", "]" to "右方括号",
            ";" to "分号", "=" to "等号",
            "\"" to "双引号", "'" to "单引号", "`" to "反引号",
            "+" to "加号", "-" to "减号", "*" to "乘号", "/" to "除号",
            "%" to "百分号", "." to "点", "," to "逗号", ":" to "冒号",
            "?" to "问号", "!" to "感叹号", "&" to "与", "|" to "或",
            "<" to "小于", ">" to "大于", "\\" to "反斜杠",
            "_" to "下划线", "$" to "美元符号", "#" to "井号",
            "@" to "at", "~" to "波浪号", "^" to "异或"
        )
        for ((symbol, description) in symbols) {
            binding.symbolBar.addView(symbolButton(symbol, description) { insertSymbol(symbol) })
        }
        binding.symbolBar.addView(symbolButton("⇥", "插入缩进") { insertIndent() })
        binding.symbolBar.addView(symbolButton("↵", "换行") { insertNewline() })
    }

    private fun symbolButton(
        label: String,
        description: String,
        onClick: () -> Unit
    ): TextView {
        val size = (48 * resources.displayMetrics.density).toInt()
        return TextView(this).apply {
            text = label
            contentDescription = description
            gravity = Gravity.CENTER
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(
                MaterialColors.getColor(
                    binding.symbolBar,
                    com.google.android.material.R.attr.colorOnSurface
                )
            )
            background = selectableBorderlessBackground()
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(size, size)
            setOnClickListener { onClick() }
        }
    }

    private fun selectableBorderlessBackground(): Drawable? {
        val out = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, out, true)
        return if (out.resourceId != 0) ContextCompat.getDrawable(this, out.resourceId) else null
    }

    private fun insertSymbol(symbol: String) {
        undoManager?.breakCoalescing()
        val editor = binding.scriptEditor
        val start = minOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        val end = maxOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        val text = editor.text
        val selected = text.substring(start, end)
        val closing = CLOSING[symbol]
        when {
            closing == null -> {
                text.replace(start, end, symbol)
                editor.setSelection(start + symbol.length)
            }

            selected.isNotEmpty() -> {
                text.replace(start, end, symbol + selected + closing)
                editor.setSelection(start + symbol.length + selected.length + closing.length)
            }

            else -> {
                text.replace(start, end, symbol + closing)
                editor.setSelection(start + symbol.length)
            }
        }
        editor.requestFocus()
        undoManager?.breakCoalescing()
    }

    private fun insertIndent() {
        undoManager?.breakCoalescing()
        val editor = binding.scriptEditor
        val start = minOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        val end = maxOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        editor.text.replace(start, end, "  ")
        editor.setSelection(start + 2)
        editor.requestFocus()
        undoManager?.breakCoalescing()
    }

    private fun insertNewline() {
        undoManager?.breakCoalescing()
        val editor = binding.scriptEditor
        val start = minOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        val end = maxOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        val before = editor.text.substring(0, start)
        val indent = before.substringAfterLast('\n').takeWhile { it == ' ' || it == '\t' }
        val insertion = "\n$indent"
        editor.text.replace(start, end, insertion)
        editor.setSelection(start + insertion.length)
        editor.requestFocus()
        undoManager?.breakCoalescing()
    }

    private fun bindActions() {
        binding.btnRun.setOnClickListener {
            saveScript()
            if (!AccessibilityUtils.isEnabled(this)) {
                toast("请先开启无障碍服务")
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return@setOnClickListener
            }
            ensureNotificationPermission()
            ScriptService.start(
                this,
                scriptId,
                scriptName,
                binding.scriptEditor.text.toString()
            )
        }
        binding.btnStop.setOnClickListener {
            ScriptService.stop(this)
        }
        binding.btnDump.setOnClickListener {
            if (!AccessibilityUtils.isEnabled(this)) {
                toast("请先开启无障碍服务")
                return@setOnClickListener
            }
            ensureNotificationPermission()
            ScriptService.start(
                this,
                null,
                "看界面",
                "toast(\"5 秒内切到目标界面\"); sleep(5000); dump();"
            )
        }
        binding.btnSchedule.setOnClickListener {
            showScheduleSheet()
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

    private fun showPublishDialog() {
        val dialogBinding = DialogVersionNoteBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(this)
            .setTitle("发布版本")
            .setView(dialogBinding.root)
            .setPositiveButton("发布") { _, _ ->
                saveScript()
                val version = repository.publishVersion(
                    scriptId,
                    dialogBinding.versionNoteInput.text.toString()
                )
                if (version != null) {
                    toast("已发布 v${version.version}")
                } else {
                    toast("发布失败")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openVersions() {
        saveScript()
        versionsLauncher.launch(
            Intent(this, VersionsActivity::class.java)
                .putExtra(VersionsActivity.EXTRA_SCRIPT_ID, scriptId)
        )
    }

    private fun reloadScript() {
        val content = repository.read(scriptId).orEmpty()
        lastSavedContent = content
        binding.scriptEditor.setText(content)
        binding.scriptEditor.setSelection(content.length)
        updateLineNumbers()
        binding.scriptEditor.post { updateLineNumbers() }
        undoManager?.reset()
    }

    private fun updateUndoRedo() {
        val menu = binding.toolbar.menu
        menu.findItem(R.id.action_undo)?.isEnabled = undoManager?.canUndo() == true
        menu.findItem(R.id.action_redo)?.isEnabled = undoManager?.canRedo() == true
    }

    @Suppress("DEPRECATION")
    private fun showFindReplace() {
        if (findDialog?.isShowing == true) {
            findSheet?.findInput?.requestFocus()
            return
        }

        val sheet = SheetFindReplaceBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(sheet.root)
        findSheet = sheet
        findDialog = dialog

        selectedTextForFind()?.let { selected ->
            sheet.findInput.setText(selected)
            sheet.findInput.setSelection(selected.length)
        }

        sheet.findInput.doAfterTextChanged {
            refreshFindMatches(fromCursor = true)
        }
        sheet.caseSensitive.setOnCheckedChangeListener { _, _ ->
            refreshFindMatches(fromCursor = true)
        }
        sheet.findInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER
            ) {
                moveFind(1)
                true
            } else {
                false
            }
        }
        sheet.btnFindPrev.setOnClickListener { moveFind(-1) }
        sheet.btnFindNext.setOnClickListener { moveFind(1) }
        sheet.btnReplace.setOnClickListener { replaceCurrent() }
        sheet.btnReplaceAll.setOnClickListener { replaceAll() }

        dialog.setOnDismissListener {
            clearFindHighlights()
            findDialog = null
            findSheet = null
            findMatches = emptyList()
            findIndex = -1
            findQuery = ""
            findCaseSensitive = false
        }
        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        sheet.findInput.post {
            sheet.findInput.requestFocus()
            getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(sheet.findInput, InputMethodManager.SHOW_IMPLICIT)
        }
        refreshFindMatches(fromCursor = true)
    }

    private fun selectedTextForFind(): String? {
        val start = minOf(
            binding.scriptEditor.selectionStart,
            binding.scriptEditor.selectionEnd
        )
        val end = maxOf(
            binding.scriptEditor.selectionStart,
            binding.scriptEditor.selectionEnd
        )
        if (start < 0 || end <= start) return null
        val selected = binding.scriptEditor.text
            ?.subSequence(start, end)
            ?.toString()
            ?: return null
        return selected.takeIf { it.length <= 100 && '\n' !in it }
    }

    private fun refreshFindMatches(fromCursor: Boolean) {
        val sheet = findSheet ?: return
        findQuery = sheet.findInput.text?.toString().orEmpty()
        findCaseSensitive = sheet.caseSensitive.isChecked
        val text = binding.scriptEditor.text?.toString().orEmpty()
        findMatches = FindReplaceEngine.findAll(text, findQuery, findCaseSensitive)
        if (findMatches.isEmpty()) {
            findIndex = -1
        } else if (fromCursor || findIndex !in findMatches.indices) {
            val cursor = binding.scriptEditor.selectionStart.coerceAtLeast(0)
            findIndex = findMatches
                .indexOfFirst { it.endExclusive > cursor }
                .let { if (it < 0) 0 else it }
        }
        applyFindHighlights()
        updateFindCount()
    }

    private fun applyFindHighlights() {
        val text = binding.scriptEditor.text ?: return
        text.getSpans(0, text.length, FindHighlightSpan::class.java)
            .forEach { text.removeSpan(it) }
        if (findQuery.isEmpty()) return
        for ((index, match) in findMatches.withIndex()) {
            if (match.start < 0 || match.endExclusive > text.length) continue
            val color = if (index == findIndex) findCurrentColor else findMatchColor
            text.setSpan(
                FindHighlightSpan(color),
                match.start,
                match.endExclusive,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun clearFindHighlights() {
        val text = binding.scriptEditor.text ?: return
        text.getSpans(0, text.length, FindHighlightSpan::class.java)
            .forEach { text.removeSpan(it) }
    }

    private fun updateFindCount() {
        val sheet = findSheet ?: return
        sheet.matchCount.text = when {
            findQuery.isEmpty() -> "0/0"
            findMatches.isEmpty() -> "无匹配"
            else -> "${findIndex + 1}/${findMatches.size}"
        }
    }

    private fun moveFind(direction: Int) {
        if (findMatches.isEmpty()) return
        findIndex = FindReplaceEngine.nextIndex(findIndex, findMatches.size, direction)
        selectFindMatch()
    }

    private fun selectFindMatch() {
        val match = findMatches.getOrNull(findIndex) ?: return
        val length = binding.scriptEditor.text?.length ?: return
        if (match.start < 0 || match.endExclusive > length) return
        binding.scriptEditor.setSelection(match.start, match.endExclusive)
        binding.scriptEditor.post {
            binding.scriptEditor.bringPointIntoView(match.start)
        }
        applyFindHighlights()
        updateFindCount()
    }

    private fun replaceCurrent() {
        val match = findMatches.getOrNull(findIndex) ?: return
        val replacement = findSheet?.replaceInput?.text?.toString().orEmpty()
        val editable = binding.scriptEditor.text ?: return
        undoManager?.breakCoalescing()
        editable.replace(match.start, match.endExclusive, replacement)
        undoManager?.breakCoalescing()
        selectFindMatch()
    }

    private fun replaceAll() {
        if (findMatches.isEmpty()) return
        val replacement = findSheet?.replaceInput?.text?.toString().orEmpty()
        val editable = binding.scriptEditor.text ?: return
        val replaced = FindReplaceEngine.replaceAll(
            editable.toString(),
            findMatches,
            replacement
        )
        undoManager?.breakCoalescing()
        editable.replace(0, editable.length, replaced)
        undoManager?.breakCoalescing()
        binding.scriptEditor.setSelection(0)
        refreshFindMatches(fromCursor = true)
        selectFindMatch()
    }

    private class FindHighlightSpan(color: Int) : BackgroundColorSpan(color)

    private fun requestFinish() {
        val current = binding.scriptEditor.text.toString()
        if (current == lastSavedContent) {
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
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

    private fun updateScheduleSummary() {
        val schedule = SchedulePrefs.scheduleFor(this, scriptId)
        binding.scheduleSummary.text = if (schedule?.enabled == true) {
            "每天 ${timeText(schedule.hour, schedule.minute)}"
        } else {
            "未启用"
        }
    }

    private fun showScheduleSheet() {
        val sheetBinding = SheetScheduleBinding.inflate(layoutInflater)
        val current = SchedulePrefs.scheduleFor(this, scriptId)
        sheetBinding.scheduleTime.setText(
            if (current != null) {
                timeText(current.hour, current.minute)
            } else {
                "08:00"
            }
        )
        sheetBinding.scheduleSwitch.isChecked = current?.enabled == true

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(sheetBinding.root)
        scheduleDialog = dialog
        dialog.setOnDismissListener { scheduleDialog = null }
        sheetBinding.btnSaveSchedule.setOnClickListener {
            val parsed = ScheduleTime.parseTime(
                sheetBinding.scheduleTime.text?.toString().orEmpty()
            )
            if (parsed == null) {
                toast("时间格式应为 HH:mm，例如 08:05")
                return@setOnClickListener
            }
            val enabled = sheetBinding.scheduleSwitch.isChecked
            if (!enabled && current == null) {
                toast("当前脚本未启用定时")
                dialog.dismiss()
                return@setOnClickListener
            }
            if (enabled) {
                current?.let { ScriptScheduler.cancelScript(this, it) }
                val schedule = SchedulePrefs.save(
                    this,
                    scriptId,
                    true,
                    parsed.first,
                    parsed.second
                )
                SchedulePrefs.clearPending(this, scriptId)
                ScriptScheduler.scheduleScript(this, schedule)
                ensureNotificationPermission()
                toast(
                    "已设置每天 ${
                        String.format(Locale.US, "%02d:%02d", parsed.first, parsed.second)
                    }"
                )
            } else {
                current?.let { ScriptScheduler.cancelScript(this, it) }
                SchedulePrefs.remove(this, scriptId)
                toast("已关闭定时")
            }
            updateScheduleSummary()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun timeText(hour: Int, minute: Int): String = String.format(
        Locale.US,
        "%02d:%02d",
        hour,
        minute
    )

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

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
