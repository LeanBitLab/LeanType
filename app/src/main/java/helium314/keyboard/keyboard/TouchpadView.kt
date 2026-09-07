// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.settings.Settings
import kotlin.math.abs

/**
 * A laptop-style touchpad overlay that replaces the keyboard.
 * Supports:
 * - Single-finger drag: move cursor (arrow keys)
 * - Two-finger drag: fast scroll (up/down)
 * - Single tap: Enter/Click
 * - Double tap: Toggle text selection mode
 */
class TouchpadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    interface TouchpadListener {
        fun onCursorMove(keyCode: Int, isSelecting: Boolean)
        fun onSingleTap()
        fun onDoubleTap()
        fun onScroll(direction: Int)
        fun onTwoFingerDoubleTap()
        fun onThreeFingerTap()
        fun onThreeFingerDoubleTap()
        fun onThreeFingerSwipeLeft()
        fun onThreeFingerSwipeRight()
        fun onThreeFingerSwipeUp()
        fun onThreeFingerSwipeDown()
        fun onClose()
    }

    private var mListener: TouchpadListener? = null
    private var mTouchpadSurface: View? = null
    private var mBtnClose: ImageView? = null
    private var mGestureDetector: GestureDetector? = null

    // State
    private var mSelectionMode = false

    // Touch tracking for the touchpad surface
    private var mLastTouchX = 0f
    private var mLastTouchY = 0f
    private var mAccX = 0f
    private var mAccY = 0f
    private var mIsDragging = false

    // Two-finger scroll tracking
    private var mIsTwoFingerScroll = false
    private var mTwoFingerLastX = 0f
    private var mTwoFingerLastY = 0f
    private var mScrollAccX = 0f
    private var mScrollAccY = 0f
    private var mTwoFingerStartX = 0f
    private var mTwoFingerStartY = 0f
    private var mHasScrolledHorizontally = false
    private var mIsTwoFingerLongPress = false
    private var mTwoFingerTapCount = 0

    private val mTwoFingerLongPressRunnable: Runnable = object : Runnable {
        override fun run() {
            if (mIsTwoFingerTap) {
                mIsTwoFingerLongPress = true
                mListener?.onThreeFingerSwipeLeft()
                postDelayed(this, 150)
            }
        }
    }

    // Two-finger tap tracking
    private var mIsTwoFingerTap = false
    private var mTwoFingerDownTime = 0L
    private val mTwoFingerTapRunnable = Runnable {
        mListener?.let {
            if (mTwoFingerTapCount == 1) {
                it.onSingleTap()
            } else if (mTwoFingerTapCount == 2) {
                it.onTwoFingerDoubleTap()
            } else if (mTwoFingerTapCount >= 3) {
                it.onThreeFingerDoubleTap()
            }
        }
        mTwoFingerTapCount = 0
    }

    // Three-finger tap & swipe tracking
    private var mIsThreeFingerTap = false
    private var mThreeFingerDownTime = 0L
    private var mLastThreeFingerTapTime = 0L
    private val mThreeFingerTapRunnable = Runnable {
        mListener?.onThreeFingerTap()
    }
    private var mIsThreeFingerSwipe = false
    private var mThreeFingerStartX = 0f
    private var mThreeFingerStartY = 0f

    init {
        init(context)
    }

    private fun init(context: Context) {
        orientation = VERTICAL
        // Consume all touches so nothing passes through to views behind
        isClickable = true
        isFocusable = true
        fitsSystemWindows = true

        LayoutInflater.from(context).inflate(R.layout.touchpad_view, this, true)
        mTouchpadSurface = findViewById(R.id.touchpad_surface)
        mBtnClose = findViewById(R.id.btn_close_touchpad)
        mBtnClose?.setOnClickListener {
            mListener?.onClose()
        }

        mGestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                mSelectionMode = true
                applySurfaceColor()
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (mListener != null) {
                    mListener!!.onDoubleTap()
                    return true
                }
                return false
            }
        })

        setupTouchSurface()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        // Intercept all touches to prevent them from reaching views behind
        return false // Let children handle their own touches
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        // Consume any touch not handled by children
        return true
    }

    fun setTouchpadListener(listener: TouchpadListener?) {
        mListener = listener
    }

    fun applyColors(colors: Colors) {
        // Root background
        colors.setBackground(this, ColorType.MAIN_BACKGROUND)
        applySurfaceColor()

        // Style the close button
        val btnClose = mBtnClose
        if (btnClose != null) {
            val switcher = KeyboardSwitcher.getInstance()
            val iconsSet: KeyboardIconsSet? = if (switcher != null && switcher.keyboard != null) {
                switcher.keyboard.mIconsSet
            } else {
                null
            }
            val keyIconColor = colors.get(ColorType.KEY_ICON)

            // Set rounded background for the close button
            val density = context.resources.displayMetrics.density
            val gd = GradientDrawable()
            gd.shape = GradientDrawable.RECTANGLE
            gd.cornerRadius = 6f * density
            gd.setColor(colors.get(ColorType.KEY_BACKGROUND))
            btnClose.background = gd

            if (iconsSet != null) {
                val icon = iconsSet.getIconDrawable("close_history")
                if (icon != null) {
                    val mutated = icon.mutate()
                    mutated.setColorFilter(keyIconColor, PorterDuff.Mode.SRC_IN)
                    btnClose.setImageDrawable(mutated)
                }
            }
        }
    }

    private fun applySurfaceColor() {
        val surface = mTouchpadSurface ?: return
        val colors = Settings.getValues().mColors
        val density = context.resources.displayMetrics.density

        val surfaceBg = GradientDrawable()
        surfaceBg.shape = GradientDrawable.RECTANGLE
        surfaceBg.cornerRadius = 16f * density
        surfaceBg.setColor(Color.WHITE)
        surface.background = surfaceBg

        // Use a different color type for selection mode to provide visual feedback
        val surfaceColorType = if (mSelectionMode) {
            ColorType.FUNCTIONAL_KEY_BACKGROUND
        } else {
            ColorType.KEY_BACKGROUND
        }
        colors.setBackground(surface, surfaceColorType)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchSurface() {
        mTouchpadSurface?.setOnTouchListener { v, event ->
            mGestureDetector?.onTouchEvent(event)
            val pointerCount = event.pointerCount
            Log.i("TouchpadViewRaw", "action=" + MotionEvent.actionToString(event.actionMasked) + ", pointers=" + pointerCount)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    Log.i("TouchpadView", "ACTION_DOWN")
                    if (v.parent != null) {
                        v.parent.requestDisallowInterceptTouchEvent(true)
                    }
                    mLastTouchX = event.x
                    mLastTouchY = event.y
                    mAccX = 0f
                    mAccY = 0f
                    mIsDragging = true
                    mIsTwoFingerScroll = false
                    true
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    Log.i("TouchpadView", "ACTION_POINTER_DOWN: pointerCount=" + pointerCount)
                    if (v.parent != null) {
                        v.parent.requestDisallowInterceptTouchEvent(true)
                    }
                    if (pointerCount == 2) {
                        mIsTwoFingerScroll = true
                        mIsTwoFingerTap = true
                        mTwoFingerDownTime = System.currentTimeMillis()
                        mIsDragging = false
                        mTwoFingerStartX = (event.getX(0) + event.getX(1)) / 2f
                        mTwoFingerStartY = (event.getY(0) + event.getY(1)) / 2f
                        mTwoFingerLastX = mTwoFingerStartX
                        mTwoFingerLastY = mTwoFingerStartY
                        mScrollAccX = 0f
                        mScrollAccY = 0f
                        mHasScrolledHorizontally = false
                        mIsTwoFingerLongPress = false

                        removeCallbacks(mTwoFingerTapRunnable)
                        postDelayed(mTwoFingerLongPressRunnable, 400)
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (mIsTwoFingerScroll && pointerCount >= 2) {
                        val midX = (event.getX(0) + event.getX(1)) / 2f
                        val midY = (event.getY(0) + event.getY(1)) / 2f
                        val deltaX = midX - mTwoFingerStartX
                        val deltaY = midY - mTwoFingerStartY

                        val density = context.resources.displayMetrics.density

                        if (abs(midX - mTwoFingerStartX) > 5f * density || abs(midY - mTwoFingerStartY) > 5f * density) {
                            mIsTwoFingerTap = false
                            removeCallbacks(mTwoFingerLongPressRunnable)
                        }

                        val swipeThreshold = 35f * density
                        if (!mHasScrolledHorizontally && abs(deltaY) > abs(deltaX) && abs(deltaY) > swipeThreshold) {
                            mIsTwoFingerScroll = false
                            mIsTwoFingerTap = false
                            removeCallbacks(mTwoFingerLongPressRunnable)
                            mTwoFingerTapCount = 0
                            removeCallbacks(mTwoFingerTapRunnable)

                            if (mListener != null) {
                                if (deltaY < 0) {
                                    mListener!!.onThreeFingerSwipeUp()
                                } else {
                                    mListener!!.onThreeFingerSwipeDown()
                                }
                            }
                        } else {
                            val lastDeltaX = midX - mTwoFingerLastX
                            mTwoFingerLastX = midX
                            mTwoFingerLastY = midY

                            mScrollAccX += lastDeltaX

                            while (mScrollAccX >= SCROLL_THRESHOLD) {
                                mIsTwoFingerTap = false
                                removeCallbacks(mTwoFingerLongPressRunnable)
                                mTwoFingerTapCount = 0
                                removeCallbacks(mTwoFingerTapRunnable)
                                mHasScrolledHorizontally = true
                                if (mListener != null) mListener!!.onScroll(KeyCode.WORD_RIGHT)
                                mScrollAccX -= SCROLL_THRESHOLD
                            }
                            while (mScrollAccX <= -SCROLL_THRESHOLD) {
                                mIsTwoFingerTap = false
                                removeCallbacks(mTwoFingerLongPressRunnable)
                                mTwoFingerTapCount = 0
                                removeCallbacks(mTwoFingerTapRunnable)
                                mHasScrolledHorizontally = true
                                if (mListener != null) mListener!!.onScroll(KeyCode.WORD_LEFT)
                                mScrollAccX += SCROLL_THRESHOLD
                            }
                        }
                    } else if (mIsDragging && pointerCount == 1) {
                        val deltaX = event.x - mLastTouchX
                        val deltaY = event.y - mLastTouchY
                        mLastTouchX = event.x
                        mLastTouchY = event.y

                        mAccX += deltaX
                        mAccY += deltaY

                        val sensitivity = Settings.getValues().mTouchpadSensitivity
                        val baseThreshold = if (mSelectionMode) 70 else 110
                        var threshold = baseThreshold - (sensitivity * 0.6f).toInt()
                        if (threshold < 10) threshold = 10

                        while (mAccX >= threshold) {
                            if (mListener != null) mListener!!.onCursorMove(KeyCode.ARROW_RIGHT, mSelectionMode)
                            mAccX -= threshold
                        }
                        while (mAccX <= -threshold) {
                            if (mListener != null) mListener!!.onCursorMove(KeyCode.ARROW_LEFT, mSelectionMode)
                            mAccX += threshold
                        }
                        while (mAccY >= threshold) {
                            if (mListener != null) mListener!!.onCursorMove(KeyCode.ARROW_DOWN, mSelectionMode)
                            mAccY -= threshold
                        }
                        while (mAccY <= -threshold) {
                            if (mListener != null) mListener!!.onCursorMove(KeyCode.ARROW_UP, mSelectionMode)
                            mAccY += threshold
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    Log.i("TouchpadView", "ACTION_UP")
                    mIsDragging = false
                    mIsTwoFingerScroll = false
                    mIsTwoFingerTap = false
                    removeCallbacks(mTwoFingerLongPressRunnable)
                    mIsTwoFingerLongPress = false
                    if (mSelectionMode) {
                        mSelectionMode = false
                        applySurfaceColor()
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    Log.i("TouchpadView", "ACTION_CANCEL")
                    mIsDragging = false
                    mIsTwoFingerScroll = false
                    mIsTwoFingerTap = false
                    removeCallbacks(mTwoFingerLongPressRunnable)
                    mIsTwoFingerLongPress = false
                    mTwoFingerTapCount = 0
                    removeCallbacks(mTwoFingerTapRunnable)
                    if (mSelectionMode) {
                        mSelectionMode = false
                        applySurfaceColor()
                    }
                    true
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    Log.i("TouchpadView", "ACTION_POINTER_UP: pointerCount=" + pointerCount)
                    if (pointerCount == 2) {
                        removeCallbacks(mTwoFingerLongPressRunnable)
                        if (mIsTwoFingerLongPress) {
                            mIsTwoFingerLongPress = false
                            mIsTwoFingerScroll = false
                            mIsTwoFingerTap = false
                            return@setOnTouchListener true
                        }
                        if (mIsTwoFingerTap && (System.currentTimeMillis() - mTwoFingerDownTime) < 300) {
                            mTwoFingerTapCount++
                            removeCallbacks(mTwoFingerTapRunnable)
                            postDelayed(mTwoFingerTapRunnable, 250)
                        }
                        mIsTwoFingerScroll = false
                        mIsTwoFingerTap = false
                    }
                    true
                }
            }
            true
        }
    }

    companion object {
        private const val SCROLL_THRESHOLD = 40
    }
}
