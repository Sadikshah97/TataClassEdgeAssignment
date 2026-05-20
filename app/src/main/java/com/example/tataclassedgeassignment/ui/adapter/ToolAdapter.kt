// adapter/ToolAdapter.kt
package com.example.tataclassedgeassignment.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tataclassedgeassignment.R
import com.example.tataclassedgeassignment.model.ToolItem
import com.example.tataclassedgeassignment.model.ToolItemType

class ToolAdapter(
    private val items: List<ToolItem>,
    private val onToolClick: (ToolItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_TOOL = 0
        const val VIEW_TYPE_DIVIDER = 1
    }

    private var selectedId: String = "pen"

    override fun getItemViewType(position: Int): Int {
        return if (items[position].type == ToolItemType.DIVIDER)
            VIEW_TYPE_DIVIDER else VIEW_TYPE_TOOL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_DIVIDER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_tool_divider, parent, false)
            DividerViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_tool_button, parent, false)
            ToolViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ToolViewHolder) {
            val item = items[position]
            holder.bind(item, item.id == selectedId, onToolClick)
        }
    }

    override fun getItemCount() = items.size

    fun setSelected(id: String) {
        val old = items.indexOfFirst { it.id == selectedId }
        selectedId = id
        val new = items.indexOfFirst { it.id == id }
        if (old >= 0) notifyItemChanged(old)
        if (new >= 0) notifyItemChanged(new)
    }

    class ToolViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val btn: ImageButton = view.findViewById(R.id.toolIcon)
        private val label: TextView = view.findViewById(R.id.toolLabel)

        fun bind(item: ToolItem, isSelected: Boolean, onClick: (ToolItem) -> Unit) {
            btn.setImageResource(item.iconRes)
            label.text = item.label
            itemView.isSelected = isSelected
            itemView.setBackgroundResource(
                if (isSelected) R.drawable.tool_button_selected_bg
                else R.drawable.tool_button_bg
            )
            itemView.setOnClickListener { onClick(item) }
        }
    }

    class DividerViewHolder(view: View) : RecyclerView.ViewHolder(view)
}