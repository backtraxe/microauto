package com.microauto.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.microauto.R
import com.microauto.databinding.ActivityVersionsBinding
import com.microauto.databinding.DialogVersionNoteBinding
import com.microauto.script.ScriptRepository
import com.microauto.script.ScriptVersion

class VersionsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SCRIPT_ID = "script_id"
    }

    private lateinit var binding: ActivityVersionsBinding
    private lateinit var repository: ScriptRepository
    private lateinit var scriptId: String
    private lateinit var adapter: VersionsAdapter

    private val previewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            refresh()
            setResult(RESULT_OK)
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
        binding = ActivityVersionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdge.apply(this, binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.scriptName.text = meta.name
        adapter = VersionsAdapter(
            onOpen = { version -> openPreview(version) },
            onMenu = { version, anchor -> showMenu(version, anchor) },
        )
        binding.versionList.layoutManager = LinearLayoutManager(this)
        binding.versionList.adapter = adapter
        binding.btnPublishVersion.setOnClickListener { showPublishDialog() }
        refresh()
    }

    private fun refresh() {
        val versions = repository.versions(scriptId)
        adapter.submit(versions)
        binding.emptyState.isVisible = versions.isEmpty()
        binding.versionList.isVisible = versions.isNotEmpty()
        binding.versionCount.text = getString(R.string.version_count, versions.size)
    }

    private fun openPreview(version: ScriptVersion) {
        previewLauncher.launch(
            Intent(this, VersionPreviewActivity::class.java)
                .putExtra(VersionPreviewActivity.EXTRA_SCRIPT_ID, scriptId)
                .putExtra(VersionPreviewActivity.EXTRA_VERSION_ID, version.id)
        )
    }

    private fun showPublishDialog() {
        val dialogBinding = DialogVersionNoteBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_publish)
            .setView(dialogBinding.root)
            .setPositiveButton("发布") { _, _ ->
                publishCurrentVersion(dialogBinding.versionNoteInput.text.toString())
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun publishCurrentVersion(note: String) {
        if (repository.find(scriptId) == null) {
            Snackbar.make(
                binding.root,
                R.string.version_script_missing,
                Snackbar.LENGTH_LONG,
            ).show()
            return
        }
        val content = repository.read(scriptId)
        val version = repository.publishVersion(scriptId, note)
        if (version == null) {
            Snackbar.make(binding.root, R.string.version_publish_failed, Snackbar.LENGTH_LONG)
                .show()
            return
        }
        refresh()
        toast(
            if (content.isNullOrEmpty()) {
                getString(R.string.version_publish_empty, version.version)
            } else {
                getString(R.string.version_publish_success, version.version)
            }
        )
    }

    private fun showMenu(version: ScriptVersion, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "恢复此版本")
        popup.menu.add(0, 2, 1, "删除此版本")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    confirmRestore(version)
                    true
                }

                2 -> {
                    confirmDelete(version)
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    private fun confirmRestore(version: ScriptVersion) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.version_restore_title, version.version))
            .setMessage(R.string.version_restore_confirm_message)
            .setPositiveButton(R.string.version_restore_action) { _, _ ->
                if (repository.restoreVersion(version.id)) {
                    toast(getString(R.string.version_restore_success, version.version))
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

    private fun confirmDelete(version: ScriptVersion) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除 v${version.version}")
            .setMessage("删除后不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                if (repository.deleteVersion(version.id)) {
                    refresh()
                    toast("已删除 v${version.version}")
                } else {
                    toast("删除失败")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
