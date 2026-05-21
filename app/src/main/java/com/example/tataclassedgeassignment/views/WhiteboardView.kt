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

    var onStrokeComplete: ((StrokeModel) -> Unit)? = null
    var onShapeComplete: ((ShapeModel) -> Unit)? = null
    var onTextTap: ((Float, Float) -> Unit)? = null
    var onTextEditRequest: ((Int, TextModel) -> Unit)? = null
    var onEraseAt: ((Float, Float, Float) -> Unit)? = null
    var onEraseBegin: (() -> Unit)? = null

    var toolState = ToolState()

    private var _strokes = mutableListOf<StrokeModel>()
    private var _shapes  = mutableListOf<ShapeModel>()
    private var _texts   = mutableListOf<TextModel>()

    // ─── SINGLE bitmap — eraser clears everything ─────────────────
    private var drawingBitmap: Bitmap? = null
    private var drawingCanvas: Canvas? = null
    private var needsRedraw = true

    private val currentPath   = Path()
    private val currentPoints = mutableListOf<List<Float>>()
    private var shapeStartX   = 0f
    private var shapeStartY   = 0f
    private var shapeEndX     = 0f
    private var shapeEndY     = 0f
    private var isDrawingShape = false

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style      = Paint.Style.STROKE
        strokeCap  = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
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
    /*fun updateStrokes(strokes: List<StrokeModel>) {
        _strokes.clear(); _strokes.addAll(strokes)
        needsRedraw = true; invalidate()
    }*/
    fun updateStrokes(strokes: List<StrokeModel>) {
        _strokes.clear()
        _strokes.addAll(strokes)

        // Don't redraw everything after erasing
        if (toolState.activeTool != DrawingTool.ERASER) {
            needsRedraw = true
        }

        invalidate()
    }

    fun updateShapes(shapes: List<ShapeModel>) {
        _shapes.clear(); _shapes.addAll(shapes)
        needsRedraw = true; invalidate()
    }

    fun updateTexts(texts: List<TextModel>) {
        _texts.clear(); _texts.addAll(texts)
        needsRedraw = true; invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        drawingBitmap?.recycle()
        // ARGB_8888 so PorterDuff.CLEAR works (makes pixels transparent)
        drawingBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        drawingCanvas = Canvas(drawingBitmap!!)
        needsRedraw = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas) // ← View background #FFFFFF draws here

        val bmp    = drawingBitmap ?: return
        val bmpCvs = drawingCanvas ?: return

        if (needsRedraw) {
            // Clear to transparent — View's white background shows through
            bmpCvs.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            // Draw everything on ONE bitmap — eraser can clear any of it
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
                // Show circle cursor so user can see eraser size
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
                        color = toolState.strokeColor
                        strokeWidth = toolState.strokeWidth
                        xfermode = null
                    }
                    canvas.drawRect(shapeStartX, shapeStartY, shapeEndX, shapeEndY, strokePaint)
                }
            }
            DrawingTool.CIRCLE -> {
                if (isDrawingShape) {
                    strokePaint.apply {
                        color = toolState.strokeColor
                        strokeWidth = toolState.strokeWidth
                        xfermode = null
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
                        color = toolState.strokeColor
                        strokeWidth = toolState.strokeWidth
                        xfermode = null
                    }
                    canvas.drawLine(shapeStartX, shapeStartY, shapeEndX, shapeEndY, strokePaint)
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
            path.quadTo(
                prev[0], prev[1],
                (prev[0] + curr[0]) / 2,
                (prev[1] + curr[1]) / 2
            )
        }
        strokePaint.apply {
            color       = Color.parseColor(stroke.color)
            strokeWidth = stroke.width
            xfermode    = null
        }
        canvas.drawPath(path, strokePaint)
    }

    private fun drawShape(canvas: Canvas, shape: ShapeModel) {
        strokePaint.apply {
            color       = Color.parseColor(shape.color)
            strokeWidth = shape.strokeWidth
            xfermode    = null
        }
        when (shape.type) {
            "rectangle" -> canvas.drawRect(shape.startX, shape.startY, shape.endX, shape.endY, strokePaint)
            "circle" -> {
                val cx = (shape.startX + shape.endX) / 2
                val cy = (shape.startY + shape.endY) / 2
                val rx = Math.abs(shape.endX - shape.startX) / 2
                val ry = Math.abs(shape.endY - shape.startY) / 2
                canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, strokePaint)
            }
            "line"    -> canvas.drawLine(shape.startX, shape.startY, shape.endX, shape.endY, strokePaint)
            "polygon" -> canvas.drawPath(
                buildPolygonPath(shape.startX, shape.startY, shape.endX, shape.endY, 5), strokePaint)
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (toolState.activeTool) {
            DrawingTool.PEN, DrawingTool.ERASER -> handleFreehandTouch(event, x, y)
            DrawingTool.RECTANGLE,
            DrawingTool.CIRCLE,
            DrawingTool.LINE,
            DrawingTool.POLYGON -> handleShapeTouch(event, x, y)
            DrawingTool.TEXT -> {
                if (event.action == MotionEvent.ACTION_UP) {
                    val tappedIndex = findTextAt(x, y)
                    if (tappedIndex >= 0) onTextEditRequest?.invoke(tappedIndex, _texts[tappedIndex])
                    else onTextTap?.invoke(x, y)
                }
            }
            else -> {}
        }
        return true
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
                    onEraseBegin?.invoke() // ← snapshot ONCE
                }
            }
            MotionEvent.ACTION_MOVE -> {
                currentPath.lineTo(x, y)
                currentPoints.add(listOf(x, y))

                if (toolState.activeTool == DrawingTool.ERASER) {
                    val pts = currentPoints
                    if (pts.size >= 2) {
                        // ✅ Erase pixels from single bitmap
                        val erasePath = Path()
                        val prev = pts[pts.size - 2]
                        val curr = pts[pts.size - 1]
                        erasePath.moveTo(prev[0], prev[1])
                        erasePath.lineTo(curr[0], curr[1])
                        eraserPaint.strokeWidth = toolState.strokeWidth * 4
                        drawingCanvas?.drawPath(erasePath, eraserPaint)

                        // ✅ Remove shapes/strokes/text from ViewModel lists
                        onEraseAt?.invoke(x, y, toolState.strokeWidth * 4)
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                currentPath.lineTo(x, y)
                currentPoints.add(listOf(x, y))

                val isEraser = toolState.activeTool == DrawingTool.ERASER

                if (!isEraser) {
                    val colorHex = String.format("#%06X", 0xFFFFFF and toolState.strokeColor)
                    onStrokeComplete?.invoke(
                        StrokeModel(
                            points   = currentPoints.toList(),
                            color    = colorHex,
                            width    = toolState.strokeWidth,
                            isEraser = false
                        )
                    )
                    val stroke = StrokeModel(
                        points = currentPoints.toList(),
                        color = colorHex,
                        width = toolState.strokeWidth,
                        isEraser = false
                    )

                    onStrokeComplete?.invoke(stroke)
                    drawStroke(drawingCanvas ?: return, stroke)
                    needsRedraw = false // ← only redraw for pen strokes
                }
                // ✅ For eraser: pixels already cleared, ViewModel items already removed
                // needsRedraw stays FALSE → no redraw → nothing comes back
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