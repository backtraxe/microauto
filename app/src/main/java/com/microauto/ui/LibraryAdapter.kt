package com.microauto.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.microauto.R
import com.microauto.core.DateFormats
import com.microauto.databinding.ItemFolderBinding
import com.microauto.databinding.ItemScriptBinding
import com.microauto.script.FolderMeta
import com.microauto.script.ScriptMeta

sealed class LibraryItem {
    data class Folder(val meta: FolderMeta, val count: Int) : LibraryItem()
    data class Script(val meta: ScriptMeta) : LibraryItem()
}

class LibraryAdapter(
    private val onFolderClick: (FolderMeta) -> Unit,
    private val onFolderMenu: (FolderMeta, View) -> Unit,
    private val onScriptClick: (ScriptMeta) -> Unit,
    private val onScriptMenu: (ScriptMeta, View) -> Unit,
    private val onScriptRun: (ScriptMeta) -> Unit,
    private val schedule: (ScriptMeta) -> String?
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<LibraryItem> = emptyList()
    private var running = false
    private var runningScriptId: String? = null

    fun submit(list: List<LibraryItem>) {
        val diff = DiffUtil.calculateDiff(DiffCallback(items, list))
        items = list
        diff.dispatchUpdatesTo(this)
    }

    fun setRunState(running: Boolean, activeScriptId: String?) {
        if (this.running == running && runningScriptId == activeScriptId) return
        val wasRunning = this.running
        val previousId = runningScriptId
        this.running = running
        runningScriptId = activeScriptId
        if (wasRunning != running) {
            notifyItemRangeChanged(0, itemCount)
        } else {
            indexOfScript(previousId)?.let { notifyItemChanged(it) }
            indexOfScript(activeScriptId)?.let { notifyItemChanged(it) }
        }
    }

    private fun indexOfScript(id: String?): Int? {
        if (id == null) return null
        val index = items.indexOfFirst { it is LibraryItem.Script && it.meta.id == id }
        return index.takeIf { it >= 0 }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int =
        if (items[position] is LibraryItem.Folder) TYPE_FOLDER else TYPE_SCRIPT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_FOLDER) {
            FolderHolder(ItemFolderBinding.inflate(inflater, parent, false))
        } else {
            ScriptHolder(ItemScriptBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is LibraryItem.Folder -> (holder as FolderHolder).bind(item)
            is LibraryItem.Script -> (holder as ScriptHolder).bind(item.meta)
        }
    }

    private inner class FolderHolder(
        private val binding: ItemFolderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: LibraryItem.Folder) {
            binding.folderName.text = item.meta.name
            binding.folderCount.text = binding.root.context.getString(
                com.microauto.R.string.folder_script_count,
                item.count
            )
            binding.root.setOnClickListener { onFolderClick(item.meta) }
            binding.folderMore.contentDescription = "「${item.meta.name}」的文件夹操作"
            binding.folderMore.setOnClickListener { onFolderMenu(item.meta, it) }
        }
    }

    private inner class ScriptHolder(
        private val binding: ItemScriptBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(meta: ScriptMeta) {
            val context = binding.root.context
            binding.nameText.text = meta.name
            val tileName = meta.name.trim().replaceFirst(Regex("^示例[：:]\\s*"), "")
            binding.iconTile.text = tileName.take(1).ifEmpty { "?" }
            binding.iconTile.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.nd_indigo_soft)
            )
            binding.iconTile.setTextColor(ContextCompat.getColor(context, R.color.nd_indigo_text))

            val scheduleText = schedule(meta)
            binding.scheduleChip.text = scheduleText
                ?: context.getString(R.string.script_chip_manual)
            binding.scheduleChip.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.nd_indigo_soft)
            )
            binding.scheduleChip.setTextColor(
                ContextCompat.getColor(context, R.color.nd_indigo_text)
            )

            val isThisRunning = running && runningScriptId == meta.id
            binding.statusChip.text = if (isThisRunning) {
                context.getString(R.string.script_chip_running)
            } else {
                DateFormats.formatMonthDay(meta.updatedAt)
            }
            val statusBackground = if (isThisRunning) {
                R.color.nd_green_soft
            } else {
                null
            }
            val statusForeground = if (isThisRunning) {
                R.color.nd_green
            } else {
                null
            }
            binding.statusChip.backgroundTintList = ColorStateList.valueOf(
                if (statusBackground != null) {
                    ContextCompat.getColor(context, statusBackground)
                } else {
                    MaterialColors.getColor(
                        binding.statusChip,
                        com.google.android.material.R.attr.colorSurfaceVariant,
                    )
                }
            )
            binding.statusChip.setTextColor(
                if (statusForeground != null) {
                    ContextCompat.getColor(context, statusForeground)
                } else {
                    ContextCompat.getColor(context, R.color.nd_chip_text)
                }
            )

            binding.btnRunScript.isVisible = !isThisRunning
            binding.btnRunScript.isEnabled = !running
            binding.btnRunScript.contentDescription = when {
                isThisRunning -> "「${meta.name}」正在运行"
                running -> "已有脚本在运行"
                else -> "运行「${meta.name}」"
            }
            binding.runSpinner.isVisible = isThisRunning
            binding.runSpinner.contentDescription = if (isThisRunning) {
                "「${meta.name}」正在运行"
            } else {
                null
            }
            binding.root.setOnClickListener { onScriptClick(meta) }
            binding.btnRunScript.setOnClickListener { onScriptRun(meta) }
            binding.moreButton.contentDescription = "「${meta.name}」的更多操作"
            binding.moreButton.setOnClickListener { onScriptMenu(meta, it) }
        }
    }

    private class DiffCallback(
        private val oldItems: List<LibraryItem>,
        private val newItems: List<LibraryItem>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldItems.size

        override fun getNewListSize(): Int = newItems.size

        override fun areItemsTheSame(oldPosition: Int, newPosition: Int): Boolean {
            val oldItem = oldItems[oldPosition]
            val newItem = newItems[newPosition]
            return when {
                oldItem is LibraryItem.Folder && newItem is LibraryItem.Folder ->
                    oldItem.meta.id == newItem.meta.id

                oldItem is LibraryItem.Script && newItem is LibraryItem.Script ->
                    oldItem.meta.id == newItem.meta.id

                else -> false
            }
        }

        override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean =
            oldItems[oldPosition] == newItems[newPosition]
    }

    private companion object {
        const val TYPE_FOLDER = 0
        const val TYPE_SCRIPT = 1
    }
}
