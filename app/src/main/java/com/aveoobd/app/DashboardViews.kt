package com.aveoobd.app

import android.content.Context
import android.graphics.*
import android.view.View

object ObdState {
    @Volatile var latestData: ObdData? = null
    @Volatile var statusText: String = "BAĞLI DEĞİL"
    @Volatile var isConnected: Boolean = false
    @Volatile var maxSessionRpm: Int = 0
    @Volatile var maxSessionSpeed: Int = 0

    fun estimateGear(rpm: Int, speed: Int): String {
        if (speed < 3) return "P/N"
        val ratio = rpm.toFloat() / speed.toFloat()
        return when {
            ratio > 95f -> "D1"
            ratio > 60f -> "D2"
            ratio > 43f -> "D3"
            ratio > 33f -> "D4"
            ratio > 26f -> "D5"
            else -> "D6"
        }
    }
}

class RpmGaugeView(context: Context) : View(context) {
    var rpm: Int = 0
    var speed: Int = 0
    var maxRpm: Int = 0
    var maxSpeed: Int = 0

    private val bgArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 22f
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#1E293B")
    }
    private val activeArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 22f
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#00E5FF")
    }
    private val redZonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 22f
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#66FF1744")
    }
    private val rpmTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val speedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat()
        val pad = 28f
        val left = (width - size) / 2f + pad
        val top = (height - size) / 2f + pad
        rect.set(left, top, left + size - 2 * pad, top + size - 2 * pad)

        // Arka plan yayı (135 dereceden 270 derece)
        canvas.drawArc(rect, 135f, 270f, false, bgArcPaint)
        // Kırmızı bölge (6000 - 8000 RPM)
        canvas.drawArc(rect, 135f + 270f * 0.75f, 270f * 0.25f, false, redZonePaint)

        val ratio = (rpm.toFloat() / 8000f).coerceIn(0f, 1f)
        activeArcPaint.color = when {
            rpm > 4500 -> Color.parseColor("#FF1744")
            rpm > 3000 -> Color.parseColor("#FFC400")
            else -> Color.parseColor("#00E5FF")
        }
        canvas.drawArc(rect, 135f, 270f * ratio, false, activeArcPaint)

        val cx = width / 2f
        val cy = height / 2f

        rpmTextPaint.textSize = size * 0.20f
        canvas.drawText("$rpm", cx, cy - size * 0.02f, rpmTextPaint)

        subTextPaint.textSize = size * 0.065f
        canvas.drawText("RPM", cx, cy + size * 0.07f, subTextPaint)

        speedTextPaint.textSize = size * 0.14f
        canvas.drawText("$speed km/s", cx, cy + size * 0.23f, speedTextPaint)

        subTextPaint.textSize = size * 0.055f
        canvas.drawText("MAKS: $maxRpm RPM | $maxSpeed km/s", cx, cy + size * 0.35f, subTextPaint)
    }
}

class GradientBarView(context: Context) : View(context) {
    var progress: Float = 0f // 0f..1f
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E293B") }
    private val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val r = height / 2f
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, r, r, bgPaint)

        val fillW = (width * progress.coerceIn(0f, 1f)).coerceAtLeast(if (progress > 0f) height.toFloat() else 0f)
        if (fillW > 0f) {
            fgPaint.shader = LinearGradient(
                0f, 0f, width.toFloat(), 0f,
                intArrayOf(
                    Color.parseColor("#00E5FF"),
                    Color.parseColor("#00E676"),
                    Color.parseColor("#FFC400"),
                    Color.parseColor("#FF1744")
                ),
                null,
                Shader.TileMode.CLAMP
            )
            rect.set(0f, 0f, fillW, height.toFloat())
            canvas.drawRoundRect(rect, r, r, fgPaint)
        }
    }
}
