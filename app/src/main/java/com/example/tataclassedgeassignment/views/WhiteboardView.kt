package com.example.tataclassedgeassignment.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.text.TextPaint
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

    var onStrokeComplete: ((StrokeModel) -> Unit)? = null
    var onShapeComplete: ((ShapeModel) -> Unit)? = null
    var onTextTap: ((Float, Float) -> Unit)? = null
    var onTextEditRequest: ((Int, TextModel) -> Unit)? = null
    var onEraseBegin: (() -> Unit)? = null
    var onTextMoved: ((Int, Float, Float) -> Unit)? = null
    var onShapeUpdated: ((Int, ShapeModel) -> Unit)? = null

    var toolState = ToolState()

    // Single bitmap
    private var canvasBitmap: Bitmap? = null
    private var canvasBitmapCanvas: Canvas? = null

    // Data models
    private var _strokes = mutableListOf<StrokeModel>()
    private var _shapes = mutableListOf<ShapeModel>()
    private var _texts = mutableListOf<TextModel>()

    // Flags and tracking
    private var neverRedrawFromModels = false
    private var lastKnownTexts = mutableListOf<TextModel>()

    // Drawing state
    private val currentPath = Path()
    private val currentPoints = mutableListOf<List<Float>>()
    private var shapeStartX = 0f
    private var shapeStartY = 0f
    private var shapeEndX = 0f
    private var shapeEndY = 0f
    private var isDrawingShape = false
    private var draggingTextIndex = -1
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f
    private var hasDragged = false
    private var selectedShapeIndex = -1
    private var selectedShape: ShapeModel? = null
    private var isResizing = false
    private var resizeStartX = 0f
    private var resizeStartY = 0f
    private var originalShape: ShapeModel? = null

    // Paints
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isDither = true
    }

    private val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        isAntiAlias = true
        color = Color.BLACK
    }

    private val bitmapPaint = Paint(Paint.DITHER_FLAG)

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLUE
    }

    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.BLUE
        strokeWidth = 2f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 5f), 0f)
    }

    fun updateStrokes(strokes: List<StrokeModel>) {
        _strokes.clear()
        _strokes.addAll(strokes)
        invalidate()
    }

    fun updateShapes(shapes: List<ShapeModel>) {
        _shapes.clear()
        _shapes.addAll(shapes)
        invalidate()
    }

    fun updateTexts(texts: List<TextModel>) {
        if (neverRedrawFromModels) {
            // Find NEW texts only (not in lastKnownTexts)
            val newTexts = texts.filter { newText ->
                !lastKnownTexts.any { oldText ->
                    oldText.text == newText.text &&
                            oldText.positionX == newText.positionX &&
                            oldText.positionY == newText.positionY &&
                            oldText.color == newText.color &&
                            oldText.size == newText.size
                }
            }

            // Find DELETED texts (in lastKnownTexts but not in texts)
            val deletedCount = lastKnownTexts.count { oldText ->
                !texts.any { newText ->
                    newText.text == oldText.text &&
                            newText.positionX == oldText.positionX &&
                            newText.positionY == oldText.positionY
                }
            }

            // Find MODIFIED texts (same position but different content)
            val modifiedCount = texts.count { newText ->
                lastKnownTexts.any { oldText ->
                    oldText.positionX == newText.positionX &&
                            oldText.positionY == newText.positionY &&
                            (oldText.text != newText.text || oldText.color != newText.color || oldText.size != newText.size)
                }
            }

            if (deletedCount > 0 || modifiedCount > 0) {
                // Text was deleted or edited - need full redraw
                redrawEverythingFromModels()
            } else if (newTexts.isNotEmpty()) {
                // Only NEW text added - just draw it on top of existing bitmap
                newTexts.forEach { text ->
                    drawTextModel(canvasBitmapCanvas!!, text)
                }
            }
            // If only text moved, the ViewModel handles that separately
        }

        _texts.clear()
        _texts.addAll(texts)
        lastKnownTexts.clear()
        lastKnownTexts.addAll(texts)
        invalidate()
    }

    private fun redrawEverythingFromModels() {
        val canvas = canvasBitmapCanvas ?: return
        canvas.drawColor(Color.WHITE)
        _strokes.forEach { drawStroke(canvas, it) }
        _shapes.forEach { drawShape(canvas, it) }
        _texts.forEach { drawTextModel(canvas, it) }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        if (w > 0 && h > 0) {
            canvasBitmap?.recycle()
            canvasBitmap = createBitmap(w, h)  // createBitmap creates MUTABLE bitmap
            canvasBitmapCanvas = Canvas(canvasBitmap!!)
            canvasBitmap?.eraseColor(Color.WHITE)
            neverRedrawFromModels = false
            lastKnownTexts.clear()
            redrawEverythingFromModels()
        }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val bmp = canvasBitmap ?: return
        val bmpCanvas = canvasBitmapCanvas ?: return

        if (!neverRedrawFromModels) {
            bmpCanvas.drawColor(Color.WHITE)
            _strokes.forEach { drawStroke(bmpCanvas, it) }
            _shapes.forEach { drawShape(bmpCanvas, it) }
            _texts.forEach { drawTextModel(bmpCanvas, it) }
        }

        canvas.drawBitmap(bmp, 0f, 0f, bitmapPaint)
        drawLivePreview(canvas)
    }

    private fun drawLivePreview(canvas: Canvas) {
        when (toolState.activeTool) {
            DrawingTool.PEN -> {
                strokePaint.apply {
                    color = toolState.strokeColor
                    strokeWidth = toolState.strokeWidth
                    xfermode = null
                }
                canvas.drawPath(currentPath, strokePaint)
            }
            DrawingTool.ERASER -> {
                if (currentPoints.isNotEmpty()) {
                    val last = currentPoints.last()
                    val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        color = Color.GRAY
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
                    val rx = abs(shapeEndX - shapeStartX) / 2
                    val ry = abs(shapeEndY - shapeStartY) / 2
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
            DrawingTool.POLYGON -> {
                if (isDrawingShape) {
                    strokePaint.apply {
                        color = toolState.strokeColor
                        strokeWidth = toolState.strokeWidth
                        xfermode = null
                    }
                    val path = buildPolygonPath(shapeStartX, shapeStartY, shapeEndX, shapeEndY, 5)
                    canvas.drawPath(path, strokePaint)
                }
            }
            else -> {}
        }

        selectedShape?.let { shape ->
            val left = minOf(shape.startX, shape.endX)
            val right = maxOf(shape.startX, shape.endX)
            val top = minOf(shape.startY, shape.endY)
            val bottom = maxOf(shape.startY, shape.endY)
            canvas.drawRect(left, top, right, bottom, selectionPaint)
            canvas.drawCircle(right, bottom, 16f, handlePaint)
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
            color = stroke.color.toColorInt()
            strokeWidth = stroke.width
            xfermode = null
        }
        canvas.drawPath(path, strokePaint)
    }

    private fun drawShape(canvas: Canvas, shape: ShapeModel) {
        strokePaint.apply {
            color = shape.color.toColorInt()
            strokeWidth = shape.strokeWidth
            xfermode = null
        }
        when (shape.type) {
            "rectangle" -> canvas.drawRect(shape.startX, shape.startY, shape.endX, shape.endY, strokePaint)
            "circle" -> {
                val cx = (shape.startX + shape.endX) / 2
                val cy = (shape.startY + shape.endY) / 2
                val rx = abs(shape.endX - shape.startX) / 2
                val ry = abs(shape.endY - shape.startY) / 2
                canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, strokePaint)
            }
            "line" -> canvas.drawLine(shape.startX, shape.startY, shape.endX, shape.endY, strokePaint)
            "polygon" -> canvas.drawPath(
                buildPolygonPath(shape.startX, shape.startY, shape.endX, shape.endY, 5), strokePaint
            )
        }
    }

    private fun drawTextModel(canvas: Canvas, textModel: TextModel) {
        textPaint.apply {
            color = textModel.color.toColorInt()
            textSize = textModel.size
        }
        val maxWidth = width / 3
        val staticLayout = android.text.StaticLayout.Builder
            .obtain(textModel.text, 0, textModel.text.length, textPaint, maxWidth)
            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()
        canvas.save()
        canvas.translate(textModel.positionX, textModel.positionY)
        staticLayout.draw(canvas)
        canvas.restore()
    }

    private fun buildPolygonPath(x1: Float, y1: Float, x2: Float, y2: Float, sides: Int): Path {
        val cx = (x1 + x2) / 2
        val cy = (y1 + y2) / 2
        val radius = minOf(abs(x2 - x1), abs(y2 - y1)) / 2
        val path = Path()
        for (i in 0 until sides) {
            val angle = (2.0 * Math.PI * i / sides - Math.PI / 2)
            val px = cx + radius * cos(angle).toFloat()
            val py = cy + radius * sin(angle).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        return path
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (toolState.activeTool) {
            DrawingTool.PEN, DrawingTool.ERASER -> handleFreehandTouch(event, x, y)
            DrawingTool.RECTANGLE, DrawingTool.CIRCLE,
            DrawingTool.LINE, DrawingTool.POLYGON -> handleShapeTouch(event, x, y)
            DrawingTool.TEXT -> handleTextTouch(event, x, y)
            else -> {}
        }
        return true
    }

    private var lastX = 0f
    private var lastY = 0f

    private fun handleFreehandTouch(event: MotionEvent, x: Float, y: Float) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPath.reset()
                currentPoints.clear()
                currentPath.moveTo(x, y)
                currentPoints.add(listOf(x, y))
                lastX = x
                lastY = y

                if (toolState.activeTool == DrawingTool.ERASER) {
                    // Save state BEFORE each erase stroke starts
                    onEraseBegin?.invoke()
                    neverRedrawFromModels = true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = abs(x - lastX)
                val dy = abs(y - lastY)

                if (dx >= 4f || dy >= 4f) {
                    currentPath.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2)
                    lastX = x
                    lastY = y
                    currentPoints.add(listOf(x, y))
                }

                if (toolState.activeTool == DrawingTool.ERASER) {
                    val pts = currentPoints
                    if (pts.size >= 2) {
                        val prev = pts[pts.size - 2]
                        val curr = pts[pts.size - 1]
                        val erasePath = Path()
                        erasePath.moveTo(prev[0], prev[1])
                        erasePath.lineTo(curr[0], curr[1])

                        val eraseWidth = toolState.strokeWidth * 4
                        eraserPaint.strokeWidth = eraseWidth
                        eraserPaint.style = Paint.Style.STROKE
                        eraserPaint.strokeCap = Paint.Cap.ROUND
                        eraserPaint.strokeJoin = Paint.Join.ROUND
                        eraserPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

                        canvasBitmapCanvas?.drawPath(erasePath, eraserPaint)
                    }
                }
                invalidate()
            }

            MotionEvent.ACTION_UP -> {
                currentPath.lineTo(x, y)
                currentPoints.add(listOf(x, y))

                if (toolState.activeTool == DrawingTool.ERASER) {
                    // Erase stroke complete - undo state already saved at ACTION_DOWN
                    // No additional action needed
                } else {
                    // For pen: save state BEFORE drawing (handled by ViewModel)
                    strokePaint.apply {
                        color = toolState.strokeColor
                        strokeWidth = toolState.strokeWidth
                        xfermode = null
                    }
                    canvasBitmapCanvas?.drawPath(currentPath, strokePaint)

                    val colorHex = String.format("#%06X", 0xFFFFFF and toolState.strokeColor)
                    val stroke = StrokeModel(
                        points = currentPoints.toList(),
                        color = colorHex,
                        width = toolState.strokeWidth,
                        isEraser = false
                    )
                    onStrokeComplete?.invoke(stroke)
                }

                currentPath.reset()
                currentPoints.clear()
                invalidate()
            }
        }
    }
    private fun handleShapeTouch(event: MotionEvent, x: Float, y: Float) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val hitIndex = findShapeAt(x, y)
                if (hitIndex >= 0 && (toolState.activeTool == DrawingTool.RECTANGLE
                            || toolState.activeTool == DrawingTool.CIRCLE
                            || toolState.activeTool == DrawingTool.LINE
                            || toolState.activeTool == DrawingTool.POLYGON)) {
                    selectedShapeIndex = hitIndex
                    selectedShape = _shapes[hitIndex]
                    originalShape = _shapes[hitIndex]
                    resizeStartX = x
                    resizeStartY = y
                    isResizing = true
                } else {
                    selectedShapeIndex = -1
                    selectedShape = null
                    shapeStartX = x
                    shapeStartY = y
                    shapeEndX = x
                    shapeEndY = y
                    isDrawingShape = true
                    isResizing = false
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isResizing && selectedShapeIndex in _shapes.indices) {
                    val dx = x - resizeStartX
                    val dy = y - resizeStartY
                    val orig = originalShape ?: return
                    val updated = orig.copy(endX = orig.endX + dx, endY = orig.endY + dy)
                    _shapes[selectedShapeIndex] = updated
                    selectedShape = updated
                    redrawEverythingFromModels()
                    invalidate()
                } else if (isDrawingShape) {
                    shapeEndX = x
                    shapeEndY = y
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP -> {
                if (isResizing && selectedShapeIndex in _shapes.indices) {
                    onShapeUpdated?.invoke(selectedShapeIndex, _shapes[selectedShapeIndex])
                    isResizing = false
                    selectedShapeIndex = -1
                    selectedShape = null
                } else if (isDrawingShape) {
                    shapeEndX = x
                    shapeEndY = y
                    isDrawingShape = false

                    val colorHex = String.format("#%06X", 0xFFFFFF and toolState.strokeColor)
                    val typeName = when (toolState.activeTool) {
                        DrawingTool.RECTANGLE -> "rectangle"
                        DrawingTool.CIRCLE -> "circle"
                        DrawingTool.LINE -> "line"
                        DrawingTool.POLYGON -> "polygon"
                        else -> "rectangle"
                    }

                    val shape = ShapeModel(
                        type = typeName,
                        startX = shapeStartX,
                        startY = shapeStartY,
                        endX = shapeEndX,
                        endY = shapeEndY,
                        color = colorHex,
                        strokeWidth = toolState.strokeWidth
                    )

                    strokePaint.apply {
                        color = colorHex.toColorInt()
                        strokeWidth = toolState.strokeWidth
                        xfermode = null
                    }
                    drawShape(canvasBitmapCanvas!!, shape)

                    onShapeComplete?.invoke(shape)
                }
                invalidate()
            }
        }
    }

    private fun handleTextTouch(event: MotionEvent, x: Float, y: Float) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val tappedIndex = findTextAt(x, y)
                if (tappedIndex in _texts.indices) {
                    draggingTextIndex = tappedIndex
                    dragOffsetX = x - _texts[tappedIndex].positionX
                    dragOffsetY = y - _texts[tappedIndex].positionY
                    hasDragged = false
                } else {
                    draggingTextIndex = -1
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingTextIndex in _texts.indices) {
                    val newX = x - dragOffsetX
                    val newY = y - dragOffsetY
                    val dx = abs(x - (_texts[draggingTextIndex].positionX + dragOffsetX))
                    val dy = abs(y - (_texts[draggingTextIndex].positionY + dragOffsetY))
                    if (dx > 10f || dy > 10f) {
                        hasDragged = true
                        onTextMoved?.invoke(draggingTextIndex, newX, newY)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (draggingTextIndex in _texts.indices) {
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

    private fun findShapeAt(x: Float, y: Float): Int {
        for (i in _shapes.indices.reversed()) {
            val shape = _shapes[i]
            val left = minOf(shape.startX, shape.endX) - 20f
            val right = maxOf(shape.startX, shape.endX) + 20f
            val top = minOf(shape.startY, shape.endY) - 20f
            val bottom = maxOf(shape.startY, shape.endY) + 20f
            if (x in left..right && y in top..bottom) return i
        }
        return -1
    }

    private fun findTextAt(x: Float, y: Float): Int {
        for (i in _texts.indices.reversed()) {
            val t = _texts[i]
            textPaint.textSize = t.size
            val maxWidth = width / 3
            val staticLayout = android.text.StaticLayout.Builder
                .obtain(t.text, 0, t.text.length, textPaint, maxWidth)
                .build()
            val left = t.positionX
            val top = t.positionY
            val right = left + maxWidth.toFloat()
            val bottom = top + staticLayout.height.toFloat()
            if (x in left..right && y in top..bottom) return i
        }
        return -1
    }

    fun getCurrentBitmap(): Bitmap? {
        val bmp = canvasBitmap ?: return null
        // Create a MUTABLE copy
        return bmp.copy(Bitmap.Config.ARGB_8888, true)  // true = mutable
    }

    fun restoreBitmap(bitmap: Bitmap) {
        canvasBitmap?.recycle()
        // Make sure we create a MUTABLE copy
        if (bitmap.isMutable) {
            canvasBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            // If somehow immutable, create a mutable copy
            canvasBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            bitmap.recycle() // Recycle the immutable one
        }
        canvasBitmapCanvas = Canvas(canvasBitmap!!)
        neverRedrawFromModels = true  // Keep this flag after undo/redo
        invalidate()
    }

    fun clearAll() {
        // Reset all flags
        neverRedrawFromModels = false

        // Clear all data
        _strokes.clear()
        _shapes.clear()
        _texts.clear()
        lastKnownTexts.clear()

        // Clear bitmap
        canvasBitmap?.eraseColor(Color.WHITE)

        // Redraw (will be empty)
        redrawEverythingFromModels()

        // Force UI update
        invalidate()
    }
    fun needsRedrawPublic() {
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        canvasBitmap?.recycle()
        canvasBitmap = null
    }
}