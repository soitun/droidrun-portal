package com.mobilerun.portal.ui.taskprompt

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

data class StatusSlice(
    val label: String,
    val count: Int,
    val color: Int,
)

class SuccessRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var slices: List<StatusSlice> = emptyList()
    private var total: Int = 0

    private val slicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF2A2A2A.toInt()
        strokeCap = Paint.Cap.ROUND
    }

    private val oval = RectF()
    private val density = resources.displayMetrics.density
    private val strokeWidth = 8f * density
    private val gapDegrees = 3f

    init {
        slicePaint.strokeWidth = strokeWidth
        emptyPaint.strokeWidth = strokeWidth
    }

    fun setData(slices: List<StatusSlice>) {
        this.slices = slices.filter { it.count > 0 }.sortedByDescending { it.count }
        this.total = this.slices.sumOf { it.count }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = (72 * density).toInt()
        val w = resolveSize(desired, widthMeasureSpec)
        val h = resolveSize(desired, heightMeasureSpec)
        val size = minOf(w, h)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val halfStroke = strokeWidth / 2f
        oval.set(
            halfStroke + paddingLeft,
            halfStroke + paddingTop,
            width - halfStroke - paddingRight,
            height - halfStroke - paddingBottom,
        )

        if (total == 0) {
            canvas.drawArc(oval, 0f, 360f, false, emptyPaint)
            return
        }

        if (slices.size == 1) {
            slicePaint.color = slices[0].color
            canvas.drawArc(oval, -90f, 360f, false, slicePaint)
            return
        }

        val totalGap = gapDegrees * slices.size
        val availableSweep = 360f - totalGap
        var startAngle = -90f
        var sweepUsed = 0f

        for ((i, slice) in slices.withIndex()) {
            val sweep = if (i == slices.lastIndex) {
                availableSweep - sweepUsed
            } else {
                slice.count.toFloat() / total * availableSweep
            }
            slicePaint.color = slice.color
            canvas.drawArc(oval, startAngle, sweep, false, slicePaint)
            sweepUsed += sweep
            startAngle += sweep + gapDegrees
        }
    }
}
