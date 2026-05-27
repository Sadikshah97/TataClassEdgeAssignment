package com.example.tataclassedgeassignment.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import com.example.tataclassedgeassignment.model.ShapeModel
import com.example.tataclassedgeassignment.model.StrokeModel
import com.example.tataclassedgeassignment.model.TextModel
import com.example.tataclassedgeassignment.ui.viewmodels.DrawingTool
import com.example.tataclassedgeassignment.ui.viewmodels.ToolState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class WhiteboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ─── Callbacks ───────────────────────────────────────────────
    var onStrokeComplete: ((StrokeModel) -> Unit)? = null
    var onShapeComplete: ((ShapeModel) -> Unit)? = null
    var onTextTap: ((Float, Float) -> Unit)? = null
    var onTextEditRequest: ((Int, TextModel) -> Unit)? = null
    var onEraseAt: ((Float, Float, Float) -> Unit)? = null
    var onEraseBegin: (() -> Unit)? = null
    var onTextMoved: ((Int, Float, Float) -> Unit)? = null

    var toolState = ToolState()

    // ─── Data ────────────────────────────────────────────────────
    private var _strokes = mutableListOf<StrokeModel>()
    private var _shapes  = mutableListOf<ShapeModel>()
    private var _texts   = mutableListOf<TextModel>()

    // ─── Bitmap ──────────────────────────────────────────────────
    private var drawingBitmap: android.graphics.Bitmap? = null
    private var drawingCanvas: Canvas? = null
    private var needsRedraw = true

    // ─── Drawing state ───────────────────────────────────────────
    private val currentPath   = Path()
    private val currentPoints = mutableListOf<List<Float>>()
    private var shapeStartX   = 0f
    private var shapeStartY   = 0f
    private var shapeEndX     = 0f
    private var shapeEndY     = 0f
    private var isDrawingShape = false
    private var draggingTextIndex = -1
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f
    private var hasDragged = false

    // ─── Paints ──────────────────────────────────────────────────
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style      = Paint.Style.STROKE
        strokeCap  = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isDither   = true
    }

    private val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style      = Paint.Style.STROKE
        strokeCap  = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        xfermode   = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val bitmapPaint = Paint(Paint.DITHER_FLAG)

    // ─── Public update methods ────────────────────────────────────
    fun updateStrokes(strokes: List<StrokeModel>) {
        _strokes.clear()
        _strokes.addAll(strokes)
        needsRedraw = true
        invalidate()
    }

    fun updateShapes(shapes: List<ShapeModel>) {
        _shapes.clear()
        _shapes.addAll(shapes)
        needsRedraw = true
        invalidate()
    }

    fun updateTexts(texts: List<TextModel>) {
        _texts.clear()
        _texts.addAll(texts)
        needsRedraw = true
        invalidate()
    }

    // ─── Bitmap setup ─────────────────────────────────────────────
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        drawingBitmap?.recycle()
        drawingBitmap = createBitmap(w, h)
        drawingCanvas = Canvas(drawingBitmap!!)
        needsRedraw = true
    }

    // ─── Draw ─────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val bmp    = drawingBitmap ?: return
        val bmpCvs = drawingCanvas ?: return

        if (needsRedraw) {
            bmpCvs.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            _strokes.forEach { drawStroke(bmpCvs, it) }
            _shapes.forEach  { drawShape(bmpCvs, it) }
            _texts.forEach   { drawTextModel(bmpCvs, it) }
            needsRedraw = false
        }

        canvas.drawBitmap(bmp, 0f, 0f, bitmapPaint)
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
                if (currentPoints.isNotEmpty()) {
                    val last = currentPoints.last()
                    val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style       = Paint.Style.STROKE
                        color       = Color.GRAY
                        strokeWidth = 1.5f
                    }
                    canvas.drawCircle(last[0], last[1], toolState.strokeWidth * 2, cursorPaint)
                }
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
                    val rx = abs(shapeEndX - shapeStartX) / 2
                    val ry = abs(shapeEndY - shapeStartY) / 2
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
            DrawingTool.POLYGON -> {
                if (isDrawingShape) {
                    strokePaint.apply {
                        color       = toolState.strokeColor
                        strokeWidth = toolState.strokeWidth
                        xfermode    = null
                    }
                    val path = buildPolygonPath(shapeStartX, shapeStartY, shapeEndX, shapeEndY, 5)
                    canvas.drawPath(path, strokePaint)
                }
            }
            else -> {}
        }
    }

    private fun drawStroke(canvas: Canvas, stroke: StrokeModel) {
        if (stroke.points.size < 2) return
        val path = Path()
        path.moveTo(stroke.points[0][0], stroke.points[0][1])
        for (i in 1 until stroke.points.size) {
            val prev = stroke.points[i - 1]
            val curr = stroke.points[i]
            val midX = (prev[0] + curr[0]) / 2
            val midY = (prev[1] + curr[1]) / 2
            path.quadTo(prev[0], prev[1], midX, midY)
        }
        val last = stroke.points.last()
        path.lineTo(last[0], last[1])
        strokePaint.apply {
            color       = stroke.color.toColorInt()
            strokeWidth = stroke.width
            xfermode    = null
        }
        canvas.drawPath(path, strokePaint)
    }

    private fun drawShape(canvas: Canvas, shape: ShapeModel) {
        strokePaint.apply {
            color       = shape.color.toColorInt()
            strokeWidth = shape.strokeWidth
            xfermode    = null
        }
        when (shape.type) {
            "rectangle" -> canvas.drawRect(
                shape.startX, shape.startY, shape.endX, shape.endY, strokePaint)
            "circle" -> {
                val cx = (shape.startX + shape.endX) / 2
                val cy = (shape.startY + shape.endY) / 2
                val rx = abs(shape.endX - shape.startX) / 2
                val ry = abs(shape.endY - shape.startY) / 2
                canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, strokePaint)
            }
            "line"    -> canvas.drawLine(
                shape.startX, shape.startY, shape.endX, shape.endY, strokePaint)
            "polygon" -> canvas.drawPath(
                buildPolygonPath(shape.startX, shape.startY, shape.endX, shape.endY, 5),
                strokePaint)
        }
    }

    private fun drawTextModel(canvas: Canvas, textModel: TextModel) {
        textPaint.apply {
            color    = textModel.color.toColorInt()
            textSize = textModel.size
        }
        canvas.drawText(textModel.text, textModel.positionX, textModel.positionY, textPaint)
    }

    private fun buildPolygonPath(x1: Float, y1: Float, x2: Float, y2: Float, sides: Int): Path {
        val cx     = (x1 + x2) / 2
        val cy     = (y1 + y2) / 2
        val radius = minOf(abs(x2 - x1), abs(y2 - y1)) / 2
        val path   = Path()
        for (i in 0 until sides) {
            val angle = (2.0 * Math.PI * i / sides - Math.PI / 2)
            val px    = cx + radius * cos(angle).toFloat()
            val py    = cy + radius * sin(angle).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        return path
    }

    // ─── Touch ───────────────────────────────────────────────────
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (toolState.activeTool) {
            DrawingTool.PEN, DrawingTool.ERASER -> handleFreehandTouch(event, x, y)
            DrawingTool.RECTANGLE,
            DrawingTool.CIRCLE,
            DrawingTool.LINE,
            DrawingTool.POLYGON -> handleShapeTouch(event, x, y)
            DrawingTool.TEXT -> handleTextTouch(event, x, y)
            else -> {}
        }
        return true
    }

    private fun handleTextTouch(event: MotionEvent, x: Float, y: Float) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val tappedIndex = findTextAt(x, y)
                if (tappedIndex >= 0) {
                    draggingTextIndex = tappedIndex
                    dragOffsetX = x - _texts[tappedIndex].positionX
                    dragOffsetY = y - _texts[tappedIndex].positionY
                    hasDragged = false
                } else {
                    draggingTextIndex = -1
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingTextIndex >= 0) {
                    hasDragged = true
                    onTextMoved?.invoke(draggingTextIndex, x - dragOffsetX, y - dragOffsetY)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (draggingTextIndex >= 0) {
                    if (!hasDragged) {
                        onTextEditRequest?.invoke(draggingTextIndex, _texts[draggingTextIndex])
                    }
                    draggingTextIndex = -1
                    hasDragged = false
                } else {
                    onTextTap?.invoke(x, y)
                }
            }
        }
    }

    private fun findTextAt(x: Float, y: Float): Int {
        for (i in _texts.indices.reversed()) {
            val t = _texts[i]
            textPaint.textSize = t.size
            val hitLeft   = t.positionX - 8f
            val hitRight  = t.positionX + textPaint.measureText(t.text) + 8f
            val hitTop    = t.positionY - t.size - 8f
            val hitBottom = t.positionY + 8f
            if (x in hitLeft..hitRight && y in hitTop..hitBottom) return i
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
                if (toolState.activeTool == DrawingTool.ERASER) {
                    onEraseBegin?.invoke()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                currentPoints.add(listOf(x, y))
                val pts = currentPoints
                if (pts.size >= 2) {
                    val prev = pts[pts.size - 2]
                    val curr = pts[pts.size - 1]
                    val midX = (prev[0] + curr[0]) / 2
                    val midY = (prev[1] + curr[1]) / 2
                    currentPath.quadTo(prev[0], prev[1], midX, midY)
                } else {
                    currentPath.lineTo(x, y)
                }
                if (toolState.activeTool == DrawingTool.ERASER) {
                    onEraseAt?.invoke(x, y, toolState.strokeWidth * 4)
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                currentPoints.add(listOf(x, y))
                val isEraser = toolState.activeTool == DrawingTool.ERASER
                if (!isEraser) {
                    val colorHex = String.format("#%06X", 0xFFFFFF and toolState.strokeColor)
                    val stroke = StrokeModel(
                        points   = currentPoints.toList(),
                        color    = colorHex,
                        width    = toolState.strokeWidth,
                        isEraser = false
                    )
                    onStrokeComplete?.invoke(stroke)
                    needsRedraw = true
                } else {
                    needsRedraw = true
                }
                currentPath.reset()
                currentPoints.clear()
                invalidate()
            }
        }
    } // ✅ handleFreehandTouch closing brace

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
    } // ✅ handleShapeTouch closing brace
} // ✅ class closing brace