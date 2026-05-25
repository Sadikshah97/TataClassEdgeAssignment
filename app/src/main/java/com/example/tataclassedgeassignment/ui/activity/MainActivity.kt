package com.example.tataclassedgeassignment.ui.activity

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tataclassedgeassignment.R
import com.example.tataclassedgeassignment.databinding.ActivityMainBinding
import com.example.tataclassedgeassignment.model.TextModel
import com.example.tataclassedgeassignment.model.ToolItem
import com.example.tataclassedgeassignment.model.ToolItemType
import com.example.tataclassedgeassignment.ui.adapter.ColorAdapter
import com.example.tataclassedgeassignment.ui.adapter.ToolAdapter
import com.example.tataclassedgeassignment.ui.viewmodels.DrawingTool
import com.example.tataclassedgeassignment.ui.viewmodels.ToolState
import com.example.tataclassedgeassignment.ui.viewmodels.WhiteboardViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: WhiteboardViewModel by viewModels()

    private lateinit var toolAdapter: ToolAdapter
    private lateinit var colorAdapter: ColorAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupCanvas()
        setupToolRecyclerView()
        setupColorRecyclerView()

        binding.strokeWidthSlider.addOnChangeListener { _, value, _ ->
            viewModel.setStrokeWidth(value)
        }
        binding.btnSave.setOnClickListener { viewModel.saveWhiteboard() }
        binding.btnLoad.setOnClickListener { showLoadDialog() }
        binding.btnExport.setOnClickListener {
            viewModel.exportAsPng(binding.whiteboardView, this)
        }
        observeViewModel()
    }
    /*private fun setupCanvas() {
        binding.whiteboardView.apply {
            // ✅ All callbacks must be here
            onStrokeComplete = { stroke -> viewModel.addStroke(stroke) }
            onShapeComplete = { shape ->
                viewModel.addShape(shape)
            }
            onTextTap        = { x, y -> showTextInputDialog(x, y) }
            onTextEditRequest = { index, existing -> showTextEditDialog(index, existing) }

            onEraseBegin = { viewModel.beginErase() }
            onEraseAt    = { x, y, radius -> viewModel.removeShapesInArea(x, y, radius) }
            onTextMoved = { index, x, y -> viewModel.moveTextAt(index, x, y) }
        }
    }*/
    private fun setupCanvas() {
        binding.whiteboardView.apply {
            onStrokeComplete = { stroke ->
                binding.whiteboardView.saveEraserSnapshot()
                viewModel.addStroke(stroke)
            }
            onShapeComplete = { shape ->
                binding.whiteboardView.saveEraserSnapshot()
                viewModel.addShape(shape)
            }
            onTextTap = { x, y -> showTextInputDialog(x, y) }
            onTextEditRequest = { index, existing -> showTextEditDialog(index, existing) }
            onEraseBegin = { viewModel.beginErase() }
            onEraseAt    = { x, y, radius -> viewModel.removeShapesInArea(x, y, radius) }
            onTextMoved  = { index, x, y -> viewModel.moveTextAt(index, x, y) }
        }
    }    private fun showTextEditDialog(index: Int, existing: TextModel) {
        val input = EditText(this).apply {
            hint     = "Edit text"
            textSize = 18f
            setText(existing.text)
            maxLines = 3
            minLines = 3

            isSingleLine = false
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(existing.text)
            setSelection(existing.text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("Edit Text")
            .setView(input)
            .setPositiveButton("Update") { _, _ ->
                val txt = input.text.toString().trim()
                if (txt.isNotEmpty()) {
                    // Replace existing text at same position
                    val updated = existing.copy(text = txt)
                    viewModel.updateTextAt(index, updated)
                }
            }
            .setNeutralButton("Delete") { _, _ ->
                // Delete this text item
                viewModel.deleteTextAt(index)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }



    private fun setupToolRecyclerView() {
        val tools = listOf(
            ToolItem("pen",     R.drawable.ink_pen,      "Pen",     ToolItemType.TOOL),
            ToolItem("eraser",  R.drawable.ic_eraser,    "Eraser",  ToolItemType.TOOL),
            ToolItem("text",    R.drawable.ic_text,      "Text",    ToolItemType.TOOL),
            ToolItem("rect",    R.drawable.ic_rectangle, "Rect",    ToolItemType.TOOL),
            ToolItem("circle",  R.drawable.ic_circle,    "Circle",  ToolItemType.TOOL),
            ToolItem("line",    R.drawable.ic_line,      "Line",    ToolItemType.TOOL),
            ToolItem("polygon", R.drawable.ic_polygon,   "Polygon", ToolItemType.TOOL),
            ToolItem("div1",    0,                       "",        ToolItemType.DIVIDER),
            ToolItem("undo",    R.drawable.ic_undo,      "Undo",    ToolItemType.ACTION),
            ToolItem("redo",    R.drawable.ic_redo,      "Redo",    ToolItemType.ACTION),
            ToolItem("clear",   R.drawable.ic_clear,     "Clear",   ToolItemType.ACTION),
        )

        toolAdapter = ToolAdapter(tools) { item ->
            when (item.id) {
                "pen"     -> viewModel.setTool(DrawingTool.PEN)
                "eraser"  -> viewModel.setTool(DrawingTool.ERASER)
                "text"    -> viewModel.setTool(DrawingTool.TEXT)
                "rect"    -> viewModel.setTool(DrawingTool.RECTANGLE)
                "circle"  -> viewModel.setTool(DrawingTool.CIRCLE)
                "line"    -> viewModel.setTool(DrawingTool.LINE)
                "polygon" -> viewModel.setTool(DrawingTool.POLYGON)
                "undo"    -> viewModel.undo()
                "redo"    -> viewModel.redo()
                "clear"   -> showClearConfirmDialog()
            }
            if (item.type == ToolItemType.TOOL) {
                toolAdapter.setSelected(item.id)
            }
        }

        binding.toolsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = toolAdapter
        }
    }

    private fun setupColorRecyclerView() {
        val colors = listOf(
            Color.BLACK,
            Color.RED,
            Color.BLUE,
            Color.parseColor("#2ECC71"),
            Color.YELLOW,
            Color.parseColor("#FF6B00"),
            Color.parseColor("#9B59B6"),
            Color.WHITE
        )

        colorAdapter = ColorAdapter(colors) { color ->
            viewModel.setStrokeColor(color)
        }

        binding.colorsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = colorAdapter
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.toolState.collect { state ->
                binding.whiteboardView.toolState = state
                updateToolHighlight(state)
            }
        }
        lifecycleScope.launch {
            viewModel.strokes.collect { strokes ->
                binding.whiteboardView.updateStrokes(strokes)
            }
        }
        lifecycleScope.launch {
            viewModel.shapes.collect { binding.whiteboardView.updateShapes(it) }
        }
        lifecycleScope.launch {
            viewModel.texts.collect { binding.whiteboardView.updateTexts(it) }
        }
        lifecycleScope.launch {
            viewModel.saveMessage.collect { msg ->
                msg?.let {
                    Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                    viewModel.clearSaveMessage()
                }
            }
        }
        lifecycleScope.launch {
            viewModel.undoSignal.collect {
                binding.whiteboardView.undoEraserPaths()
            }
        }

    }
    private fun updateToolHighlight(state: ToolState) {
        val selectedId = when (state.activeTool) {
            DrawingTool.PEN       -> "pen"
            DrawingTool.ERASER    -> "eraser"
            DrawingTool.TEXT      -> "text"
            DrawingTool.RECTANGLE -> "rect"
            DrawingTool.CIRCLE    -> "circle"
            DrawingTool.LINE      -> "line"
            DrawingTool.POLYGON   -> "polygon"
            else                  -> return
        }
        toolAdapter.setSelected(selectedId)
    }

    private fun showClearConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear Canvas")
            .setMessage("Clear everything?")
            .setPositiveButton("Clear") { _, _ ->
                viewModel.clearCanvas()
                binding.whiteboardView.clearEraserPaths() // ✅ wapas add karo

                // binding.whiteboardView.clearEraserPaths() // ✅ ADD THIS
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTextInputDialog(x: Float, y: Float) {
        val input = EditText(this).apply {
            hint = "Enter text"
            textSize = 18f
            maxLines = 3
            minLines = 3
            isSingleLine = false
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE

        }
        AlertDialog.Builder(this)
            .setTitle("Insert Text")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val txt = input.text.toString().trim()
                if (txt.isNotEmpty()) {
                    viewModel.addText(
                        TextModel(
                            text = txt,
                            positionX = x,
                            positionY = y,
                            color = String.format(
                                "#%06X",
                                0xFFFFFF and viewModel.toolState.value.textColor
                            ),
                            size = viewModel.toolState.value.textSize
                        )
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLoadDialog() {
        viewModel.refreshSavedFiles()
        val files = viewModel.savedFiles.value
        if (files.isEmpty()) {
            Toast.makeText(this, "No saved whiteboards found", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Load Whiteboard")
            .setItems(files.toTypedArray()) { _, which ->
                viewModel.loadWhiteboard(files[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}