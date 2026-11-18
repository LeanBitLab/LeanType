// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.prefs
import kotlin.math.roundToInt

class KeyboardWrapperView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle), View.OnClickListener {

    var keyboardActionListener: KeyboardActionListener? = null

    private lateinit var stopOneHandedModeBtn: ImageButton
    private lateinit var switchOneHandedModeBtn: ImageButton
    private lateinit var resizeOneHandedModeBtn: ImageButton

    var oneHandedModeEnabled = false
        set(enabled) {
            field = enabled
            updateViewsVisibility()
            if (isInLayout) post { requestLayout() } else requestLayout()
        }

    /** Hides the one-handed controls while another view (touchpad) covers the keyboard, without leaving the mode. */
    var oneHandedControlsHidden = false
        set(hidden) {
            if (field == hidden) return
            field = hidden
            updateViewsVisibility()
        }

    // Resize drag: rebuilding the keyboard takes ~100 ms, so the drag only previews the new width by scaling the
    // keyboard view, and the keyboard is rebuilt once when the finger is lifted.
    private var resizeStartX = 0f
    private var resizeStartScale = 0f
    private var resizePendingScale = 0f
    private var resizePreviewView: View? = null

    // Side switch: keyboard position before the switch, to slide it to the new side on the next layout
    private var slideFromLeft: Int? = null

    private fun oneHandedWidthFraction(extraScale: Float): Float {
        val baseScale = resources.getFraction(R.fraction.config_one_handed_mode_width, 1, 1)
        return 1f - (1f - baseScale) * extraScale
    }

    private fun previewResize(scale: Float) {
        val view = resizePreviewView ?: return
        val scaleX = oneHandedWidthFraction(scale) / oneHandedWidthFraction(resizeStartScale)
        val isLeftGravity = oneHandedGravity == Gravity.LEFT
        view.pivotX = if (isLeftGravity) 0f else view.width.toFloat()
        view.scaleX = scaleX
        // the buttons stay centered in the space beside the keyboard
        val shift = (scaleX - 1f) * view.width / 2f
        val buttonShift = if (isLeftGravity) shift else -shift
        stopOneHandedModeBtn.translationX = buttonShift
        switchOneHandedModeBtn.translationX = buttonShift
        resizeOneHandedModeBtn.translationX = buttonShift
    }

    private fun endResize(apply: Boolean) {
        resizePreviewView?.scaleX = 1f
        resizePreviewView = null
        stopOneHandedModeBtn.translationX = 0f
        switchOneHandedModeBtn.translationX = 0f
        resizeOneHandedModeBtn.translationX = 0f
        if (!apply || resizePendingScale == resizeStartScale || !oneHandedModeEnabled) return
        // the pref listener reloads settings synchronously, so the rebuilt keyboard uses the new scale
        Settings.getInstance().writeOneHandedModeScale(resizePendingScale)
        KeyboardSwitcher.getInstance().setOneHandedModeEnabled(true, true)
    }

    var oneHandedGravity = Gravity.NO_GRAVITY
        set(value) {
            field = value
            updateSwitchButtonSide()
            if (isInLayout) post { requestLayout() } else requestLayout()
        }


    @SuppressLint("ClickableViewAccessibility")
    override fun onFinishInflate() {
        super.onFinishInflate()
        val keyboardIconsSet = KeyboardIconsSet.instance
        keyboardIconsSet.loadIcons(context)
        stopOneHandedModeBtn = findViewById(R.id.btn_stop_one_handed_mode)
        stopOneHandedModeBtn.setImageDrawable(keyboardIconsSet.getNewDrawable(KeyboardIconsSet.NAME_STOP_ONEHANDED_KEY, context))
        stopOneHandedModeBtn.visibility = GONE
        switchOneHandedModeBtn = findViewById(R.id.btn_switch_one_handed_mode)
        switchOneHandedModeBtn.setImageDrawable(keyboardIconsSet.getNewDrawable(KeyboardIconsSet.NAME_SWITCH_ONEHANDED_KEY, context))
        switchOneHandedModeBtn.visibility = GONE
        resizeOneHandedModeBtn = findViewById(R.id.btn_resize_one_handed_mode)
        resizeOneHandedModeBtn.setImageDrawable(keyboardIconsSet.getNewDrawable(KeyboardIconsSet.NAME_RESIZE_ONEHANDED_KEY, context))
        resizeOneHandedModeBtn.visibility = GONE

        stopOneHandedModeBtn.setOnClickListener(this)
        switchOneHandedModeBtn.setOnClickListener(this)

        resizeOneHandedModeBtn.setOnTouchListener { _, motionEvent ->
            when (motionEvent.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    resizeStartX = motionEvent.rawX
                    val landscape = Settings.getValues().mDisplayOrientation == Configuration.ORIENTATION_LANDSCAPE
                    val split = Settings.getValues().mIsSplitKeyboardEnabled
                    resizeStartScale = Settings.readOneHandedModeScale(context.prefs(), landscape, split)
                    resizePendingScale = resizeStartScale
                    resizePreviewView = KeyboardSwitcher.getInstance().visibleKeyboardView
                }
                MotionEvent.ACTION_MOVE -> {
                    val sign = -switchOneHandedModeBtn.scaleX
                    // factor 2 to make it more sensitive (maybe could be tuned a little)
                    val changePercent = 2 * sign * (resizeStartX - motionEvent.rawX) / context.resources.displayMetrics.density
                    val newScale = ((resizeStartScale * 100f + changePercent).roundToInt() / 100f).coerceIn(0.5f, 2.5f)
                    if (newScale != resizePendingScale) {
                        resizePendingScale = newScale
                        previewResize(newScale)
                    }
                }
                MotionEvent.ACTION_UP -> endResize(true)
                MotionEvent.ACTION_CANCEL -> endResize(false)
            }
            true
        }

        val colors = Settings.getValues().mColors
        // styled like the toolbar keys (rounded key background, toolbar icon color)
        for (button in listOf(stopOneHandedModeBtn, switchOneHandedModeBtn, resizeOneHandedModeBtn)) {
            button.setBackgroundResource(R.drawable.toolbar_key_background)
            colors.setColor(button.background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)
            colors.setColor(button, ColorType.TOOL_BAR_KEY)
        }
    }

    @SuppressLint("RtlHardcoded")
    fun switchOneHandedModeSide() {
        slideFromLeft = KeyboardSwitcher.getInstance().visibleKeyboardView?.left
        oneHandedGravity = if (oneHandedGravity == Gravity.LEFT) Gravity.RIGHT else Gravity.LEFT
    }

    private fun updateViewsVisibility() {
        val visibility = if (oneHandedModeEnabled && !oneHandedControlsHidden) VISIBLE else GONE
        stopOneHandedModeBtn.visibility = visibility
        switchOneHandedModeBtn.visibility = visibility
        resizeOneHandedModeBtn.visibility = visibility
    }

    @SuppressLint("RtlHardcoded")
    private fun updateSwitchButtonSide() {
        switchOneHandedModeBtn.scaleX = if (oneHandedGravity == Gravity.LEFT) -1f else 1f
    }

    override fun onClick(view: View) {
        if (view === stopOneHandedModeBtn) {
            keyboardActionListener?.onCodeInput(KeyCode.TOGGLE_ONE_HANDED_MODE,
                Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE,
                false /* isKeyRepeat */)
        } else if (view === switchOneHandedModeBtn) {
            keyboardActionListener?.onCodeInput(KeyCode.SWITCH_ONE_HANDED_MODE,
                Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE,
                false /* isKeyRepeat */)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val isFloating = FloatingKeyboardManager.isFloatingMode(context)
        if (isFloating) {
            val activeChild = (0 until childCount).map { getChildAt(it) }.firstOrNull {
                it.visibility == VISIBLE &&
                it.id != R.id.btn_stop_one_handed_mode &&
                it.id != R.id.btn_switch_one_handed_mode &&
                it.id != R.id.btn_resize_one_handed_mode &&
                it.id != R.id.touchpad_view
            }
            if (activeChild != null) {
                activeChild.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                val targetHeight = activeChild.measuredHeight
                if (targetHeight > 0) {
                    val width = MeasureSpec.getSize(widthMeasureSpec)
                    setMeasuredDimension(width, targetHeight)
                    val exactHeightSpec = MeasureSpec.makeMeasureSpec(targetHeight, MeasureSpec.EXACTLY)
                    for (i in 0 until childCount) {
                        val child = getChildAt(i)
                        if (child.visibility != GONE && child !== activeChild) {
                            measureChildWithMargins(child, widthMeasureSpec, 0, exactHeightSpec, 0)
                        }
                    }
                    return
                }
            }
        }

        val settingsValues = Settings.getValues()
        val ocrCameraView = findViewById<View?>(R.id.ocr_camera_view)
        val isOcrCameraVisible = ocrCameraView != null && (ocrCameraView.isShown || ocrCameraView.visibility == VISIBLE)
        val touchpadView = findViewById<View?>(R.id.touchpad_view)
        val isTouchpadFullscreen = touchpadView != null && (touchpadView.isShown || touchpadView.visibility == VISIBLE) && settingsValues.mTouchpadFullscreen
        val baseHeight = when {
            isOcrCameraVisible -> ResourceUtils.getOcrCameraHeight(context.resources, settingsValues)
            isTouchpadFullscreen -> ResourceUtils.getKeyboardHeight(context.resources, settingsValues) + ResourceUtils.getSuggestionsStripHeight(context.resources)
            else -> ResourceUtils.getKeyboardHeight(context.resources, settingsValues)
        }
        val keyboardView = findViewById<View>(R.id.keyboard_view)
        val padding = if (keyboardView != null && !isFloating) keyboardView.paddingTop + keyboardView.paddingBottom else 0
        val maxExpectedHeight = baseHeight + padding

        if (maxExpectedHeight > 0) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            setMeasuredDimension(width, maxExpectedHeight)
            val exactHeightSpec = MeasureSpec.makeMeasureSpec(maxExpectedHeight, MeasureSpec.EXACTLY)
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child.visibility != GONE) {
                    measureChildWithMargins(child, widthMeasureSpec, 0, exactHeightSpec, 0)
                }
            }
            return
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    @SuppressLint("RtlHardcoded")
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (!oneHandedModeEnabled) {
            super.onLayout(changed, left, top, right, bottom)
            return
        }

        val isLeftGravity = oneHandedGravity == Gravity.LEFT
        val width = right - left
        val keyboardView = KeyboardSwitcher.getInstance().visibleKeyboardView ?: return
        val spareWidth = width - keyboardView.measuredWidth

        val keyboardLeft = if (isLeftGravity) 0 else spareWidth
        keyboardView.layout(
                keyboardLeft,
                0,
                keyboardLeft + keyboardView.measuredWidth,
                keyboardView.measuredHeight
        )
        slideFromLeft?.let { fromLeft ->
            slideFromLeft = null
            val duration = Settings.getAnimationDuration(200)
            if (duration > 0L && fromLeft != keyboardLeft) {
                keyboardView.translationX = (fromLeft - keyboardLeft).toFloat()
                keyboardView.animate().translationX(0f).setDuration(duration)
                    .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
            }
        }

        val scale = Settings.getValues().mKeyboardHeightScale
        // scale one-handed mode button height if keyboard height scale is < 80%
        val heightScale = if (scale < 0.8f) scale + 0.2f else 1f
        val buttonsLeft = if (isLeftGravity) keyboardView.measuredWidth else 0
        // square buttons like the toolbar keys, smaller if the space beside the keyboard is narrow
        val buttonSize = (heightScale * 40 * resources.displayMetrics.density).toInt().coerceAtMost(spareWidth)
        val buttonXLeft = buttonsLeft + (spareWidth - buttonSize) / 2
        fun View.setLayout(yPosition: Int) {
            layout(buttonXLeft, yPosition - buttonSize / 2, buttonXLeft + buttonSize, yPosition + buttonSize / 2)
        }
        // Bottom padding holds the navigation bar inset, keep the buttons above it
        val contentHeight = keyboardView.measuredHeight - keyboardView.paddingBottom
        stopOneHandedModeBtn.setLayout((contentHeight * 0.2f).toInt())
        switchOneHandedModeBtn.setLayout((contentHeight * 0.5f).toInt())
        resizeOneHandedModeBtn.setLayout((contentHeight * 0.8f).toInt())
    }
}
