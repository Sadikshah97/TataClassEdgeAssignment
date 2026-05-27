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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@HiltViewModel
class WhiteboardViewModel @Inject constructor(
    private val saveWhiteboardUseCase: SaveWhiteboardUseCase,
    private val loadWhiteboardUseCase: LoadWhiteboardUseCase,
    private val repository: WhiteboardRepository
) : ViewModel() {

    // ─── Data classes ─────────────────────────────────────────────
    data class EraserStroke(val points: List<Pair<Float, Float>>, val width: Float)
    data class Quad(
        val strokes: List<StrokeModel>,
        val shapes: List<ShapeModel>,
        val texts: List<TextModel>,
    )
    private val _erasedStrokeIds = MutableStateFlow<Set<String>>(emptySet())
    private val erasedIds = mutableSetOf<String>()

    private val _undoSignal = MutableStateFlow(0)


    private val _toolState = MutableStateFlow(ToolState())
    val toolState: StateFlow<ToolState> = _toolState.asStateFlow()

    private val _strokes = MutableStateFlow<List<StrokeModel>>(emptyList())
    val strokes: StateFlow<List<StrokeModel>> = _strokes.asStateFlow()

    private val _shapes = MutableStateFlow<List<ShapeModel>>(emptyList())
    val shapes: StateFlow<List<ShapeModel>> = _shapes.asStateFlow()

    private val _texts = MutableStateFlow<List<TextModel>>(emptyList())
    val texts: StateFlow<List<TextModel>> = _texts.asStateFlow()

    private val _eraserStrokes = MutableStateFlow<List<EraserStroke>>(emptyList())
    val eraserStrokes: StateFlow<List<EraserStroke>> = _eraserStrokes.asStateFlow()

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    private val _savedFiles = MutableStateFlow<List<String>>(emptyList())
    val savedFiles: StateFlow<List<String>> = _savedFiles.asStateFlow()

    private val undoStack = ArrayDeque<Quad>()
    private val redoStack = ArrayDeque<Quad>()

    // ─── Tool ─────────────────────────────────────────────────────
    fun setTool(tool: DrawingTool) {
        _toolState.value = _toolState.value.copy(activeTool = tool)
    }

    fun setStrokeColor(color: Int) {
        _toolState.value = _toolState.value.copy(strokeColor = color)
    }

    fun setStrokeWidth(width: Float) {
        _toolState.value = _toolState.value.copy(strokeWidth = width)
    }

    // ─── Drawing ──────────────────────────────────────────────────
    fun addStroke(stroke: StrokeModel) {
        pushUndoSnapshot()
        _strokes.value += stroke
        redoStack.clear()
    }

    fun addShape(shape: ShapeModel) {
        pushUndoSnapshot()
        _shapes.value += shape
        redoStack.clear()
    }

    fun addText(text: TextModel) {
        pushUndoSnapshot()
        _texts.value += text
        redoStack.clear()
    }



    // ─── Text ─────────────────────────────────────────────────────
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

    fun deleteTextAt(index: Int) {
        pushUndoSnapshot()
        val list = _texts.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _texts.value = list
        }
    }

    // ─── Eraser ───────────────────────────────────────────────────
    fun beginErase() {
        pushUndoSnapshot()
        redoStack.clear()
    }

    fun removeShapesInArea(x: Float, y: Float, radius: Float) {
        // Shapes — poora erase
        val erasedShapes = _shapes.value.filter { isShapeTouchedByEraser(it, x, y, radius) }
        if (erasedShapes.isNotEmpty()) {
            erasedIds.addAll(erasedShapes.map { it.id })
            _shapes.value = _shapes.value.filter { it.id !in erasedIds }
        }

        // Texts — poora erase
        val erasedTexts = _texts.value.filter { isTextTouchedByEraser(it, x, y, radius) }
        if (erasedTexts.isNotEmpty()) {
            erasedIds.addAll(erasedTexts.map { it.id })
            _texts.value = _texts.value.filter { it.id !in erasedIds }
        }

        // ✅ Strokes — pixel based split erase
        val newStrokes = mutableListOf<StrokeModel>()

        _strokes.value.forEach { stroke ->
            val segments = mutableListOf<MutableList<List<Float>>>()
            var currentSegment = mutableListOf<List<Float>>()

            stroke.points.forEach { point ->
                val dx = x - point[0]
                val dy = y - point[1]
                val dist = kotlin.math.sqrt((dx * dx + dy * dy).toDouble())

                if (dist < radius) {
                    // ✅ Yeh point eraser ke andar hai — segment tod do
                    if (currentSegment.size >= 2) {
                        segments.add(currentSegment)
                    }
                    currentSegment = mutableListOf()
                } else {
                    // ✅ Yeh point safe hai — segment mein add karo
                    currentSegment.add(point)
                }
            }

            // Last segment save karo
            if (currentSegment.size >= 2) {
                segments.add(currentSegment)
            }

            // ✅ Har segment ek alag stroke ban jayega
            segments.forEach { seg ->
                newStrokes.add(
                    StrokeModel(
                        points   = seg,
                        color    = stroke.color,
                        width    = stroke.width,
                        isEraser = false
                    )
                )
            }
        }

        _strokes.value = newStrokes
    }    fun undo() {
        if (undoStack.isEmpty()) return
        erasedIds.clear()
        _erasedStrokeIds.value = emptySet() // ✅
        redoStack.addLast(currentSnapshot())
        val snap = undoStack.removeLast()
        restoreSnapshot(snap)
        _undoSignal.value++
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        erasedIds.clear()
        _erasedStrokeIds.value = emptySet() // ✅
        undoStack.addLast(currentSnapshot())
        val snap = redoStack.removeLast()
        restoreSnapshot(snap)
        _undoSignal.value++
    }

    // ─── Clear ────────────────────────────────────────────────────
    fun clearCanvas() {
        pushUndoSnapshot()
        erasedIds.clear()
        _erasedStrokeIds.value = emptySet() // ✅
        _strokes.value = emptyList()
        _shapes.value = emptyList()
        _texts.value = emptyList()
        _eraserStrokes.value = emptyList()
        redoStack.clear()
    }

    // ─── Snapshot helpers ─────────────────────────────────────────
    private fun currentSnapshot() = Quad(
        _strokes.value,
        _shapes.value,
        _texts.value,
       // _eraserStrokes.value
    )

    private fun restoreSnapshot(snap: Quad) {
        _strokes.value = snap.strokes
        _shapes.value = snap.shapes
        _texts.value = snap.texts
       // _eraserStrokes.value = snap.eraserStrokes
    }

    private fun pushUndoSnapshot() {
        undoStack.addLast(currentSnapshot())
        if (undoStack.size > 50) undoStack.removeFirst()
    }

    // ─── Save/Load/Export ─────────────────────────────────────────
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
            _eraserStrokes.value = emptyList()
        }
    }

    fun exportAsPng(view: View, context: Context) {
        viewModelScope.launch {
            try {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
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
                java.io.FileOutputStream(file).use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                _saveMessage.value = "Exported: $fileName"
            } catch (e: Exception) {
                _saveMessage.value = "Export failed: ${e.message}"
            }
        }
    }

    fun refreshSavedFiles() {
        _savedFiles.value = repository.listSavedWhiteboards()
    }

    fun clearSaveMessage() {
        _saveMessage.value = null
    }

    // ─── Hit detection ────────────────────────────────────────────
    private fun isShapeTouchedByEraser(shape: ShapeModel, ex: Float, ey: Float, radius: Float): Boolean {
        return when (shape.type) {
            "rectangle" -> {
                val left   = minOf(shape.startX, shape.endX)
                val right  = maxOf(shape.startX, shape.endX)
                val top    = minOf(shape.startY, shape.endY)
                val bottom = maxOf(shape.startY, shape.endY)
                val nearLeft   = ex in (left - radius)..(left + radius) && ey in top..bottom
                val nearRight  = ex in (right - radius)..(right + radius) && ey in top..bottom
                val nearTop    = ey in (top - radius)..(top + radius) && ex in left..right
                val nearBottom = ey in (bottom - radius)..(bottom + radius) && ex in left..right
                nearLeft || nearRight || nearTop || nearBottom
            }
            "circle" -> {
                val cx = (shape.startX + shape.endX) / 2
                val cy = (shape.startY + shape.endY) / 2
                val rx = abs(shape.endX - shape.startX) / 2
                val ry = abs(shape.endY - shape.startY) / 2
                val normalizedDist = sqrt(
                    ((ex - cx) * (ex - cx) / (rx * rx) +
                            (ey - cy) * (ey - cy) / (ry * ry)).toDouble()
                )
                normalizedDist in 0.7..1.3
            }
            "line" -> {
                val dx  = shape.endX - shape.startX
                val dy  = shape.endY - shape.startY
                val len = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                if (len == 0f) return false
                val t = (((ex - shape.startX) * dx + (ey - shape.startY) * dy) / (len * len))
                    .coerceIn(0f, 1f)
                val nearX = shape.startX + t * dx
                val nearY = shape.startY + t * dy
                sqrt(((ex - nearX) * (ex - nearX) + (ey - nearY) * (ey - nearY)).toDouble()) < radius
            }
            "polygon" -> {
                val cx      = (shape.startX + shape.endX) / 2
                val cy      = (shape.startY + shape.endY) / 2
                val radius2 = minOf(abs(shape.endX - shape.startX), abs(shape.endY - shape.startY)) / 2
                val sides   = 5
                var touched = false
                for (i in 0 until sides) {
                    val angle1  = (2.0 * Math.PI * i / sides - Math.PI / 2)
                    val angle2  = (2.0 * Math.PI * (i + 1) / sides - Math.PI / 2)
                    val x1      = cx + radius2 * cos(angle1).toFloat()
                    val y1      = cy + radius2 * sin(angle1).toFloat()
                    val x2      = cx + radius2 * cos(angle2).toFloat()
                    val y2      = cy + radius2 * sin(angle2).toFloat()
                    val edgeDx  = x2 - x1
                    val edgeDy  = y2 - y1
                    val edgeLen = sqrt((edgeDx * edgeDx + edgeDy * edgeDy).toDouble()).toFloat()
                    if (edgeLen == 0f) continue
                    val t       = (((ex - x1) * edgeDx + (ey - y1) * edgeDy) / (edgeLen * edgeLen)).coerceIn(0f, 1f)
                    val nearX   = x1 + t * edgeDx
                    val nearY   = y1 + t * edgeDy
                    val dist    = sqrt(((ex - nearX) * (ex - nearX) + (ey - nearY) * (ey - nearY)).toDouble())
                    if (dist < radius) { touched = true; break }
                }
                touched
            }
            else -> false
        }
    }

    private fun isTextTouchedByEraser(text: TextModel, ex: Float, ey: Float, radius: Float): Boolean {
        return ex > text.positionX - radius &&
                ex < text.positionX + 200 + radius &&
                ey > text.positionY - text.size - radius &&
                ey < text.positionY + radius
    }
}