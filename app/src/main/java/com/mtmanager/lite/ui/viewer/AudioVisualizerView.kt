package com.mtmanager.lite.ui.viewer

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

/**
 * Bell-curve audio visualizer.
 * – Bars fill the full width, tallest in the middle like the reference image.
 * – Horizontal color gradient: blue (left) → purple (right).
 * – Smooth lerp animation, separate idle / playing speed.
 */
class AudioVisualizerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val barCount = 46
    private val barGap   = 2.5f
    private val corner   = 3f

    private val heights = FloatArray(barCount) { i -> bellEnvelope(i) * 0.35f }
    private val targets = FloatArray(barCount) { i -> bellEnvelope(i) * 0.40f }
    private val phaseOff = FloatArray(barCount) { i -> i * 0.18f }

    var isPlaying = false
    private var animPhase = 0.0

    /** Gaussian bell-curve envelope [0..1], tallest at centre. */
    private fun bellEnvelope(i: Int): Float {
        val x = (i - barCount / 2.0) / (barCount / 3.5)
        return exp(-x * x).toFloat()
    }

    /** Linearly interpolate two ARGB colours. */
    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        val r = (Color.red(a)   + (Color.red(b)   - Color.red(a))   * t).toInt()
        val g = (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt()
        val bl= (Color.blue(a)  + (Color.blue(b)  - Color.blue(a))  * t).toInt()
        return Color.rgb(r, g, bl)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val barW = (w - barGap * (barCount - 1)) / barCount

        for (i in 0 until barCount) {
            val bell = bellEnvelope(i)
            val t    = i.toFloat() / (barCount - 1)            // 0=left, 1=right

            targets[i] = if (isPlaying) {
                val wave = 0.35f + 0.60f * sin(animPhase + phaseOff[i]).toFloat()
                (wave * bell + 0.06f).coerceIn(0.06f, 0.96f)
            } else {
                val idle = 0.12f + 0.22f * sin(animPhase * 0.55 + phaseOff[i]).toFloat()
                (idle * bell + 0.04f).coerceIn(0.04f, 0.52f)
            }

            heights[i] += (targets[i] - heights[i]) * 0.13f

            val barH  = (h * heights[i]).coerceAtLeast(4f)
            val left  = i * (barW + barGap)
            val top   = h - barH
            val right = left + barW

            // Horizontal gradient: #4F8EF7 (blue) → #A855F7 (purple)
            barPaint.color = lerpColor(0xFF4F8EF7.toInt(), 0xFFA855F7.toInt(), t)
            barPaint.alpha = 210

            canvas.drawRoundRect(left, top, right, h, corner, corner, barPaint)
        }

        animPhase += if (isPlaying) 0.20 else 0.045
        postInvalidateDelayed(30)
    }
}
