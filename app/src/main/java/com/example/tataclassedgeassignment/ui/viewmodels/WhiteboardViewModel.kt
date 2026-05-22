package com.example.tataclassedgeassignment.ui.viewmodels

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tataclassedgeassignment.domain.repository.WhiteboardRepository
import com.example.tataclassedgeassignment.domain.usecase.LoadWhiteboardUseCase
import com.example.tataclassedgeassignment.domain.usecase.SaveWhiteboardUseCase
import com.example.tataclassedgeassignment.model.ShapeModel
import com.example.tataclassedgeassignment.model.StrokeModel
import com.example.tataclassedgeassignment.model.TextModel
import com.example.tataclassedgeassignment.model.WhiteboardData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WhiteboardViewModel @Inject constructor(
    private val saveWhiteboardUseCase: SaveWhiteboardUseCase,
    private val loadWhiteboardUseCase: LoadWhiteboardUseCase,
    private val repository: WhiteboardRepository
) : ViewModel() {
    private val erasedStrokeIds = mutableSetOf<String>()

    val _undoSignal = MutableStateFlow(0)
    val undoSignal: StateFlow<Int> = _undoSignal.asStateFlow()
    private val erasedIds = mutableSetOf<String>()

    private val _toolState = MutableStateFlow(ToolState())
    val toolState: StateFlow<ToolState> = _toolState.asStateFlow()

    private val _strokes = MutableStateFlow<List<StrokeModel>>(emptyList())
    val strokes: StateFlow<List<StrokeModel>> = _strokes.asStateFlow()

    private val _shapes = MutableStateFlow<List<ShapeModel>>(emptyList())
    val shapes: StateFlow<List<ShapeModel>> = _shapes.asStateFlow()

    private val _texts = MutableStateFlow<List<TextModel>>(emptyList())
    val texts: StateFlow<List<TextModel>> = _texts.asStateFlow()

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    private val _savedFiles = MutableStateFlow<List<String>>(emptyList())
    val savedFiles: StateFlow<List<String>> = _savedFiles.asStateFlow()

    // Undo/Redo stacks — each entry is a full snapshot
    private val undoStack = ArrayDeque<Triple<List<StrokeModel>, List<ShapeModel>, List<TextModel>>>()
    private val redoStack = ArrayDeque<Triple<List<StrokeModel>, List<ShapeModel>, List<TextModel>>>()

    fun setTool(tool: DrawingTool) {
        _toolState.value = _toolState.value.copy(activeTool = tool)
    }

    fun setStrokeColor(color: Int) {
        _toolState.value = _toolState.value.copy(strokeColor = color)
    }

    fun setStrokeWidth(width: Float) {
        _toolState.value = _toolState.value.copy(strokeWidth = width)
    }

    fun addStroke(stroke: StrokeModel) {
        pushUndoSnapshot()
        _strokes.value = _strokes.value + stroke
        redoStack.clear()
    }

    fun addShape(shape: ShapeModel) {
        pushUndoSnapshot()
        _shapes.value = _shapes.value + shape
        redoStack.clear()
    }

    fun addText(text: TextModel) {
        pushUndoSnapshot()
        _texts.value = _texts.value + text
        redoStack.clear()
    }
    fun updateTextAt(index: Int, updated: TextModel) {
        pushUndoSnapshot()
        val list = _texts.value.toMutableList()
        if (index in list.indices) {
            list[index] = updated
            _texts.value = list
        }
    }
    fun moveTextAt(index: Int, newX: Float, newY: Float) {
        val list = _texts.value.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(positionX = newX, positionY = newY)
            _texts.value = list
        }
    }
    fun exportAsPng(view: View, context: Context) {
        viewModelScope.launch {
            try {
                val bitmap = Bitmap.createBitmap(
                    view.width, view.height, Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                view.draw(canvas)

                val fileName = "whiteboard_${
                    java.text.SimpleDateFormat("yyyyMMdd_HHmmss",
                        java.util.Locale.getDefault()).format(java.util.Date())
                }.png"

                val file = java.io.File(
                    context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES),
                    fileName
                )
                val out = java.io.FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
                out.close()

                _saveMessage.value = "Exported: $fileName"
            } catch (e: Exception) {
                _saveMessage.value = "Export failed: ${e.message}"
            }
        }
    }


    /*fun removeShapesInArea(x: Float, y: Float, radius: Float) {
        // Shapes
        val erasedShapes = _shapes.value.filter { isShapeTouchedByEraser(it, x, y, radius) }
        erasedIds.addAll(erasedShapes.map { it.id })
        _shapes.value = _shapes.value.filter { it.id !in erasedIds }

        // Texts
        val erasedTexts = _texts.value.filter { isTextTouchedByEraser(it, x, y, radius) }
        erasedIds.addAll(erasedTexts.map { it.id })
        _texts.value = _texts.value.filter { it.id !in erasedIds }

        // ✅ Strokes bhi remove karo
        val erasedStrokes = _strokes.value.filter { isStrokeTouchedByEraser(it, x, y, radius) }
        erasedIds.addAll(erasedStrokes.map { it.id })
        _strokes.value = _strokes.value.filter { it.id !in erasedIds }
    }*/

    fun removeShapesInArea(x: Float, y: Float, radius: Float) {
        // ✅ Shapes → poora remove (touch pe)
        val erasedShapes = _shapes.value.filter { isShapeTouchedByEraser(it, x, y, radius) }
        erasedIds.addAll(erasedShapes.map { it.id })
        _shapes.value = _shapes.value.filter { it.id !in erasedIds }

        // ✅ Texts → poora remove (touch pe)
        val erasedTexts = _texts.value.filter { isTextTouchedByEraser(it, x, y, radius) }
        erasedIds.addAll(erasedTexts.map { it.id })
        _texts.value = _texts.value.filter { it.id !in erasedIds }

        // ✅ Strokes → list se mat hatao
        // Sirf bitmap pe pixel erase hoga (WhiteboardView handle karega)
    }
    fun beginErase() {
        pushUndoSnapshot()
        redoStack.clear()
    }

    private fun isShapeTouchedByEraser(shape: ShapeModel, ex: Float, ey: Float, radius: Float): Boolean {
        return when (shape.type) {
            "rectangle" -> {
                val left   = minOf(shape.startX, shape.endX)
                val right  = maxOf(shape.startX, shape.endX)
                val top    = minOf(shape.startY, shape.endY)
                val bottom = maxOf(shape.startY, shape.endY)

                // Check proximity to each of the 4 edges (not filled area)
                val nearLeftEdge   = ex in (left - radius)..(left + radius)   && ey in top..bottom
                val nearRightEdge  = ex in (right - radius)..(right + radius)  && ey in top..bottom
                val nearTopEdge    = ey in (top - radius)..(top + radius)      && ex in left..right
                val nearBottomEdge = ey in (bottom - radius)..(bottom + radius) && ex in left..right

                nearLeftEdge || nearRightEdge || nearTopEdge || nearBottomEdge
            }

            "circle" -> {
                val cx = (shape.startX + shape.endX) / 2
                val cy = (shape.startY + shape.endY) / 2
                val rx = Math.abs(shape.endX - shape.startX) / 2
                val ry = Math.abs(shape.endY - shape.startY) / 2
                // Distance from eraser to ellipse boundary
                val normalizedDist = Math.sqrt(
                    ((ex - cx) * (ex - cx) / (rx * rx) +
                            (ey - cy) * (ey - cy) / (ry * ry)).toDouble()
                )
                normalizedDist in 0.7..1.3
            }

            "line" -> {
                val dx  = shape.endX - shape.startX
                val dy  = shape.endY - shape.startY
                val len = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                if (len == 0f) return false
                val t = (((ex - shape.startX) * dx + (ey - shape.startY) * dy) / (len * len))
                    .coerceIn(0f, 1f)
                val nearX = shape.startX + t * dx
                val nearY = shape.startY + t * dy
                Math.sqrt(((ex - nearX) * (ex - nearX) + (ey - nearY) * (ey - nearY)).toDouble()) < radius
            }

            "polygon" -> {
                val cx     = (shape.startX + shape.endX) / 2
                val cy     = (shape.startY + shape.endY) / 2
                val radius2 = minOf(
                    Math.abs(shape.endX - shape.startX),
                    Math.abs(shape.endY - shape.startY)
                ) / 2
                val sides  = 5
                // Check proximity to each polygon edge
                var touched = false
                for (i in 0 until sides) {
                    val angle1 = (2.0 * Math.PI * i / sides - Math.PI / 2)
                    val angle2 = (2.0 * Math.PI * (i + 1) / sides - Math.PI / 2)
                    val x1 = cx + radius2 * Math.cos(angle1).toFloat()
                    val y1 = cy + radius2 * Math.sin(angle1).toFloat()
                    val x2 = cx + radius2 * Math.cos(angle2).toFloat()
                    val y2 = cy + radius2 * Math.sin(angle2).toFloat()

                    // Distance from eraser point to this polygon edge
                    val edgeDx  = x2 - x1
                    val edgeDy  = y2 - y1
                    val edgeLen = Math.sqrt((edgeDx * edgeDx + edgeDy * edgeDy).toDouble()).toFloat()
                    if (edgeLen == 0f) continue
                    val t = (((ex - x1) * edgeDx + (ey - y1) * edgeDy) / (edgeLen * edgeLen))
                        .coerceIn(0f, 1f)
                    val nearX = x1 + t * edgeDx
                    val nearY = y1 + t * edgeDy
                    val dist  = Math.sqrt(((ex - nearX) * (ex - nearX) + (ey - nearY) * (ey - nearY)).toDouble())
                    if (dist < radius) { touched = true; break }
                }
                touched
            }
            else -> false
        }
    }
    private fun isStrokeTouchedByEraser(stroke: StrokeModel, ex: Float, ey: Float, radius: Float): Boolean {
        return stroke.points.any { point ->
            val dx = ex - point[0]
            val dy = ey - point[1]
            Math.sqrt((dx * dx + dy * dy).toDouble()) < radius
        }
    }

    private fun isTextTouchedByEraser(text: TextModel, ex: Float, ey: Float, radius: Float): Boolean {
        return ex > text.positionX - radius &&
                ex < text.positionX + 200 + radius &&
                ey > text.positionY - text.size - radius &&
                ey < text.positionY + radius
    }
    fun deleteTextAt(index: Int) {
        pushUndoSnapshot()
        val list = _texts.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _texts.value = list
        }
    }
    fun updateStrokes(strokes: List<StrokeModel>) {
        _strokes.value = strokes
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        erasedIds.clear()
        redoStack.addLast(Triple(_strokes.value, _shapes.value, _texts.value))
        val (s, sh, t) = undoStack.removeLast()
        _strokes.value = s
        _shapes.value = sh
        _texts.value = t
        _undoSignal.value++
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        erasedIds.clear()
        undoStack.addLast(Triple(_strokes.value, _shapes.value, _texts.value))
        val (s, sh, t) = redoStack.removeLast()
        _strokes.value = s
        _shapes.value = sh
        _texts.value = t
        _undoSignal.value++
    }
    fun clearCanvas() {
        pushUndoSnapshot()
        erasedIds.clear()
        erasedStrokeIds.clear()
        _strokes.value = emptyList()
        _shapes.value = emptyList()
        _texts.value = emptyList()
        redoStack.clear()
    }

    private fun pushUndoSnapshot() {
        undoStack.addLast(Triple(_strokes.value, _shapes.value, _texts.value))
        if (undoStack.size > 50) undoStack.removeFirst()
    }

    fun saveWhiteboard() {
        viewModelScope.launch {
            val data = WhiteboardData(_strokes.value, _shapes.value, _texts.value)
            val fileName = saveWhiteboardUseCase(data)
            _saveMessage.value = "Saved: $fileName"
        }
    }

    fun loadWhiteboard(fileName: String) {
        viewModelScope.launch {
            val data = loadWhiteboardUseCase(fileName) ?: return@launch
            pushUndoSnapshot()
            _strokes.value = data.strokes
            _shapes.value = data.shapes
            _texts.value = data.texts
        }
    }

    fun refreshSavedFiles() {
        _savedFiles.value = repository.listSavedWhiteboards()
    }

    fun clearSaveMessage() {
        _saveMessage.value = null
    }

}