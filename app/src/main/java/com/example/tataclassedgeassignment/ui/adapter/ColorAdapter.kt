// adapter/ColorAdapter.kt
package com.example.tataclassedgeassignment.ui.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.tataclassedgeassignment.R

class ColorAdapter(
    private val colors: List<Int>,
    private val onColorClick: (Int) -> Unit
) : RecyclerView.Adapter<ColorAdapter.ColorViewHolder>() {

    private var selectedIndex = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_color_swatch, parent, false)
        return ColorViewHolder(view)
    }

    override fun onBindViewHolder(holder: ColorViewHolder, @SuppressLint("RecyclerView") position: Int) {
        holder.bind(colors[position], position == selectedIndex) { color ->
            val old = selectedIndex
            selectedIndex = position
            notifyItemChanged(old)
            notifyItemChanged(position)
            onColorClick(color)
        }
    }

    override fun getItemCount() = colors.size

    class ColorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val swatchView: View = view.findViewById(R.id.colorSwatchView)
        private val selectedRing: View = view.findViewById(R.id.selectedRing)

        fun bind(color: Int, isSelected: Boolean, onClick: (Int) -> Unit) {
            swatchView.setBackgroundColor(color)
            selectedRing.visibility = if (isSelected) View.VISIBLE else View.GONE
            itemView.setOnClickListener { onClick(color) }
        }
    }
}