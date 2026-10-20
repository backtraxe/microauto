package com.microauto.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.microauto.R
import com.microauto.accessibility.AccessibilityUtils
import com.microauto.core.ScheduleTime
import com.microauto.databinding.ActivityEditorBinding
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
    private var lastSavedContent = ""
    private var highlighter: JsSyntaxHighlighter? = null
    private var lastLineCount = 0

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

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
        EdgeToEdge.apply(this, binding.root)

        binding.toolbar.title = meta.name
        binding.toolbar.setNavigationOnClickListener { requestFinish() }
        binding.toolbar.inflateMenu(R.menu.menu_editor)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_save) {
                saveScript()
                toast("已保存")
                true
            } else {
                false
            }
        }

        lastSavedContent = repository.read(scriptId).orEmpty()
        binding.scriptEditor.setText(lastSavedContent)
        setupEditor()
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
        highlighter?.release()
        super.onDestroy()
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
    }

    private fun insertIndent() {
        val editor = binding.scriptEditor
        val start = minOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        val end = maxOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        editor.text.replace(start, end, "  ")
        editor.setSelection(start + 2)
        editor.requestFocus()
    }

    private fun insertNewline() {
        val editor = binding.scriptEditor
        val start = minOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        val end = maxOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        val before = editor.text.substring(0, start)
        val indent = before.substringAfterLast('\n').takeWhile { it == ' ' || it == '\t' }
        val insertion = "\n$indent"
        editor.text.replace(start, end, insertion)
        editor.setSelection(start + insertion.length)
        editor.requestFocus()
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
            ensureNotificationPermission()
            ScriptService.start(
                this,
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
        val scheduledHere = SchedulePrefs.isEnabled(this) &&
            SchedulePrefs.scriptId(this) == scriptId
        binding.scheduleSummary.text = if (scheduledHere) {
            "每天 ${timeText()}"
        } else {
            "未启用"
        }
    }

    private fun showScheduleSheet() {
        val sheetBinding = SheetScheduleBinding.inflate(layoutInflater)
        val currentlyScheduled = SchedulePrefs.scriptId(this) == scriptId
        sheetBinding.scheduleTime.setText(
            if (currentlyScheduled) {
                timeText()
            } else {
                "08:00"
            }
        )
        sheetBinding.scheduleSwitch.isChecked =
            currentlyScheduled && SchedulePrefs.isEnabled(this)

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(sheetBinding.root)
        sheetBinding.btnSaveSchedule.setOnClickListener {
            val parsed = ScheduleTime.parseTime(
                sheetBinding.scheduleTime.text?.toString().orEmpty()
            )
            if (parsed == null) {
                toast("时间格式应为 HH:mm，例如 08:05")
                return@setOnClickListener
            }
            val enabled = sheetBinding.scheduleSwitch.isChecked
            if (!enabled && !currentlyScheduled) {
                toast("当前脚本未启用定时")
                dialog.dismiss()
                return@setOnClickListener
            }
            if (enabled) {
                SchedulePrefs.save(this, scriptId, true, parsed.first, parsed.second)
                ScriptScheduler.scheduleNext(this)
                toast(
                    "已设置每天 ${
                        String.format(Locale.US, "%02d:%02d", parsed.first, parsed.second)
                    }"
                )
            } else {
                SchedulePrefs.setEnabled(this, false)
                SchedulePrefs.setScriptId(this, null)
                ScriptScheduler.cancel(this)
                toast("已关闭定时")
            }
            updateScheduleSummary()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun timeText(): String = String.format(
        Locale.US,
        "%02d:%02d",
        SchedulePrefs.hour(this),
        SchedulePrefs.minute(this)
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
