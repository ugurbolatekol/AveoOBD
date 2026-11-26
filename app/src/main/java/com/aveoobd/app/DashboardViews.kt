package com.aveoobd.app

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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

class LedEcoBarView(context: Context) : View(context) {
    var progress: Float = 0.25f
    private val segmentCount = 32
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#080D14")
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E293B")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val segPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        rect.set(2f, 2f, w - 2f, h - 2f)
        canvas.drawRoundRect(rect, 14f, 14f, framePaint)
        canvas.drawRoundRect(rect, 14f, 14f, borderPaint)

        val padX = 12f
        val padY = 8f
        val gap = 5f
        val usableW = w - 2 * padX
        val segW = (usableW - gap * (segmentCount - 1)) / segmentCount
        val activeCount = (progress.coerceIn(0f, 1f) * segmentCount).toInt()

        for (i in 0 until segmentCount) {
            val frac = i.toFloat() / segmentCount
            val baseColor = when {
                frac < 0.45f -> Color.parseColor("#00E676")
                frac < 0.70f -> Color.parseColor("#FFC400")
                frac < 0.85f -> Color.parseColor("#FF6D00")
                else -> Color.parseColor("#FF1744")
            }
            segPaint.color = if (i < activeCount) baseColor else Color.argb(
                45, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)
            )
            val left = padX + i * (segW + gap)
            rect.set(left, padY, left + segW, h - padY)
            canvas.drawRoundRect(rect, 6f, 6f, segPaint)
        }
    }
}

class RpmGaugeView(context: Context) : View(context) {
    var rpm: Int = 0
    var speed: Int = 0
    var maxRpm: Int = 0
    var maxSpeed: Int = 0

    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#334155")
    }
    private val bgArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 20f
        color = Color.parseColor("#121B2A")
    }
    private val activeArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 20f
        color = Color.parseColor("#00C8FF")
    }
    private val redZonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 20f
        color = Color.parseColor("#CCFF1744")
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val rpmTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00D8FF")
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
        if (size <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        val radius = size * 0.44f

        canvas.drawCircle(cx, cy, radius + 14f, outerRingPaint)

        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(rect, 135f, 270f, false, bgArcPaint)
        canvas.drawArc(rect, 135f + 270f * 0.8125f, 270f * 0.1875f, false, redZonePaint)

        val ratio = (rpm.toFloat() / 8000f).coerceIn(0f, 1f)
        activeArcPaint.color = when {
            rpm > 6000 -> Color.parseColor("#FF1744")
            rpm > 3500 -> Color.parseColor("#FFC400")
            else -> Color.parseColor("#00C8FF")
        }
        canvas.drawArc(rect, 135f, 270f * ratio, false, activeArcPaint)

        val totalTicks = 32
        numPaint.textSize = size * 0.065f
        for (i in 0..totalTicks) {
            val deg = 135.0 + (270.0 * i / totalTicks)
            val rad = deg * PI / 180.0
            val isMajor = (i % 4 == 0)
            val isRed = (i >= 26)

            tickPaint.strokeWidth = if (isMajor) 5f else 2.5f
            tickPaint.color = if (isRed) Color.parseColor("#FF1744") else Color.parseColor("#E2E8F0")

            val rOuter = radius + 8f
            val rInner = radius - if (isMajor) 16f else 8f
            canvas.drawLine(
                (cx + rInner * cos(rad)).toFloat(),
                (cy + rInner * sin(rad)).toFloat(),
                (cx + rOuter * cos(rad)).toFloat(),
                (cy + rOuter * sin(rad)).toFloat(),
                tickPaint
            )

            if (isMajor) {
                val label = (i / 4).toString()
                val rNum = radius - 34f
                numPaint.color = if (isRed) Color.parseColor("#FF1744") else Color.parseColor("#CBD5E1")
                val tx = (cx + rNum * cos(rad)).toFloat()
                val ty = (cy + rNum * sin(rad)).toFloat() + numPaint.textSize * 0.35f
                canvas.drawText(label, tx, ty, numPaint)
            }
        }

        subTextPaint.textSize = size * 0.055f
        canvas.drawText("RPM", cx, cy - size * 0.14f, subTextPaint)

        rpmTextPaint.textSize = size * 0.20f
        canvas.drawText("$rpm", cx, cy + size * 0.02f, rpmTextPaint)

        speedTextPaint.textSize = size * 0.14f
        canvas.drawText("$speed", cx, cy + size * 0.20f, speedTextPaint)

        subTextPaint.textSize = size * 0.052f
        canvas.drawText("km/h", cx, cy + size * 0.27f, subTextPaint)

        subTextPaint.textSize = size * 0.046f
        canvas.drawText("MAKS: $maxRpm | $maxSpeed km/s", cx, cy + size * 0.39f, subTextPaint)
    }
}

class GradientBarView(context: Context) : View(context) {
    var progress: Float = 0f
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F172A") }
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
