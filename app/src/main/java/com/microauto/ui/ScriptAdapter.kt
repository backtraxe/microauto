package com.microauto.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.microauto.databinding.ItemScriptBinding
import com.microauto.script.ScriptMeta

class ScriptAdapter(
    private val onClick: (ScriptMeta) -> Unit,
    private val onMenu: (ScriptMeta, View) -> Unit,
    private val subtitle: (ScriptMeta) -> String,
    private val schedule: (ScriptMeta) -> String?
) : RecyclerView.Adapter<ScriptAdapter.Holder>() {

    private var items: List<ScriptMeta> = emptyList()

    fun submit(list: List<ScriptMeta>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemScriptBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class Holder(
        private val binding: ItemScriptBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(meta: ScriptMeta) {
            binding.nameText.text = meta.name
            binding.metaText.text = subtitle(meta)
            val scheduleText = schedule(meta)
            binding.scheduleChip.isVisible = scheduleText != null
            binding.scheduleChip.text = scheduleText.orEmpty()
            binding.root.setOnClickListener { onClick(meta) }
            binding.moreButton.setOnClickListener { onMenu(meta, it) }
        }
    }
}
