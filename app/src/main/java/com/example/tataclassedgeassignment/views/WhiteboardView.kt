package com.example.tataclassedgeassignment.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.tataclassedgeassignment.model.ShapeModel
import com.example.tataclassedgeassignment.model.StrokeModel
import com.example.tataclassedgeassignment.model.TextModel
import com.example.tataclassedgeassignment.ui.viewmodels.DrawingTool
import com.example.tataclassedgeassignment.ui.viewmodels.ToolState

class WhiteboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ─── Callbacks ───────────────────────────────────────────────
    var onStrokeComplete: ((StrokeModel) -> Unit)? = null
    var onShapeComplete: ((ShapeModel) -> Unit)? = null
    var onTextTap: ((Float, Float) -> Unit)? = null
    var onTextEditRequest: ((Int, TextModel) -> Unit)? = null  // NEW: index + model

    var toolState = ToolState()

    // ─── Committed data ──────────────────────────────────────────
    private var _strokes = mutableListOf<StrokeModel>()
    private var _shapes  = mutableListOf<ShapeModel>()
    private var _texts   = mutableListOf<TextModel>()

    // ─── Bitmap canvas for real erasing ──────────────────────────
    private var drawingBitmap: Bitmap? = null
    private var drawingCanvas: Canvas? = null
    private var needsRedraw = true

    // ─── In-progress stroke / shape ──────────────────────────────
    private val currentPath   = Path()
    private val currentPoints = mutableListOf<List<Float>>()
    private var shapeStartX   = 0f
    private var shapeStartY   = 0f
    private var shapeEndX     = 0f
    private var shapeEndY     = 0f
    private var isDrawingShape = false

    // ─── Paints ──────────────────────────────────────────────────
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style     = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style     = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        // REAL erase — removes pixels from bitmap, not paint-over-white
        xfermode  = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val bitmapPaint = Paint(Paint.DITHER_FLAG)

    // ─── Public update methods ────────────────────────────────────
    fun updateStrokes(strokes: List<StrokeModel>) {
        _strokes.clear(); _strokes.addAll(strokes)
        needsRedraw = true; invalidate()
    }

    fun updateShapes(shapes: List<ShapeModel>) {
        _shapes.clear(); _shapes.addAll(shapes)
        needsRedraw = true; invalidate()
    }

    fun updateTexts(texts: List<TextModel>) {
        _texts.clear(); _texts.addAll(texts)
        needsRedraw = true; invalidate()
    }

    // ─── Size changed — create backing bitmap ─────────────────────
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        drawingBitmap?.recycle()
        drawingBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        drawingCanvas = Canvas(drawingBitmap!!)
        needsRedraw = true
    }

    // ─── Draw ────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val bmp = drawingBitmap ?: return
        val bmpCanvas = drawingCanvas ?: return

        // Only re-render committed content to bitmap when data changed
        if (needsRedraw) {
            bmpCanvas.drawColor(Color.WHITE)
            _strokes.forEach { drawStroke(bmpCanvas, it) }
            _shapes.forEach  { drawShape(bmpCanvas, it) }
            _texts.forEach   { drawTextModel(bmpCanvas, it) }
            needsRedraw = false
        }

        // Draw committed bitmap to screen
        canvas.drawBitmap(bmp, 0f, 0f, bitmapPaint)

        // Draw in-progress stroke/shape on top (live preview)
        drawLivePreview(canvas)
    }

    private fun drawLivePreview(canvas: Canvas) {
        when (toolState.activeTool) {
            DrawingTool.PEN -> {
                strokePaint.apply {
                    color       = toolState.strokeColor
                    strokeWidth = toolState.strokeWidth
                    xfermode    = null
                }
                canvas.drawPath(currentPath, strokePaint)
            }
            DrawingTool.ERASER -> {
                // Show eraser cursor as grey circle so user can see it
                val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style       = Paint.Style.STROKE
                    color       = Color.LTGRAY
                    strokeWidth = 1f
                }
                // Draw the eraser path preview in light gray
                strokePaint.apply {
                    color       = Color.LTGRAY
                    strokeWidth = toolState.strokeWidth * 4
                    xfermode    = null
                }
                canvas.drawPath(currentPath, strokePaint)
            }
            DrawingTool.RECTANGLE -> {
                if (isDrawingShape) {
                    strokePaint.apply {
                        color       = toolState.strokeColor
                        strokeWidth = toolState.strokeWidth
                        xfermode    = null
                    }
                    canvas.drawRect(shapeStartX, shapeStartY, shapeEndX, shapeEndY, strokePaint)
                }
            }
            DrawingTool.CIRCLE -> {
                if (isDrawingShape) {
                    strokePaint.apply {
                        color       = toolState.strokeColor
                        strokeWidth = toolState.strokeWidth
                        xfermode    = null
                    }
                    val cx = (shapeStartX + shapeEndX) / 2
                    val cy = (shapeStartY + shapeEndY) / 2
                    val rx = Math.abs(shapeEndX - shapeStartX) / 2
                    val ry = Math.abs(shapeEndY - shapeStartY) / 2
                    canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, strokePaint)
                }
            }
            DrawingTool.LINE -> {
                if (isDrawingShape) {
                    strokePaint.apply {
                        color       = toolState.strokeColor
                        strokeWidth = toolState.strokeWidth
                        xfermode    = null
                    }
                    canvas.drawLine(shapeStartX, shapeStartY, shapeEndX, shapeEndY, strokePaint)
                }
            }
            else -> {}
        }
    }

    // ─── Draw committed stroke ───────────────────────────────────
    private fun drawStroke(canvas: Canvas, stroke: StrokeModel) {
        if (stroke.points.size < 2) return

        val path = Path()
        path.moveTo(stroke.points[0][0], stroke.points[0][1])
        for (i in 1 until stroke.points.size) {
            val prev = stroke.points[i - 1]
            val curr = stroke.points[i]
            // Smooth quadratic bezier curve
            path.quadTo(
                prev[0], prev[1],
                (prev[0] + curr[0]) / 2,
                (prev[1] + curr[1]) / 2
            )
        }

        if (stroke.isEraser) {
            // REAL erase — clears pixels from bitmap
            eraserPaint.strokeWidth = stroke.width * 4
            canvas.drawPath(path, eraserPaint)
        } else {
            strokePaint.apply {
                color       = Color.parseColor(stroke.color)
                strokeWidth = stroke.width
                xfermode    = null
            }
            canvas.drawPath(path, strokePaint)
        }
    }

    private fun drawShape(canvas: Canvas, shape: ShapeModel) {
        strokePaint.apply {
            color       = Color.parseColor(shape.color)
            strokeWidth = shape.strokeWidth
            xfermode    = null
        }
        when (shape.type) {
            "rectangle" -> canvas.drawRect(shape.startX, shape.startY, shape.endX, shape.endY, strokePaint)
            "circle"    -> {
                val cx = (shape.startX + shape.endX) / 2
                val cy = (shape.startY + shape.endY) / 2
                val rx = Math.abs(shape.endX - shape.startX) / 2
                val ry = Math.abs(shape.endY - shape.startY) / 2
                canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, strokePaint)
            }
            "line"      -> canvas.drawLine(shape.startX, shape.startY, shape.endX, shape.endY, strokePaint)
            "polygon"   -> {
                val path = buildPolygonPath(shape.startX, shape.startY, shape.endX, shape.endY, 5)
                canvas.drawPath(path, strokePaint)
            }
        }
    }

    private fun drawTextModel(canvas: Canvas, textModel: TextModel) {
        textPaint.apply {
            color    = Color.parseColor(textModel.color)
            textSize = textModel.size
        }
        canvas.drawText(textModel.text, textModel.positionX, textModel.positionY, textPaint)
    }

    private fun buildPolygonPath(x1: Float, y1: Float, x2: Float, y2: Float, sides: Int): Path {
        val cx     = (x1 + x2) / 2
        val cy     = (y1 + y2) / 2
        val radius = minOf(Math.abs(x2 - x1), Math.abs(y2 - y1)) / 2
        val path   = Path()
        for (i in 0 until sides) {
            val angle = (2.0 * Math.PI * i / sides - Math.PI / 2).toFloat()
            val px    = cx + radius * Math.cos(angle.toDouble()).toFloat()
            val py    = cy + radius * Math.sin(angle.toDouble()).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        return path
    }

    // ─── Touch handling ──────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (toolState.activeTool) {
            DrawingTool.PEN, DrawingTool.ERASER ->
                handleFreehandTouch(event, x, y)

            DrawingTool.RECTANGLE,
            DrawingTool.CIRCLE,
            DrawingTool.LINE,
            DrawingTool.POLYGON ->
                handleShapeTouch(event, x, y)

            DrawingTool.TEXT -> {
                if (event.action == MotionEvent.ACTION_UP) {
                    // Check if tapped on existing text first
                    val tappedIndex = findTextAt(x, y)
                    if (tappedIndex >= 0) {
                        // Edit existing text
                        onTextEditRequest?.invoke(tappedIndex, _texts[tappedIndex])
                    } else {
                        // Add new text
                        onTextTap?.invoke(x, y)
                    }
                }
            }

            else -> {}
        }
        return true
    }

    // ─── Detect tap on existing text ─────────────────────────────
    private fun findTextAt(x: Float, y: Float): Int {
        // Iterate in reverse so topmost text is checked first
        for (i in _texts.indices.reversed()) {
            val t = _texts[i]
            textPaint.textSize = t.size
            val textWidth  = textPaint.measureText(t.text)
            val textHeight = t.size

            // Hit box: from positionX to positionX+width, positionY-height to positionY
            val hitLeft   = t.positionX - 8f
            val hitRight  = t.positionX + textWidth + 8f
            val hitTop    = t.positionY - textHeight - 8f
            val hitBottom = t.positionY + 8f

            if (x in hitLeft..hitRight && y in hitTop..hitBottom) {
                return i
            }
        }
        return -1
    }

    private fun handleFreehandTouch(event: MotionEvent, x: Float, y: Float) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPath.reset()
                currentPoints.clear()
                currentPath.moveTo(x, y)
                currentPoints.add(listOf(x, y))
            }
            MotionEvent.ACTION_MOVE -> {
                currentPath.lineTo(x, y)
                currentPoints.add(listOf(x, y))
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                currentPath.lineTo(x, y)
                currentPoints.add(listOf(x, y))

                val colorHex = String.format("#%06X", 0xFFFFFF and toolState.strokeColor)
                val isEraser = toolState.activeTool == DrawingTool.ERASER

                onStrokeComplete?.invoke(
                    StrokeModel(
                        points   = currentPoints.toList(),
                        color    = colorHex,
                        width    = toolState.strokeWidth,
                        isEraser = isEraser
                    )
                )
                currentPath.reset()
                currentPoints.clear()
                invalidate()
            }
        }
    }

    private fun handleShapeTouch(event: MotionEvent, x: Float, y: Float) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                shapeStartX = x; shapeStartY = y
                shapeEndX   = x; shapeEndY   = y
                isDrawingShape = true
            }
            MotionEvent.ACTION_MOVE -> {
                shapeEndX = x; shapeEndY = y
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                shapeEndX = x; shapeEndY = y
                isDrawingShape = false

                val colorHex = String.format("#%06X", 0xFFFFFF and toolState.strokeColor)
                val typeName = when (toolState.activeTool) {
                    DrawingTool.RECTANGLE -> "rectangle"
                    DrawingTool.CIRCLE    -> "circle"
                    DrawingTool.LINE      -> "line"
                    DrawingTool.POLYGON   -> "polygon"
                    else                  -> "rectangle"
                }
                onShapeComplete?.invoke(
                    ShapeModel(
                        type        = typeName,
                        startX      = shapeStartX,
                        startY      = shapeStartY,
                        endX        = shapeEndX,
                        endY        = shapeEndY,
                        color       = colorHex,
                        strokeWidth = toolState.strokeWidth
                    )
                )
                invalidate()
            }
        }
    }
}