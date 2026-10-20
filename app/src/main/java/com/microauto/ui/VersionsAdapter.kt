package com.microauto.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.microauto.databinding.ItemVersionBinding
import com.microauto.script.ScriptVersion
import java.text.DateFormat
import java.util.Date

class VersionsAdapter(
    private val onMenu: (ScriptVersion, View) -> Unit
) : RecyclerView.Adapter<VersionsAdapter.Holder>() {

    private var items: List<ScriptVersion> = emptyList()

    fun submit(list: List<ScriptVersion>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemVersionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class Holder(
        private val binding: ItemVersionBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(version: ScriptVersion) {
            binding.versionLabel.text = "v${version.version}"
            binding.versionNote.text = version.note.ifBlank { "无备注" }
            binding.versionTime.text = DateFormat
                .getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(version.createdAt))
            binding.versionMore.setOnClickListener { onMenu(version, it) }
        }
    }
}
