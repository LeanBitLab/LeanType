// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import helium314.keyboard.latin.utils.dpToPx
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class VoiceVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Mode {
        IDLE,
        CONNECTING,
        RECORDING,
        PROCESSING
    }

    private var mode = Mode.IDLE
    private var baseColor = 0xFF4285F4.toInt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = baseColor
    }

    private val barRect = RectF()
    private var phase = 0f
    private var animator: ValueAnimator? = null
    private var lastInvalidateTime = 0L

    var audioLevelProvider: (() -> Float)? = null
    private var smoothedLevel = 0f

    private val barWidth = 3.dpToPx(resources).toFloat()
    private val barSpacing = 3.dpToPx(resources).toFloat()
    private val slotWidth = barWidth + barSpacing
    private val minHeight = barWidth
    private val connectingWaveExtra = 4.dpToPx(resources).toFloat()
    private val processingWaveExtra = 5.dpToPx(resources).toFloat()
    private val idleBreathingExtra = 4.dpToPx(resources).toFloat()
    private val minHeightExtra = 8.dpToPx(resources).toFloat()
    private val sidePadding = 6.dpToPx(resources).toFloat()

    init {
        val defaultColor = 0xFF4285F4.toInt()
        setColor(defaultColor)
    }

    fun setColor(color: Int) {
        baseColor = color
        paint.color = color
        invalidate()
    }

    fun setMode(newMode: Mode) {
        if (mode == newMode) return
        mode = newMode
        if (newMode != Mode.RECORDING) {
            smoothedLevel = 0f
        }
        updateAnimation()
        invalidate()
    }

    private fun stopAnimation() {
        animator?.cancel()
        animator = null
        smoothedLevel = 0f
    }

    private fun updateAnimation() {
        animator?.cancel()
        animator = null

        if (mode == Mode.IDLE || !isAttachedToWindow || visibility != VISIBLE || windowVisibility != VISIBLE) {
            smoothedLevel = 0f
            return
        }

        val duration = when (mode) {
            Mode.CONNECTING -> 1200L
            Mode.RECORDING -> 1000L
            Mode.PROCESSING -> 800L
            Mode.IDLE -> 0L
        }

        if (duration > 0) {
            animator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
                this.duration = duration
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { va ->
                    phase = va.animatedValue as Float
                    if (mode == Mode.RECORDING) {
                        val target = audioLevelProvider?.invoke() ?: 0f
                        val smoothingFactor = if (target > smoothedLevel) 0.35f else 0.12f
                        smoothedLevel += (target - smoothedLevel) * smoothingFactor
                    }
                    val now = SystemClock.uptimeMillis()
                    val isQuiet = mode == Mode.RECORDING && smoothedLevel <= 0.05f
                    if (!isQuiet || (now - lastInvalidateTime) >= 32L) {
                        lastInvalidateTime = now
                        invalidate()
                    }
                }
                start()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            updateAnimation()
        } else {
            stopAnimation()
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            updateAnimation()
        } else {
            stopAnimation()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mode == Mode.IDLE) return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f) return

        val availableWidth = viewWidth - sidePadding * 2f
        if (availableWidth <= 0f) return

        val rawCount = ((availableWidth + barSpacing) / slotWidth).toInt()
        val barCount = rawCount.let { if (it % 2 == 0) it - 1 else it }.coerceAtLeast(7)
        val totalBarsWidth = barCount * barWidth + (barCount - 1) * barSpacing

        var startX = (viewWidth - totalBarsWidth) / 2f
        val centerY = viewHeight / 2f
        val maxHeight = (viewHeight * 0.65f).coerceAtLeast(minHeight + minHeightExtra)
        val centerIndex = (barCount - 1) / 2f
        val radius = barWidth / 2f

        val parsedAlpha = Color.alpha(baseColor)
        val baseAlpha = if (parsedAlpha > 0) parsedAlpha else 255
        val twoPi = (2 * Math.PI).toFloat()
        val piHalf = (Math.PI / 2).toFloat()
        val level = smoothedLevel.coerceIn(0f, 1f)
        val idleWeight = (1f - level * 3f).coerceIn(0f, 1f)

        for (i in 0 until barCount) {
            val normDist = if (centerIndex > 0f) abs(i - centerIndex) / centerIndex else 0f
            val envelope = 0.20f + 0.80f * cos(normDist * piHalf)
            val u = if (barCount > 1) i.toFloat() / (barCount - 1) else 0.5f

            val barHeight = when (mode) {
                Mode.RECORDING -> {
                    paint.alpha = baseAlpha

                    // Smooth resting breathing wave (synchronized integer harmonics)
                    val idleWave = (sin(phase - twoPi * u) + 1f) / 2f
                    val idleHeight = minHeight + idleWave * idleBreathingExtra * envelope

                    // Organic acoustic harmonic wave during speech
                    val w1 = sin(phase * 2f - twoPi * u)
                    val w2 = cos(phase - twoPi * 2f * u)
                    val microFactor = 0.70f + 0.30f * ((w1 + w2) / 2f)
                    val dynamicExpansion = (level * envelope * microFactor).coerceIn(0f, 1f)
                    val activeHeight = minHeight + dynamicExpansion * (maxHeight - minHeight)

                    // Continuous cross-fade: zero pops when speech begins or pauses
                    activeHeight + idleWeight * (idleHeight - minHeight)
                }
                Mode.CONNECTING -> {
                    val wave = (sin(phase - twoPi * u) + 1f) / 2f
                    val shimmer = 0.35f + 0.65f * wave
                    paint.alpha = (baseAlpha * shimmer).toInt().coerceIn(0, 255)
                    minHeight + wave * envelope * connectingWaveExtra
                }
                Mode.PROCESSING -> {
                    val wave = (sin(phase * 2f - twoPi * 2f * u) + 1f) / 2f
                    val shimmer = 0.40f + 0.60f * wave
                    paint.alpha = (baseAlpha * shimmer).toInt().coerceIn(0, 255)
                    minHeight + wave * envelope * processingWaveExtra
                }
                Mode.IDLE -> {
                    paint.alpha = baseAlpha
                    minHeight
                }
            }

            val top = centerY - barHeight / 2f
            val bottom = centerY + barHeight / 2f
            val right = startX + barWidth

            barRect.set(startX, top, right, bottom)
            canvas.drawRoundRect(barRect, radius, radius, paint)

            startX += slotWidth
        }
    }
}
