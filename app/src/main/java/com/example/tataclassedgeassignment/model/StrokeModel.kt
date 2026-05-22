package com.example.tataclassedgeassignment.model

import com.google.gson.annotations.SerializedName
import java.util.UUID

data class StrokeModel(
    @Transient val id: String = UUID.randomUUID().toString(), // ← @Transient excludes from Gson
    @SerializedName("points") val points: List<List<Float>>,
    @SerializedName("color") val color: String,
    @SerializedName("width") val width: Float,
    @SerializedName("isEraser") val isEraser: Boolean = false
)

data class ShapeModel(
    @Transient val id: String = UUID.randomUUID().toString(),
    @SerializedName("type") val type: String,
    @SerializedName("startX") val startX: Float,
    @SerializedName("startY") val startY: Float,
    @SerializedName("endX") val endX: Float,
    @SerializedName("endY") val endY: Float,
    @SerializedName("color") val color: String,
    @SerializedName("strokeWidth") val strokeWidth: Float
)

data class TextModel(
    @Transient val id: String = UUID.randomUUID().toString(),
    @SerializedName("text") val text: String,
    @SerializedName("positionX") val positionX: Float,
    @SerializedName("positionY") val positionY: Float,
    @SerializedName("color") val color: String,
    @SerializedName("size") val size: Float
)

data class WhiteboardData(
    @SerializedName("strokes") val strokes: List<StrokeModel>,
    @SerializedName("shapes") val shapes: List<ShapeModel>,
    @SerializedName("texts") val texts: List<TextModel>
)