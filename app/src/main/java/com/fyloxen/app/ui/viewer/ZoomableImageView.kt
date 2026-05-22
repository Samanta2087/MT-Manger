package com.fyloxen.app.ui.viewer

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

/**
 * ImageView with stable pinch-to-zoom and drag.
 *
 * Key design decisions that eliminate shaking:
 *  1.  baseMatrix  = the FIT_CENTER equivalent (immutable after each new image)
 *  2.  animMatrix  = user zoom/pan ON TOP of base (mutable)
 *  3.  Clamping is NEVER applied during an active scale gesture —
 *      it only runs when the gesture ENDS or finger is lifted.
 *      This prevents the clamp from fighting the pinch and oscillating.
 *  4.  Pan delta is divided by base scale so the image always tracks
 *      the finger 1-to-1 regardless of the base fit scale.
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private val baseMatrix = Matrix()  // fit-center (never changes after setupBase)
    private val animMatrix = Matrix()  // user zoom/pan on top of base
    private val invBase    = Matrix()  // cached inverse of baseMatrix
    private val scratch    = FloatArray(9)

    /** 1.0 = fit-center, 2.0 = zoomed 2× beyond fit-center, etc. */
    var currentScale = 1f
        private set

    private val MIN_SCALE = 1f
    private val MAX_SCALE = 6f

    private var lastX = 0f
    private var lastY = 0f
    private var activePtr = MotionEvent.INVALID_POINTER_ID

    // ── Scale detector ────────────────────────────────────────────────────────
    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

            override fun onScaleBegin(d: ScaleGestureDetector): Boolean {
                lockParent(true)
                return true
            }

            override fun onScale(d: ScaleGestureDetector): Boolean {
                val factor = when {
                    currentScale * d.scaleFactor < MIN_SCALE -> MIN_SCALE / currentScale
                    currentScale * d.scaleFactor > MAX_SCALE -> MAX_SCALE / currentScale
                    else -> d.scaleFactor
                }
                currentScale *= factor

                // Pivot is in VIEW space; map into animMatrix's input space via invBase
                val pt = floatArrayOf(d.focusX, d.focusY)
                invBase.mapPoints(pt)
                animMatrix.postScale(factor, factor, pt[0], pt[1])

                // ⚠️ commit WITHOUT clamp — clamping during scale causes shaking
                commit()
                return true
            }

            override fun onScaleEnd(d: ScaleGestureDetector) {
                // Safe to clamp now that the gesture has finished
                clampAndCommit()
            }
        })

    // ── Double-tap detector ───────────────────────────────────────────────────
    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (currentScale > 1.2f) {
                    // Reset to fit-center
                    animMatrix.reset()
                    currentScale = 1f
                } else {
                    val sf = 2.5f / currentScale
                    currentScale = 2.5f
                    val pt = floatArrayOf(e.x, e.y)
                    invBase.mapPoints(pt)
                    animMatrix.postScale(sf, sf, pt[0], pt[1])
                }
                clampAndCommit()
                return true
            }
        })

    init { scaleType = ScaleType.MATRIX }

    // ── Setup base matrix after layout or new image ───────────────────────────
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (changed) setupBase()
    }

    override fun setImageBitmap(bm: android.graphics.Bitmap?) {
        super.setImageBitmap(bm)
        animMatrix.reset()
        currentScale = 1f
        if (width > 0 && height > 0) setupBase() else post { setupBase() }
    }

    private fun setupBase() {
        val d  = drawable ?: return
        val dW = d.intrinsicWidth.toFloat()
        val dH = d.intrinsicHeight.toFloat()
        val vW = width.toFloat()
        val vH = height.toFloat()
        if (dW <= 0 || dH <= 0 || vW <= 0 || vH <= 0) return

        val s = min(vW / dW, vH / dH)
        baseMatrix.reset()
        baseMatrix.postScale(s, s)
        baseMatrix.postTranslate((vW - dW * s) / 2f, (vH - dH * s) / 2f)
        baseMatrix.invert(invBase)
        commit()
    }

    private fun commit() {
        val m = Matrix(baseMatrix)
        m.postConcat(animMatrix)
        imageMatrix = m
    }

    /**
     * Clamps the image so it never drifts off-screen.
     * Correction is applied in animMatrix space (= view-space delta / base scale).
     */
    private fun clampAndCommit() {
        val d = drawable ?: run { commit(); return }

        // Build the combined display matrix
        val combined = Matrix(baseMatrix)
        combined.postConcat(animMatrix)
        combined.getValues(scratch)

        val scale = scratch[Matrix.MSCALE_X]
        val tx    = scratch[Matrix.MTRANS_X]
        val ty    = scratch[Matrix.MTRANS_Y]
        val imgW  = d.intrinsicWidth  * scale
        val imgH  = d.intrinsicHeight * scale
        val vW    = width.toFloat()
        val vH    = height.toFloat()

        // Needed correction in VIEW space
        val dx = when {
            imgW <= vW   -> (vW - imgW) / 2f - tx   // centre horizontally
            tx > 0f      -> -tx                       // left edge drifted right
            tx + imgW < vW -> vW - tx - imgW          // right edge drifted left
            else         -> 0f
        }
        val dy = when {
            imgH <= vH   -> (vH - imgH) / 2f - ty
            ty > 0f      -> -ty
            ty + imgH < vH -> vH - ty - imgH
            else         -> 0f
        }

        if (dx != 0f || dy != 0f) {
            // Convert VIEW-space correction → animMatrix-space (divide by base scale)
            baseMatrix.getValues(scratch)
            val bs = scratch[Matrix.MSCALE_X].takeIf { it > 0f } ?: 1f
            animMatrix.postTranslate(dx / bs, dy / bs)
        }
        commit()
    }

    // ── Touch handling ────────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x; lastY = event.y
                activePtr = event.getPointerId(0)
                if (currentScale > 1f) lockParent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> lockParent(true)
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && currentScale > 1f) {
                    lockParent(true)
                    val idx = event.findPointerIndex(activePtr)
                    if (idx >= 0) {
                        val dx = event.getX(idx) - lastX
                        val dy = event.getY(idx) - lastY
                        lastX = event.getX(idx)
                        lastY = event.getY(idx)

                        // Divide by base scale so finger and image move 1:1
                        baseMatrix.getValues(scratch)
                        val bs = scratch[Matrix.MSCALE_X].takeIf { it > 0f } ?: 1f
                        animMatrix.postTranslate(dx / bs, dy / bs)
                        clampAndCommit()
                    }
                } else if (currentScale <= 1f) {
                    lockParent(false)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePtr = MotionEvent.INVALID_POINTER_ID
                // Final clamp on finger lift (catches any drift from fast flings)
                if (currentScale > 1f) clampAndCommit()
                else lockParent(false)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val idx = event.actionIndex
                if (event.getPointerId(idx) == activePtr) {
                    val ni = if (idx == 0) 1 else 0
                    lastX = event.getX(ni); lastY = event.getY(ni)
                    activePtr = event.getPointerId(ni)
                }
            }
        }
        return true
    }

    private fun lockParent(lock: Boolean) {
        parent?.requestDisallowInterceptTouchEvent(lock)
    }
}
