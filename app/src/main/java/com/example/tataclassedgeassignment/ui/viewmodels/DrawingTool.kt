package com.example.tataclassedgeassignment.ui.viewmodels

enum class DrawingTool {
    PEN, ERASER, RECTANGLE, CIRCLE, LINE, POLYGON, TEXT, SELECTOR
}

data class ToolState(
    val activeTool: DrawingTool = DrawingTool.PEN,
    val strokeColor: Int = android.graphics.Color.BLACK,
    val strokeWidth: Float = 5f,
    val textSize: Float = 40f,
    val textColor: Int = android.graphics.Color.BLACK
)