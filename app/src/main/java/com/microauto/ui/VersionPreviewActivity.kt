package com.microauto.ui

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.microauto.R
import com.microauto.core.DateFormats
import com.microauto.core.DiffKind
import com.microauto.core.DiffResult
import com.microauto.core.LineDiff
import com.microauto.databinding.ActivityVersionPreviewBinding
import com.microauto.script.ScriptRepository
import com.microauto.script.ScriptVersion

class VersionPreviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SCRIPT_ID = "script_id"
        const val EXTRA_VERSION_ID = "version_id"
    }

    private lateinit var binding: ActivityVersionPreviewBinding
    private lateinit var repository: ScriptRepository
    private lateinit var scriptId: String
    private lateinit var version: ScriptVersion
    private var versionContent: String? = null
    private var sameAsCurrent = false
    private var diff: DiffResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scriptId = intent.getStringExtra(EXTRA_SCRIPT_ID) ?: run {
            finish()
            return
        }
        val versionId = intent.getStringExtra(EXTRA_VERSION_ID) ?: run {
            finish()
            return
        }
        repository = ScriptRepository(this)
        if (repository.find(scriptId) == null) {
            finish()
            return
        }
        version = repository.versions(scriptId).firstOrNull { it.id == versionId } ?: run {
            finish()
            return
        }

        binding = ActivityVersionPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdge.apply(this, binding.root)
        binding.previewToolbar.title = "v${version.version}"
        binding.previewToolbar.setNavigationOnClickListener { finish() }
        binding.btnClosePreview.setOnClickListener { finish() }
        binding.btnRestoreVersion.setOnClickListener { confirmRestore() }

        versionContent = repository.readVersion(version.id)
        val currentContent = repository.read(scriptId).orEmpty()
        if (versionContent != null) {
            if (versionContent == currentContent) {
                sameAsCurrent = true
            } else {
                val computed = LineDiff.diff(currentContent, versionContent!!)
                sameAsCurrent = !computed.tooLarge && computed.added == 0 && computed.removed == 0
                if (!sameAsCurrent) diff = computed
            }
        }
        bindHeader()
        bindCode()
    }

    private fun bindHeader() {
        val content = versionContent
        binding.previewNote.text = version.note.ifBlank {
            getString(R.string.version_note_empty)
        }
        binding.previewTime.text = DateFormats.format(version.createdAt)
        binding.previewHint.isVisible = false
        binding.previewTooLarge.isVisible = false

        if (content == null) {
            binding.previewLineCount.isVisible = false
            binding.previewDiffSummary.isVisible = false
            binding.btnRestoreVersion.isEnabled = false
            return
        }

        binding.previewLineCount.isVisible = true
        binding.previewLineCount.text = getString(
            R.string.version_line_count,
            lineCount(content),
        )
        binding.btnRestoreVersion.isEnabled = !sameAsCurrent
        if (sameAsCurrent) {
            binding.previewDiffSummary.isVisible = true
            binding.previewDiffSummary.setText(R.string.version_same_content)
            binding.previewHint.isVisible = true
            binding.previewHint.setText(R.string.version_current_is_this)
            return
        }

        val result = diff
        if (result?.tooLarge == true) {
            binding.previewDiffSummary.isVisible = false
            binding.previewTooLarge.isVisible = true
        } else {
            binding.previewDiffSummary.isVisible = true
            binding.previewDiffSummary.text = getString(
                R.string.version_diff_summary,
                result?.added ?: 0,
                result?.removed ?: 0,
            )
        }
        if (content.isEmpty()) {
            binding.previewHint.isVisible = true
            binding.previewHint.setText(R.string.version_empty_content)
        }
    }

    private fun bindCode() {
        val content = versionContent
        binding.previewEmpty.isVisible = content == null
        binding.previewScroll.isVisible = content != null
        if (content == null) {
            binding.previewCode.text = ""
            return
        }
        val result = diff
        if (result != null && !result.tooLarge) {
            renderDiff(result)
        } else {
            renderPlain(content)
        }
    }

    private fun renderDiff(result: DiffResult) {
        if (result.lines.isEmpty()) {
            binding.previewCode.text = ""
            return
        }
        val width = result.lines
            .mapNotNull { it.newLine ?: it.oldLine }
            .maxOrNull()
            ?.toString()
            ?.length
            ?: 1
        val builder = SpannableStringBuilder()
        result.lines.forEach { line ->
            val sign = when (line.kind) {
                DiffKind.SAME -> "  "
                DiffKind.ADDED -> "+ "
                DiffKind.REMOVED -> "- "
            }
            val number = (line.newLine ?: line.oldLine)
                ?.toString()
                .orEmpty()
                .padStart(width)
            val start = builder.length
            builder.append(sign).append(number).append(" │ ").append(line.text)
            if (line.kind != DiffKind.SAME) {
                builder.setSpan(
                    BackgroundColorSpan(diffBackground(line.kind)),
                    start,
                    builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            builder.append('\n')
        }
        binding.previewCode.text = builder
    }

    private fun renderPlain(content: String) {
        val lines = splitLines(content)
        if (lines.isEmpty()) {
            binding.previewCode.text = ""
            return
        }
        val width = lines.size.toString().length
        val builder = SpannableStringBuilder()
        lines.forEachIndexed { index, text ->
            builder.append((index + 1).toString().padStart(width))
                .append(" │ ")
                .append(text)
            if (index != lines.lastIndex) builder.append('\n')
        }
        binding.previewCode.text = builder
    }

    private fun diffBackground(kind: DiffKind): Int {
        val night = (resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return when (kind) {
            DiffKind.ADDED -> if (night) {
                Color.argb(48, 34, 197, 94)
            } else {
                Color.rgb(220, 252, 231)
            }

            DiffKind.REMOVED -> if (night) {
                Color.argb(48, 239, 68, 68)
            } else {
                Color.rgb(254, 226, 226)
            }

            DiffKind.SAME -> Color.TRANSPARENT
        }
    }

    private fun confirmRestore() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.version_restore_title, version.version))
            .setMessage(R.string.version_restore_confirm_message)
            .setPositiveButton(R.string.version_restore_action) { _, _ ->
                if (repository.restoreVersion(version.id)) {
                    setResult(RESULT_OK)
                    finish()
                } else {
                    Snackbar.make(
                        binding.root,
                        R.string.version_restore_failed,
                        Snackbar.LENGTH_LONG,
                    ).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun lineCount(text: String): Int = splitLines(text).size

    private fun splitLines(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        return text.replace("\r\n", "\n").split('\n')
    }
}
