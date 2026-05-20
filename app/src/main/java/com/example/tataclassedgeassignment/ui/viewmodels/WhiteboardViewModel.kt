package com.example.tataclassedgeassignment.ui.viewmodels

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
        redoStack.addLast(Triple(_strokes.value, _shapes.value, _texts.value))
        val (s, sh, t) = undoStack.removeLast()
        _strokes.value = s
        _shapes.value = sh
        _texts.value = t
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(Triple(_strokes.value, _shapes.value, _texts.value))
        val (s, sh, t) = redoStack.removeLast()
        _strokes.value = s
        _shapes.value = sh
        _texts.value = t
    }

    fun clearCanvas() {
        pushUndoSnapshot()
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