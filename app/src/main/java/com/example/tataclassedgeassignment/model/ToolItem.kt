package com.example.tataclassedgeassignment.model

// models/ToolItem.kt

data class ToolItem(
    val id: String,
    val iconRes: Int,
    val label: String,
    val type: ToolItemType
)

enum class ToolItemType {
    TOOL, DIVIDER, ACTION
}