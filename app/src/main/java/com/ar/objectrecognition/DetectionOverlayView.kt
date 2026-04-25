package com.ar.objectrecognition

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class DetectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var detectionResults: List<DetectionResult> = emptyList()
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    private val boxPaint = Paint().apply {
        color = Color.parseColor("#00FF00")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#CC000000")
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        isAntiAlias = true
    }

    private val labelPaint = Paint().apply {
        color = Color.parseColor("#FFEB3B")
        textSize = 28f
        isAntiAlias = true
    }

    fun updateResults(results: List<DetectionResult>, imgWidth: Int, imgHeight: Int) {
        detectionResults = results
        imageWidth = imgWidth
        imageHeight = imgHeight
        invalidate()
    }

    fun clear() {
        detectionResults = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (detectionResults.isEmpty() || imageWidth <= 1 || imageHeight <= 1) {
            return
        }

        val scaleX = width.toFloat() / imageWidth
        val scaleY = height.toFloat() / imageHeight

        for (result in detectionResults) {
            val box = result.boundingBox

            val left = box.left * scaleX
            val top = box.top * scaleY
            val right = box.right * scaleX
            val bottom = box.bottom * scaleY

            val rect = RectF(left, top, right, bottom)
            canvas.drawRect(rect, boxPaint)

            val labelText = "${result.label} (${String.format("%.0f", result.confidence * 100)}%)"

            val textWidth = textPaint.measureText(labelText)
            val textPadding = 8f
            val textHeight = textPaint.textSize

            canvas.drawRoundRect(
                left - textPadding,
                top - textHeight - textPadding * 2,
                left + textWidth + textPadding * 2,
                top,
                8f, 8f,
                backgroundPaint
            )
            canvas.drawText(
                labelText,
                left,
                top - textPadding,
                textPaint
            )

            result.annotation?.let { annotation ->
                val annText = "备注: $annotation"
                val annWidth = labelPaint.measureText(annText)

                canvas.drawRoundRect(
                    left - textPadding,
                    top + textPadding,
                    left + annWidth + textPadding * 2,
                    top + textHeight + textPadding * 2,
                    8f, 8f,
                    backgroundPaint
                )
                canvas.drawText(
                    annText,
                    left,
                    top + textHeight + textPadding,
                    labelPaint
                )
            }
        }
    }
}
