package com.microauto.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
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
    private val subtitle: (ScriptMeta) -> String,
    private val schedule: (ScriptMeta) -> String?
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<LibraryItem> = emptyList()

    fun submit(list: List<LibraryItem>) {
        val diff = DiffUtil.calculateDiff(DiffCallback(items, list))
        items = list
        diff.dispatchUpdatesTo(this)
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
            binding.folderMore.setOnClickListener { onFolderMenu(item.meta, it) }
        }
    }

    private inner class ScriptHolder(
        private val binding: ItemScriptBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(meta: ScriptMeta) {
            binding.nameText.text = meta.name
            val scheduleText = schedule(meta)
            binding.metaText.text = if (scheduleText != null) {
                "$scheduleText · ${subtitle(meta)}"
            } else {
                subtitle(meta)
            }
            binding.root.setOnClickListener { onScriptClick(meta) }
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
