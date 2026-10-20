package com.microauto.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.microauto.R
import com.microauto.databinding.ActivityVersionsBinding
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
        adapter = VersionsAdapter { version, anchor -> showMenu(version, anchor) }
        binding.versionList.layoutManager = LinearLayoutManager(this)
        binding.versionList.adapter = adapter
        refresh()
    }

    private fun refresh() {
        val versions = repository.versions(scriptId)
        adapter.submit(versions)
        binding.emptyState.isVisible = versions.isEmpty()
        binding.versionList.isVisible = versions.isNotEmpty()
        binding.versionCount.text = getString(R.string.version_count, versions.size)
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
            .setTitle("恢复 v${version.version}")
            .setMessage("当前脚本内容会被覆盖。")
            .setPositiveButton("恢复") { _, _ ->
                if (repository.restoreVersion(version.id)) {
                    toast("已恢复 v${version.version}")
                    setResult(RESULT_OK)
                    finish()
                } else {
                    toast("恢复失败")
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
