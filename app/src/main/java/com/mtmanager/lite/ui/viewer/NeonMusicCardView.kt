package com.mtmanager.lite.ui.viewer

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

class NeonMusicCardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, def: Int = 0
) : View(context, attrs, def) {

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    private val barCount = 46
    private val barH   = FloatArray(barCount) { i -> bell(i) * 0.3f }
    private val barTgt = FloatArray(barCount) { i -> bell(i) * 0.35f }
    private val barPhi = FloatArray(barCount) { i -> i * 0.18f }

    var isPlaying = false
    private var phase = 0.0

    private fun bell(i: Int): Float {
        val x = (i - barCount / 2.0) / (barCount / 3.5)
        return exp(-x * x).toFloat()
    }
    private fun lerp(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a)   + (Color.red(b)   - Color.red(a))   * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a)  + (Color.blue(b)  - Color.blue(a))  * t).toInt()
    )

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w == 0f) return

        val pad = w * 0.07f
        val card = RectF(pad, pad, w - pad, h - pad)
        val cr = card.width() * 0.20f

        drawGlow(canvas, card, cr)
        drawBody(canvas, card, cr)
        drawShine(canvas, card, cr)
        drawBorder(canvas, card, cr)
        drawBars(canvas, card)
        drawNote(canvas, w, card)

        phase += if (isPlaying) 0.20 else 0.045
        postInvalidateDelayed(30)
    }

    private fun drawGlow(c: Canvas, r: RectF, cr: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        listOf(
            Triple(r.width()*0.10f, 0x14A855F7.toInt(), 0x144F8EF7.toInt()),
            Triple(r.width()*0.055f,0x28A855F7.toInt(), 0x284FBAFF.toInt()),
            Triple(r.width()*0.025f,0x55CC55FF.toInt(), 0x554FBAFF.toInt()),
        ).forEach { (sw, sc, ec) ->
            p.strokeWidth = sw
            p.shader = LinearGradient(r.left, r.centerY(), r.right, r.centerY(),
                intArrayOf(sc, ec), null, Shader.TileMode.CLAMP)
            c.drawRoundRect(r, cr + sw/2, cr + sw/2, p)
        }
    }

    private fun drawBody(c: Canvas, r: RectF, cr: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(r.left + r.width()*0.35f, r.top + r.height()*0.25f,
            r.width()*0.9f, intArrayOf(0xF50E162A.toInt(), 0xF5060C18.toInt()),
            null, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, cr, cr, p)
    }

    private fun drawShine(c: Canvas, r: RectF, cr: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val bot = r.top + r.height() * 0.48f
        p.shader = LinearGradient(0f, r.top, 0f, bot,
            intArrayOf(0x35FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        c.save(); c.clipRect(r.left, r.top, r.right, bot)
        c.drawRoundRect(r, cr, cr, p); c.restore()
    }

    private fun drawBorder(c: Canvas, r: RectF, cr: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        // Blurred glow border
        p.strokeWidth = 7f
        p.maskFilter  = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
        p.shader = LinearGradient(r.left, r.centerY(), r.right, r.centerY(),
            intArrayOf(0xDD4FBAFF.toInt(), 0xDDCC55FF.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, cr, cr, p)
        // Sharp rim
        p.strokeWidth = 2f; p.maskFilter = null
        p.shader = LinearGradient(r.left, r.centerY(), r.right, r.centerY(),
            intArrayOf(0xFF99EEFF.toInt(), 0xFFDD99FF.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, cr, cr, p)
    }

    private fun drawBars(c: Canvas, card: RectF) {
        val aL = card.left + 14f; val aR = card.right - 14f
        val aB = card.bottom - 12f; val aT = card.top + card.height() * 0.54f
        val bw = (aR - aL - 2.5f * (barCount - 1)) / barCount
        val aH = aB - aT
        val p  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        c.save(); c.clipRect(card.left+12, card.top+10, card.right-12, card.bottom-10)
        for (i in 0 until barCount) {
            val t = i.toFloat() / (barCount - 1)
            barTgt[i] = if (isPlaying)
                ((0.38f + 0.58f * sin(phase + barPhi[i]).toFloat()) * bell(i) + 0.06f).coerceIn(0.06f, 0.96f)
            else
                ((0.10f + 0.20f * sin(phase*0.5 + barPhi[i]).toFloat()) * bell(i) + 0.04f).coerceIn(0.04f, 0.50f)
            barH[i] += (barTgt[i] - barH[i]) * 0.13f
            val bH = aH * barH[i]
            val lx = aL + i * (bw + 2.5f)
            p.color = lerp(0xFF4F8EF7.toInt(), 0xFFA855F7.toInt(), t); p.alpha = 215
            c.drawRoundRect(lx, aB - bH, lx + bw, aB, 3f, 3f, p)
        }
        c.restore()
    }

    private fun drawNote(c: Canvas, w: Float, card: RectF) {
        val cw  = card.width()
        val ncx = card.centerX()
        // Note occupies upper 45% of card; centre point at 26%
        val ncy = card.top + card.height() * 0.26f

        // All sizes relative to card inner width — scales on every screen
        val headRx = cw * 0.090f   // head half-width
        val headRy = cw * 0.058f   // head half-height
        val stemW  = cw * 0.028f   // stem thickness
        val beamH  = cw * 0.046f   // beam bar height
        val stemH  = cw * 0.200f   // stem length (beam-bottom → head-top)

        // Head centres
        val lhx = ncx - cw * 0.155f
        val rhx = ncx + cw * 0.060f
        val lhy = ncy + stemH + beamH + headRy  // left head (lower)
        val rhy = ncy + stemH * 0.82f + beamH + headRy  // right head (slightly higher)

        val beamTop = ncy
        val beamBot = beamTop + beamH

        val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(lhx - headRx, 0f, rhx + headRx, 0f,
                intArrayOf(0xFF50C8FF.toInt(), 0xFFCC55FF.toInt()), null, Shader.TileMode.CLAMP)
            maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.SOLID)
        }

        // Beam
        c.drawRoundRect(lhx - stemW/2, beamTop, rhx + stemW/2, beamBot, beamH/2, beamH/2, lp)
        // Left stem
        c.drawRoundRect(lhx - stemW/2, beamBot, lhx + stemW/2, lhy - headRy, stemW/2, stemW/2, lp)
        // Right stem
        c.drawRoundRect(rhx - stemW/2, beamBot, rhx + stemW/2, rhy - headRy, stemW/2, stemW/2, lp)

        // Left head — blue radial
        lp.maskFilter = null
        lp.shader = RadialGradient(lhx - headRx*0.3f, lhy - headRy*0.4f, headRx*1.5f,
            intArrayOf(0xFF90E0FF.toInt(), 0xFF2266EE.toInt()), null, Shader.TileMode.CLAMP)
        c.drawOval(lhx - headRx, lhy - headRy, lhx + headRx, lhy + headRy, lp)

        // Right head — purple radial
        lp.shader = RadialGradient(rhx - headRx*0.3f, rhy - headRy*0.4f, headRx*1.5f,
            intArrayOf(0xFFEEAAFF.toInt(), 0xFF9922CC.toInt()), null, Shader.TileMode.CLAMP)
        c.drawOval(rhx - headRx, rhy - headRy, rhx + headRx, rhy + headRy, lp)

        // Glossy inner highlights
        lp.shader = null; lp.color = 0x66FFFFFF
        c.drawOval(lhx - headRx*0.55f, lhy - headRy*0.70f, lhx - headRx*0.10f, lhy - headRy*0.10f, lp)
        c.drawOval(rhx - headRx*0.55f, rhy - headRy*0.70f, rhx - headRx*0.10f, rhy - headRy*0.10f, lp)
    }
}
