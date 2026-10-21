package com.microauto.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.microauto.core.DateFormats
import com.microauto.databinding.ItemVersionBinding
import com.microauto.script.ScriptVersion

class VersionsAdapter(
    private val onOpen: (ScriptVersion) -> Unit,
    private val onMenu: (ScriptVersion, View) -> Unit,
) : RecyclerView.Adapter<VersionsAdapter.Holder>() {

    private var items: List<ScriptVersion> = emptyList()

    fun submit(list: List<ScriptVersion>) {
        val diff = DiffUtil.calculateDiff(DiffCallback(items, list))
        items = list
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemVersionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    inner class Holder(
        private val binding: ItemVersionBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(version: ScriptVersion) {
            binding.versionLabel.text = binding.root.context.getString(
                com.microauto.R.string.version_label,
                version.version
            )
            binding.versionNote.text = version.note.ifBlank {
                binding.root.context.getString(com.microauto.R.string.version_note_empty)
            }
            binding.versionTime.text = DateFormats.format(version.createdAt)
            binding.versionMore.contentDescription = "v${version.version} 的版本操作"
            binding.root.setOnClickListener { onOpen(version) }
            binding.versionMore.setOnClickListener { onMenu(version, it) }
        }
    }

    private class DiffCallback(
        private val oldItems: List<ScriptVersion>,
        private val newItems: List<ScriptVersion>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldItems.size

        override fun getNewListSize(): Int = newItems.size

        override fun areItemsTheSame(oldPosition: Int, newPosition: Int): Boolean =
            oldItems[oldPosition].id == newItems[newPosition].id

        override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean =
            oldItems[oldPosition] == newItems[newPosition]
    }
}
