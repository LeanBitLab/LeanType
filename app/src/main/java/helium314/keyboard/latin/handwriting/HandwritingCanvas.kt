// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.handwriting

import android.annotation.SuppressLint
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.animation.doOnEnd

class HandwritingCanvas @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = context.resources.displayMetrics.density

    private val strokePaint = Paint().apply {
        color = 0xFF3F51B5.toInt() // Default blue, overridden by theme
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val guidelinePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
        pathEffect = DashPathEffect(floatArrayOf(10f * density, 8f * density), 0f)
        color = Color.argb(35, 128, 128, 128)
    }

    private val hintPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        textSize = 15f * context.resources.displayMetrics.scaledDensity
        color = Color.argb(45, 128, 128, 128)
    }

    private val path = Path()
    private val guidelinePath = Path()
    private val strokes = mutableListOf<FloatArray>()
    private var currentStroke = mutableListOf<Float>()
    private var startTime: Long = 0
    private var isRecognitionDone = false
    private var lastX: Float = 0f
    private var lastY: Float = 0f

    private var fadeAlpha: Float = 1.0f
    private var fadeAnimator: ValueAnimator? = null

    var hintText: String = "Write here"
    private var showHint: Boolean = true

    private val mainHandler = Handler(Looper.getMainLooper())
    private val recognitionTimeout = 700L
    private val recognizeRunnable = Runnable {
        isRecognitionDone = true
        onRecognitionTriggered?.invoke(ArrayList(strokes))
    }

    var onRecognitionTriggered: ((List<FloatArray>) -> Unit)? = null
    var onStrokeStarted: (() -> Unit)? = null

    fun setColors(strokeColor: Int, hintTextColor: Int) {
        strokePaint.color = strokeColor

        // Use ~14% alpha for guideline and ~22% for watermark hint
        val alphaGuideline = (Color.alpha(hintTextColor) * 0.14f).toInt().coerceIn(15, 60)
        val alphaHint = (Color.alpha(hintTextColor) * 0.25f).toInt().coerceIn(25, 90)

        guidelinePaint.color = Color.argb(
            alphaGuideline,
            Color.red(hintTextColor),
            Color.green(hintTextColor),
            Color.blue(hintTextColor)
        )
        hintPaint.color = Color.argb(
            alphaHint,
            Color.red(hintTextColor),
            Color.green(hintTextColor),
            Color.blue(hintTextColor)
        )
        invalidate()
    }

    fun setStrokeColor(color: Int) {
        strokePaint.color = color
        invalidate()
    }

    fun clear() {
        fadeAnimator?.cancel()
        fadeAnimator = null
        fadeAlpha = 1.0f
        strokePaint.alpha = 255
        mainHandler.removeCallbacks(recognizeRunnable)
        path.reset()
        strokes.clear()
        currentStroke.clear()
        isRecognitionDone = false
        showHint = true
        invalidate()
    }

    fun fadeOutAndClear(onComplete: (() -> Unit)? = null) {
        fadeAnimator?.cancel()
        fadeAnimator = ValueAnimator.ofFloat(1.0f, 0.0f).apply {
            duration = 160L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                fadeAlpha = animator.animatedValue as Float
                strokePaint.alpha = (255 * fadeAlpha).toInt()
                invalidate()
            }
            doOnEnd {
                clear()
                onComplete?.invoke()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        guidelinePath.reset()
        if (w > 0 && h > 0) {
            val margin = 24f * density
            // Baseline at ~65% height
            val baselineY = h * 0.65f
            guidelinePath.moveTo(margin, baselineY)
            guidelinePath.lineTo(w - margin, baselineY)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw guideline baseline
        if (!guidelinePath.isEmpty) {
            canvas.drawPath(guidelinePath, guidelinePaint)
        }

        // Draw empty-state hint watermark
        if (showHint && hintText.isNotEmpty()) {
            val cx = width / 2f
            val cy = height * 0.45f
            canvas.drawText(hintText, cx, cy, hintPaint)
        }

        // Draw ink strokes
        canvas.drawPath(path, strokePaint)
    }

    @SuppressLint("ClickableViewAccessibility") // draws strokes, not a click target
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val time = event.eventTime

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                fadeAnimator?.cancel()
                fadeAlpha = 1.0f
                strokePaint.alpha = 255
                showHint = false

                mainHandler.removeCallbacks(recognizeRunnable)
                if (isRecognitionDone) {
                    onStrokeStarted?.invoke()
                    isRecognitionDone = false
                }
                path.moveTo(x, y)
                lastX = x
                lastY = y
                startTime = time
                currentStroke.clear()
                currentStroke.add(x)
                currentStroke.add(y)
                currentStroke.add(0f) // Relative time start
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                // Bezier curve interpolation between touch points for organic ink flow
                val midX = (lastX + x) / 2f
                val midY = (lastY + y) / 2f
                path.quadTo(lastX, lastY, midX, midY)
                lastX = x
                lastY = y

                currentStroke.add(x)
                currentStroke.add(y)
                currentStroke.add((time - startTime).toFloat())
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                path.lineTo(x, y)
                currentStroke.add(x)
                currentStroke.add(y)
                currentStroke.add((time - startTime).toFloat())
                strokes.add(currentStroke.toFloatArray())
                currentStroke.clear()
                invalidate()

                mainHandler.postDelayed(recognizeRunnable, recognitionTimeout)
            }
        }
        return true
    }
}
