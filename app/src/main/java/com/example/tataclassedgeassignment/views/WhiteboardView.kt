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

    // Two bitmaps - one for content (strokes+shapes), one for display (content+text)
    private var contentBitmap: Bitmap? = null
    private var contentCanvas: Canvas? = null
    private var displayBitmap: Bitmap? = null
    private var displayCanvas: Canvas? = null

    private var eraseBitmap: Bitmap? = null
    private var eraseCanvas: Canvas? = null

    // Data models
    private var _strokes = mutableListOf<StrokeModel>()
    private var _shapes = mutableListOf<ShapeModel>()
    private var _texts = mutableListOf<TextModel>()

    // Flags and tracking
    private var neverRedrawContentFromModels = false
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

    private val eraseOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
    }

    // Update content bitmap (strokes + shapes only)
    private fun updateContentBitmap() {
        val canvas = contentCanvas ?: return
        canvas.drawColor(Color.WHITE)
        _strokes.forEach { drawStroke(canvas, it) }
        _shapes.forEach { drawShape(canvas, it) }
        eraseBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
    }

    // Update display bitmap (content + text)
    private fun updateDisplayBitmap() {
        val displayCvs = displayCanvas ?: return
        val contentBmp = contentBitmap ?: return

        displayCvs.drawColor(Color.WHITE)
        displayCvs.drawBitmap(contentBmp, 0f, 0f, null)
        _texts.forEach { drawTextModel(displayCvs, it) }
        eraseBitmap?.let {
            displayCvs.drawBitmap(it, 0f, 0f, null)
        }
    }

    fun updateStrokes(strokes: List<StrokeModel>) {
        _strokes.clear()
        _strokes.addAll(strokes)
        if (!neverRedrawContentFromModels) {
            updateContentBitmap()
            updateDisplayBitmap()
        }
        invalidate()
    }

    fun updateShapes(shapes: List<ShapeModel>) {
        _shapes.clear()
        _shapes.addAll(shapes)
        if (!neverRedrawContentFromModels) {
            updateContentBitmap()
            updateDisplayBitmap()
        }
        invalidate()
    }

    fun updateTexts(texts: List<TextModel>) {
        val oldTexts = _texts.toList()
        _texts.clear()
        _texts.addAll(texts)
        lastKnownTexts.clear()
        lastKnownTexts.addAll(texts)

        if (neverRedrawContentFromModels) {
            val onlyPositionChanged = texts.size == oldTexts.size &&
                    texts.zip(oldTexts).all { (newText, oldText) ->
                        newText.text == oldText.text &&
                                newText.color == oldText.color &&
                                newText.size == oldText.size
                    }

            if (onlyPositionChanged) {
                // 🔥 FIX: DO NOT call updateDisplayBitmap() here!
                // Text position already updated during drag. No redraw needed.
                // Just invalidate to refresh the view.
            } else {
                val newTexts = texts.filter { newText ->
                    !oldTexts.any { oldText ->
                        oldText.text == newText.text &&
                                oldText.positionX == newText.positionX &&
                                oldText.positionY == newText.positionY
                    }
                }

                if (newTexts.isNotEmpty() && newTexts.size == texts.size - oldTexts.size) {
                    newTexts.forEach { text ->
                        drawTextModel(displayCanvas!!, text)
                    }
                } else {
                    redrawEverythingFromModels()
                }
            }
        } else {
            updateDisplayBitmap()
        }
        invalidate()
    }

    private fun redrawEverythingFromModels() {
        updateContentBitmap()
        updateDisplayBitmap()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        if (w > 0 && h > 0) {
            contentBitmap?.recycle()
            displayBitmap?.recycle()
            eraseBitmap?.recycle()

            contentBitmap = createBitmap(w, h)
            contentCanvas = Canvas(contentBitmap!!)

            displayBitmap = createBitmap(w, h)
            displayCanvas = Canvas(displayBitmap!!)

            eraseBitmap = createBitmap(w, h)
            eraseCanvas = Canvas(eraseBitmap!!)

            neverRedrawContentFromModels = false
            eraseCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            lastKnownTexts.clear()
            redrawEverythingFromModels()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val displayBmp = displayBitmap ?: return

        if (!neverRedrawContentFromModels) {
            updateContentBitmap()
            updateDisplayBitmap()
        }

        canvas.drawBitmap(displayBmp, 0f, 0f, bitmapPaint)
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

            DrawingTool.TEXT -> {
                // Show selected text border and resize handle
                selectedText?.let { text ->
                    textPaint.apply {
                        color = text.color.toColorInt()
                        textSize = text.size
                    }
                    val maxWidth = width / 3
                    val staticLayout = android.text.StaticLayout.Builder
                        .obtain(text.text, 0, text.text.length, textPaint, maxWidth)
                        .build()
                    val left = text.positionX
                    val top = text.positionY
                    val right = left + maxWidth.toFloat()
                    val bottom = top + staticLayout.height.toFloat()

                    // Dashed border around text
                    canvas.drawRect(left - 5, top - 5, right + 5, bottom + 5, selectionPaint)
                    // Resize handle at bottom-right
                    canvas.drawCircle(right + 5, bottom + 5, 16f, handlePaint)
                }
            }

            else -> {}
        }

        // Shape selection (keep existing)
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
            "rectangle" -> canvas.drawRect(
                shape.startX,
                shape.startY,
                shape.endX,
                shape.endY,
                strokePaint
            )

            "circle" -> {
                val cx = (shape.startX + shape.endX) / 2
                val cy = (shape.startY + shape.endY) / 2
                val rx = abs(shape.endX - shape.startX) / 2
                val ry = abs(shape.endY - shape.startY) / 2
                canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, strokePaint)
            }

            "line" -> canvas.drawLine(
                shape.startX,
                shape.startY,
                shape.endX,
                shape.endY,
                strokePaint
            )

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
                    onEraseBegin?.invoke()
                    neverRedrawContentFromModels = true
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
                        eraseOverlayPaint.strokeWidth = eraseWidth
                        eraserPaint.strokeWidth = eraseWidth
                        eraserPaint.style = Paint.Style.STROKE
                        eraserPaint.strokeCap = Paint.Cap.ROUND
                        eraserPaint.strokeJoin = Paint.Join.ROUND
                        eraserPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

                        // Erase from BOTH bitmaps
                        contentCanvas?.drawPath(erasePath, eraserPaint)
                        displayCanvas?.drawPath(erasePath, eraserPaint)
                        eraseCanvas?.drawPath(erasePath, eraseOverlayPaint)
                        contentCanvas?.drawPath(erasePath, eraseOverlayPaint)
                    }
                }
                invalidate()
            }

            MotionEvent.ACTION_UP -> {
                currentPath.lineTo(x, y)
                currentPoints.add(listOf(x, y))

                if (toolState.activeTool != DrawingTool.ERASER) {
                    strokePaint.apply {
                        color = toolState.strokeColor
                        strokeWidth = toolState.strokeWidth
                        xfermode = null
                    }
                    // Draw directly to content bitmap
                    contentCanvas?.drawPath(currentPath, strokePaint)

                    if (neverRedrawContentFromModels) {
                        // 🔥 After erase: Draw stroke directly on display too (don't redraw all texts)
                        displayCanvas?.drawPath(currentPath, strokePaint)
                    } else {
                        updateDisplayBitmap()
                    }

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
                            || toolState.activeTool == DrawingTool.POLYGON)
                ) {
                    selectedShapeIndex = hitIndex
                    selectedShape = _shapes[hitIndex]
                    originalShape = _shapes[hitIndex]
                    resizeStartX = x
                    resizeStartY = y
                    isResizing = true
                    isDrawingShape = false
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

//                    if (neverRedrawContentFromModels) {
//                        // 🔥 FIX: Draw resized shape directly on display (preserves erased content)
//                        // Don't call updateContentBitmap() - it would wipe erased pixels!
//                        displayCanvas?.drawColor(Color.WHITE)
//                        // Use a temporary copy of content to preserve erased areas
//                        val tempContent = contentBitmap?.copy(Bitmap.Config.ARGB_8888, false)
//                        displayCanvas?.drawBitmap(tempContent!!, 0f, 0f, null)
//                        tempContent?.recycle()
//                        // Draw only the resized shape on top
//                        drawShape(displayCanvas!!, updated)
//                        // Draw texts on top
//                        _texts.forEach { drawTextModel(displayCanvas!!, it) }
//                    } else {
//                        updateContentBitmap()
//                        updateDisplayBitmap()
//                    }
                    updateContentBitmap()
                    updateDisplayBitmap()
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
                    drawShape(contentCanvas!!, shape)

                    if (neverRedrawContentFromModels) {
                        drawShape(displayCanvas!!, shape)
                    } else {
                        updateDisplayBitmap()
                    }

                    onShapeComplete?.invoke(shape)
                }
                invalidate()
            }
        }
    }

    private var selectedTextIndex = -1
    private var selectedText: TextModel? = null
    private var isResizingText = false
    private var textResizeStartSize = 0f
    private var textResizeStartY = 0f
    private fun isNearTextResizeHandle(x: Float, y: Float, text: TextModel): Boolean {
        textPaint.textSize = text.size
        val maxWidth = width / 3
        val staticLayout = android.text.StaticLayout.Builder
            .obtain(text.text, 0, text.text.length, textPaint, maxWidth)
            .build()
        val handleX = text.positionX + maxWidth + 5
        val handleY = text.positionY + staticLayout.height + 5
        return abs(x - handleX) < 30f && abs(y - handleY) < 30f
    }

    private fun handleTextTouch(event: MotionEvent, x: Float, y: Float) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (selectedText != null && isNearTextResizeHandle(x, y, selectedText!!)) {
                    isResizingText = true
                    textResizeStartSize = selectedText!!.size
                    textResizeStartY = y
                    return
                }

                val tappedIndex = findTextAt(x, y)
                if (tappedIndex in _texts.indices) {
                    draggingTextIndex = tappedIndex
                    selectedTextIndex = tappedIndex
                    selectedText = _texts[tappedIndex]
                    dragOffsetX = x - _texts[tappedIndex].positionX
                    dragOffsetY = y - _texts[tappedIndex].positionY
                    hasDragged = false
                    isResizingText = false
                    invalidate()
                } else {
                    draggingTextIndex = -1
                    selectedTextIndex = -1
                    selectedText = null
                    invalidate()
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isResizingText && selectedTextIndex in _texts.indices) {
                    val dy = textResizeStartY - y
                    val newSize = (textResizeStartSize + dy).coerceIn(20f, 200f)
                    _texts[selectedTextIndex] = _texts[selectedTextIndex].copy(size = newSize)
                    selectedText = _texts[selectedTextIndex]

                    if (neverRedrawContentFromModels) {
                        // 🔥 Use content bitmap (has erased strokes/shapes) + redraw texts
                        displayCanvas?.drawColor(Color.WHITE)
                        displayCanvas?.drawBitmap(contentBitmap!!, 0f, 0f, null)
                        _texts.forEach { drawTextModel(displayCanvas!!, it) }
                    } else {
                        updateDisplayBitmap()
                    }
                    invalidate()
                } else if (draggingTextIndex in _texts.indices) {
                    val newX = x - dragOffsetX
                    val newY = y - dragOffsetY
                    val dx = abs(x - (_texts[draggingTextIndex].positionX + dragOffsetX))
                    val dy = abs(y - (_texts[draggingTextIndex].positionY + dragOffsetY))
                    if (dx > 10f || dy > 10f) {
                        hasDragged = true
                        _texts[draggingTextIndex] = _texts[draggingTextIndex].copy(
                            positionX = newX,
                            positionY = newY
                        )
                        selectedText = _texts[draggingTextIndex]

                        if (neverRedrawContentFromModels) {
                            // 🔥 Use content bitmap (has erased strokes/shapes) + redraw texts
                            displayCanvas?.drawColor(Color.WHITE)
                            displayCanvas?.drawBitmap(contentBitmap!!, 0f, 0f, null)
                            _texts.forEach { drawTextModel(displayCanvas!!, it) }
                        } else {
                            updateDisplayBitmap()
                        }
                        onTextMoved?.invoke(draggingTextIndex, newX, newY)
                        invalidate()
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                if (isResizingText && selectedTextIndex in _texts.indices) {
                    isResizingText = false
                } else if (draggingTextIndex in _texts.indices) {
                    if (!hasDragged) {
                        onTextEditRequest?.invoke(draggingTextIndex, _texts[draggingTextIndex])
                    }
                    draggingTextIndex = -1
                    hasDragged = false
                } else if (selectedText == null) {
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
        return displayBitmap?.copy(Bitmap.Config.ARGB_8888, true)
    }

    fun restoreBitmap(bitmap: Bitmap) {
        displayBitmap?.recycle()
        if (bitmap.isMutable) {
            displayBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            displayBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            bitmap.recycle()
        }
        displayCanvas = Canvas(displayBitmap!!)
        neverRedrawContentFromModels = true
        invalidate()
    }

    fun clearAll() {
        neverRedrawContentFromModels = false
        _strokes.clear()
        _shapes.clear()
        _texts.clear()
        eraseCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        lastKnownTexts.clear()
        contentCanvas?.drawColor(Color.WHITE)
        displayCanvas?.drawColor(Color.WHITE)
        invalidate()
    }

    fun needsRedrawPublic() {
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        contentBitmap?.recycle()
        displayBitmap?.recycle()
        eraseBitmap?.recycle()
        eraseBitmap = null
        contentBitmap = null
        displayBitmap = null
    }
}

