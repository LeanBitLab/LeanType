package helium314.keyboard.latin

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ResourceUtils

/**
 * Manages the floating keyboard within the IME's native TYPE_INPUT_METHOD window.
 * The keyboard frame is positioned dynamically inside InputView via translation coordinates
 * and wrapped with an interactive floating bottom bar (drag pill, dock button, resize handle).
 */
class FloatingKeyboardManager(private val context: Context, private val latinIME: LatinIME) {

    companion object {
        private const val TAG = "FloatingKeyboardManager"
        private const val PREFS_NAME = "floating_keyboard_prefs"
        private const val PREF_X = "floating_x"
        private const val PREF_Y = "floating_y"
        private const val PREF_WIDTH = "floating_width"
        private const val PREF_SCALE = "floating_scale"
        private const val PREF_IS_ACTIVE = "floating_is_active"
        private const val FLOATING_WIDTH_FRACTION = 0.75f
        private const val BOTTOM_BAR_HEIGHT_DP = 28
        private const val CORNER_RADIUS_DP = 16f

        fun isFloatingMode(context: Context? = null): Boolean {
            if (ResourceUtils.getFloatingKeyboardWidth() > 0) return true
            val latinIme = LatinIME.getInstance()
            val fkm = latinIme?.floatingKeyboardManager
            val ctx = context ?: fkm?.context ?: latinIme
            val isLandscape = ctx?.resources?.configuration?.orientation == Configuration.ORIENTATION_LANDSCAPE
            val sv = Settings.getValues()
            if (fkm != null) {
                if (fkm.isFloating) {
                    if (fkm.isAutoLandscapeFloating && !isLandscape) return false
                    return true
                }
                if (sv.mFloatingKeyboardLandscape && isLandscape) return true
                if (sv.mRememberFloatingKeyboard && fkm.wasFloatingLastTime()) return true
            } else {
                if (sv.mFloatingKeyboardLandscape && isLandscape) return true
            }
            return false
        }
    }

    private val prefs: SharedPreferences by lazy {
        DeviceProtectedUtils.getSharedPreferences(context, PREFS_NAME)
    }

    private fun isLandscape(): Boolean {
        val res = (latinIME.displayContext ?: context).resources
        return res.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    private fun getOrientationKey(base: String, isLandscape: Boolean): String =
        "${base}_${if (isLandscape) "landscape" else "portrait"}"

    private fun getSavedInt(base: String, isLandscape: Boolean, fallback: Int): Int {
        val key = getOrientationKey(base, isLandscape)
        if (prefs.contains(key)) {
            return prefs.getInt(key, fallback)
        }
        if (!isLandscape && prefs.contains(base)) {
            return prefs.getInt(base, fallback)
        }
        return fallback
    }

    private fun getSavedFloat(base: String, isLandscape: Boolean, fallback: Float): Float {
        val key = getOrientationKey(base, isLandscape)
        if (prefs.contains(key)) {
            return prefs.getFloat(key, fallback)
        }
        if (!isLandscape && prefs.contains(base)) {
            return prefs.getFloat(base, fallback)
        }
        return fallback
    }

    fun wasFloatingLastTime(): Boolean = prefs.getBoolean(PREF_IS_ACTIVE, false)

    fun clearFloatingActiveState() {
        prefs.edit().putBoolean(PREF_IS_ACTIVE, false).apply()
        if (isFloating) {
            hide(showDockedKeyboard = true)
        }
    }

    var isFloating = false
        private set

    var isAutoLandscapeFloating = false
        private set

    fun isFloatingMode(res: Resources? = null): Boolean {
        if (ResourceUtils.getFloatingKeyboardWidth() > 0) return true
        val isLandscape = (res ?: context.resources).configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val sv = Settings.getValues()
        if (isFloating) {
            if (isAutoLandscapeFloating && !isLandscape) return false
            return true
        }
        if (sv.mFloatingKeyboardLandscape && isLandscape) return true
        if (sv.mRememberFloatingKeyboard && wasFloatingLastTime()) return true
        return false
    }

    @Volatile
    var isDragging = false
        private set

    @Volatile
    var isResizing = false
        private set

    // Multi-touch tracking for drag & resize
    private var activeDragPointerId = MotionEvent.INVALID_POINTER_ID
    private var activeResizePointerId = MotionEvent.INVALID_POINTER_ID

    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialTransX = 0f
    private var initialTransY = 0f

    private var initialResizeTouchX = 0f
    private var initialResizeTouchY = 0f
    private var initialResizeTransX = 0f
    private var initialResizeTransY = 0f
    private var initialResizeWidth = 0
    private var initialResizeHeight = 0
    private var initialResizeScale = 1.0f

    private var wasFloatingBeforeExtract = false
    private var wasAutoLandscapeBeforeExtract = false
    private val tempLocation = IntArray(2)

    private var bottomBar: FrameLayout? = null

    fun getKeyboardFrame(): View? = latinIME.mInputView?.findViewById(R.id.main_keyboard_frame)

    /**
     * Fills [outRect] with the bounding rectangle of the floating keyboard in window coordinates,
     * clamped to [windowWidth] and [windowHeight].
     * Returns true if the keyboard is floating and [outRect] is non-empty, false otherwise.
     */
    fun getFloatingTouchableRect(outRect: Rect, windowWidth: Int, windowHeight: Int): Boolean {
        val frame = getKeyboardFrame() ?: return false
        if (!isFloating || !frame.isShown || frame.width <= 0 || frame.height <= 0) {
            outRect.setEmpty()
            return false
        }
        frame.getLocationInWindow(tempLocation)
        outRect.set(tempLocation[0], tempLocation[1], tempLocation[0] + frame.width, tempLocation[1] + frame.height)
        if (windowWidth > 0 && windowHeight > 0) {
            outRect.intersect(0, 0, windowWidth, windowHeight)
        }
        return !outRect.isEmpty
    }

    fun show(isAutoLandscape: Boolean = false) {
        val frame = getKeyboardFrame() ?: return

        val dm = (latinIME.displayContext ?: context).resources.displayMetrics
        val density = dm.density
        val isLandscape = isLandscape()
        val minWidth = (dm.widthPixels * (if (isLandscape) 0.25f else 0.40f)).toInt().coerceAtLeast((220 * density).toInt())
        val maxWidth = (dm.widthPixels * 0.95f).toInt()
        val defaultWidth = (dm.widthPixels * (if (isLandscape) 0.50f else FLOATING_WIDTH_FRACTION)).toInt()
        val savedWidth = getSavedInt(PREF_WIDTH, isLandscape, -1)
        val floatingWidth = (if (savedWidth != -1) savedWidth else defaultWidth).coerceIn(minWidth, maxWidth)
        val savedScale = getSavedFloat(PREF_SCALE, isLandscape, 1.0f).coerceIn(0.65f, 1.35f)

        // Set floating state and geometry overrides FIRST before view hierarchy layout passes
        isFloating = true
        isAutoLandscapeFloating = isAutoLandscape
        if (!isAutoLandscape && Settings.getValues().mRememberFloatingKeyboard) {
            prefs.edit().putBoolean(PREF_IS_ACTIVE, true).apply()
        }
        ResourceUtils.setFloatingKeyboardWidth(floatingWidth)
        ResourceUtils.setFloatingKeyboardScale(savedScale)
        KeyboardLayoutSet.clearKeyboardCache()

        val colors = Settings.getValues().mColors
        val bgColor = colors.get(ColorType.MAIN_BACKGROUND)
        val textColor = colors.get(ColorType.KEY_TEXT)
        val cornerRadius = CORNER_RADIUS_DP * density
        val bottomBarHeight = (BOTTOM_BAR_HEIGHT_DP * density).toInt()

        // Configure or create bottom bar inside main_keyboard_frame
        val bottomContainer = frame.findViewById<FrameLayout>(R.id.floating_bottom_bar)
        if (bottomContainer != null) {
            val bottomLp = bottomContainer.layoutParams as? LinearLayout.LayoutParams
                ?: LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, bottomBarHeight)
            bottomLp.width = LinearLayout.LayoutParams.MATCH_PARENT
            bottomLp.height = bottomBarHeight
            bottomContainer.layoutParams = bottomLp
            bottomContainer.setPadding(0, 0, 0, 0)
            bottomContainer.removeAllViews()
            bottomBar = createBottomBar(bottomBarHeight, bgColor, textColor, density, cornerRadius)
            bottomContainer.addView(bottomBar)
            bottomContainer.visibility = View.VISIBLE
        }

        // Configure FrameLayout layoutParams
        val lp = frame.layoutParams as? FrameLayout.LayoutParams ?: FrameLayout.LayoutParams(
            floatingWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.width = floatingWidth
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
        frame.layoutParams = lp

        // Calculate and clamp position
        val savedX = getSavedInt(PREF_X, isLandscape, -1)
        val savedY = getSavedInt(PREF_Y, isLandscape, -1)
        val maxX = (dm.widthPixels - floatingWidth).coerceAtLeast(0)
        val maxY = (dm.heightPixels - (220 * density).toInt()).coerceAtLeast(0)

        val posX = if (savedX != -1) savedX.coerceIn(0, maxX) else (dm.widthPixels - floatingWidth) / 2
        val posY = if (savedY != -1) savedY.coerceIn(0, maxY) else dm.heightPixels / 3

        frame.translationX = posX.toFloat()
        frame.translationY = posY.toFloat()

        // Background styling: rounded corners and elevation
        val bgDrawable = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadii = floatArrayOf(
                cornerRadius, cornerRadius,
                cornerRadius, cornerRadius,
                cornerRadius, cornerRadius,
                cornerRadius, cornerRadius
            )
        }
        frame.background = bgDrawable
        frame.clipToOutline = true
        frame.outlineProvider = ViewOutlineProvider.BACKGROUND
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            frame.elevation = 8f * density
        }

        KeyboardSwitcher.getInstance().reloadKeyboard()

        (frame.findViewById<View>(R.id.keyboard_view_wrapper) as? View)?.requestLayout()
        frame.requestLayout()
        (latinIME.mInputView as? InputView)?.resetChildrenFloatingPadding()
        (latinIME.mInputView as? InputView)?.updateBottomPadding()
        latinIME.onFloatingKeyboardShown()

        frame.post {
            if (!isFloating) return@post
            (latinIME.mInputView as? InputView)?.resetChildrenFloatingPadding()
            (latinIME.mInputView as? InputView)?.updateBottomPadding()
            KeyboardSwitcher.getInstance().reloadKeyboard()
            val currentLp = frame.layoutParams
            currentLp.width = ResourceUtils.getFloatingKeyboardWidth().takeIf { it > 0 } ?: floatingWidth
            currentLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            frame.layoutParams = currentLp
            (frame.findViewById<View>(R.id.keyboard_view_wrapper) as? View)?.requestLayout()
            frame.requestLayout()
        }
        Log.i(TAG, "Floating keyboard shown at ${floatingWidth}px width, scale $savedScale")
    }

    fun hide(showDockedKeyboard: Boolean = true) {
        if (!isFloating) return

        if (showDockedKeyboard) {
            prefs.edit().putBoolean(PREF_IS_ACTIVE, false).apply()
        }

        isFloating = false
        isAutoLandscapeFloating = false
        isDragging = false
        isResizing = false

        ResourceUtils.setFloatingKeyboardWidth(0)
        ResourceUtils.setFloatingKeyboardScale(0.0f)
        KeyboardLayoutSet.clearKeyboardCache()

        val frame = getKeyboardFrame()
        if (frame != null) {
            frame.findViewById<View>(R.id.floating_bottom_bar)?.visibility = View.GONE
            frame.translationX = 0f
            frame.translationY = 0f
            frame.scaleX = 1f
            frame.scaleY = 1f

            val lp = frame.layoutParams as? FrameLayout.LayoutParams
            if (lp != null) {
                lp.gravity = Gravity.BOTTOM
                lp.width = FrameLayout.LayoutParams.MATCH_PARENT
                lp.height = FrameLayout.LayoutParams.WRAP_CONTENT
                frame.layoutParams = lp
            }

            frame.background = null
            frame.outlineProvider = null
            frame.clipToOutline = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                frame.elevation = 0f
            }
            (latinIME.mInputView as? InputView)?.updateBottomPadding()
            latinIME.mInputView?.post { latinIME.mInputView?.requestApplyInsets() }
        }

        KeyboardSwitcher.getInstance().reloadKeyboard()
        latinIME.onFloatingKeyboardHidden(showDockedKeyboard)
        Log.i(TAG, "Floating keyboard hidden, docked mode restored")
    }

    fun toggle() {
        if (isFloating) {
            hide()
        } else {
            show(isAutoLandscape = false)
        }
    }

    fun resetDragAndResizeState() {
        if (isDragging || isResizing) {
            isDragging = false
            isResizing = false
            activeDragPointerId = MotionEvent.INVALID_POINTER_ID
            activeResizePointerId = MotionEvent.INVALID_POINTER_ID
            getKeyboardFrame()?.let { frame ->
                frame.scaleX = 1f
                frame.scaleY = 1f
            }
            latinIME.requestInsetsUpdate()
        }
    }

    fun onOrientationChanged(isLandscape: Boolean) {
        if (!isFloating) return
        resetDragAndResizeState()
        show(isAutoLandscape = isAutoLandscapeFloating)
    }

    fun clampPositionToScreen() {
        val frame = getKeyboardFrame() ?: return
        if (!isFloating) return
        val dm = (latinIME.displayContext ?: context).resources.displayMetrics
        val inputView = latinIME.mInputView
        val availableWidth = inputView?.width?.takeIf { it > 0 } ?: dm.widthPixels
        val availableHeight = inputView?.height?.takeIf { it > 0 } ?: dm.heightPixels

        val maxX = (availableWidth - frame.width).coerceAtLeast(0)
        val maxY = (availableHeight - frame.height).coerceAtLeast(0)

        frame.translationX = frame.translationX.coerceIn(0f, maxX.toFloat())
        frame.translationY = frame.translationY.coerceIn(0f, maxY.toFloat())

        val isLandscape = isLandscape()
        prefs.edit()
            .putInt(getOrientationKey(PREF_X, isLandscape), frame.translationX.toInt())
            .putInt(getOrientationKey(PREF_Y, isLandscape), frame.translationY.toInt())
            .putInt(PREF_X, frame.translationX.toInt())
            .putInt(PREF_Y, frame.translationY.toInt())
            .apply()
        latinIME.requestInsetsUpdate()
    }

    fun onStartExtractMode() {
        resetDragAndResizeState()
        if (isFloating) {
            wasFloatingBeforeExtract = true
            wasAutoLandscapeBeforeExtract = isAutoLandscapeFloating
            hide(showDockedKeyboard = false)
        }
    }

    fun onFinishExtractMode() {
        resetDragAndResizeState()
        if (wasFloatingBeforeExtract) {
            val wasAuto = wasAutoLandscapeBeforeExtract
            wasFloatingBeforeExtract = false
            wasAutoLandscapeBeforeExtract = false
            show(isAutoLandscape = wasAuto)
        }
    }

    fun onInputViewRecreated(newInputView: View) {
        if (!isFloating) return
        Log.i(TAG, "Input view recreated while floating, reapplying floating layout")
        show(isAutoLandscape = isAutoLandscapeFloating)
    }

    fun destroy() {
        resetDragAndResizeState()
        if (isFloating) {
            val frame = getKeyboardFrame()
            if (frame != null) {
                frame.background = null
                frame.outlineProvider = null
                frame.clipToOutline = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    frame.elevation = 0f
                }
            }
            ResourceUtils.setFloatingKeyboardWidth(0)
            ResourceUtils.setFloatingKeyboardScale(0.0f)
            isFloating = false
            isAutoLandscapeFloating = false
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun createBottomBar(
        height: Int,
        bgColor: Int,
        textColor: Int,
        density: Float,
        cornerRadius: Float
    ): FrameLayout {
        val dm = context.resources.displayMetrics
        val minHeight = (120 * density).toInt()

        val bottomBar = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                height
            )
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadii = floatArrayOf(
                    0f, 0f,
                    0f, 0f,
                    cornerRadius, cornerRadius,
                    cornerRadius, cornerRadius
                )
            }
        }

        // Sleek drag handle pill in center
        val pillWidth = (44 * density).toInt()
        val pillHeight = (4.5f * density).toInt()
        val defaultPillColor = (textColor and 0x00FFFFFF) or 0x66000000.toInt()
        val activePillColor = (textColor and 0x00FFFFFF) or 0xCC000000.toInt()
        val dragHandleBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = 2.5f * density
            setColor(defaultPillColor)
        }
        val dragHandle = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(pillWidth, pillHeight).apply {
                gravity = Gravity.CENTER
            }
            background = dragHandleBg
            contentDescription = context.getString(R.string.floating_keyboard_drag_handle)
        }
        bottomBar.addView(dragHandle)

        // Dock button (Bottom-Left / Start)
        val btnSize = (height * 0.8f).toInt()
        val dockPadding = (3 * density).toInt()
        val dockBtn = ImageButton(context).apply {
            layoutParams = FrameLayout.LayoutParams(btnSize, btnSize).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                marginStart = (8 * density).toInt()
            }
            setPadding(dockPadding, dockPadding, dockPadding, dockPadding)
            setImageResource(R.drawable.ic_close)
            setColorFilter(textColor)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                this.cornerRadius = 6 * density
                setColor((textColor and 0x00FFFFFF) or 0x1A000000.toInt())
            }
            contentDescription = context.getString(R.string.floating_keyboard_dock_handle)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setOnClickListener { hide(showDockedKeyboard = true) }
        }
        bottomBar.addView(dockBtn)

        // Corner Resize Handle (Bottom-Right / End)
        val defaultBgColor = (textColor and 0x00FFFFFF) or 0x1A000000.toInt()
        val activeBgColor = (textColor and 0x00FFFFFF) or 0x55000000.toInt()

        val resizeBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = 6 * density
            setColor(defaultBgColor)
        }

        val resizeBtn = ImageButton(context).apply {
            layoutParams = FrameLayout.LayoutParams(btnSize, btnSize).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                marginEnd = (8 * density).toInt()
            }
            setPadding(dockPadding, dockPadding, dockPadding, dockPadding)
            setImageResource(R.drawable.ic_floating_resize)
            setColorFilter(textColor)
            background = resizeBg
            contentDescription = context.getString(R.string.floating_keyboard_resize_handle)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        }

        var lastTargetWidth = 0
        var lastTargetHeight = 0

        resizeBtn.setOnTouchListener { _, event ->
            val frame = getKeyboardFrame() ?: return@setOnTouchListener false
            val inputView = latinIME.mInputView
            val screenWidth = inputView?.width?.takeIf { it > 0 } ?: dm.widthPixels
            val screenHeight = inputView?.height?.takeIf { it > 0 } ?: dm.heightPixels

            val isLandscape = isLandscape()
            val minWidth = (screenWidth * (if (isLandscape) 0.25f else 0.40f)).toInt().coerceAtLeast((220 * density).toInt())
            val maxWidth = (screenWidth * 0.95f).toInt()
            val maxHeight = (screenHeight * 0.75f).toInt()

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    activeResizePointerId = event.getPointerId(0)
                    isResizing = true
                    initialResizeTouchX = event.rawX
                    initialResizeTouchY = event.rawY
                    initialResizeTransX = frame.translationX
                    initialResizeTransY = frame.translationY
                    initialResizeWidth = frame.width.takeIf { it > 0 } ?: ResourceUtils.getFloatingKeyboardWidth().takeIf { it > 0 } ?: (screenWidth * FLOATING_WIDTH_FRACTION).toInt()
                    initialResizeHeight = frame.height.takeIf { it > 0 } ?: ((220 * density).toInt())
                    initialResizeScale = ResourceUtils.getFloatingKeyboardScale().let { if (it > 0f) it else 1.0f }

                    lastTargetWidth = initialResizeWidth
                    lastTargetHeight = initialResizeHeight

                    frame.pivotX = 0f
                    frame.pivotY = 0f

                    resizeBg.setColor(activeBgColor)
                    latinIME.requestInsetsUpdate()
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN -> true
                MotionEvent.ACTION_MOVE -> {
                    val pointerIndex = event.findPointerIndex(activeResizePointerId)
                    if (pointerIndex != -1) {
                        val currentRawX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) event.getRawX(pointerIndex) else event.rawX
                        val currentRawY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) event.getRawY(pointerIndex) else event.rawY
                        val dx = (currentRawX - initialResizeTouchX).toInt()
                        val dy = (currentRawY - initialResizeTouchY).toInt()

                        val maxAvailableWidth = (screenWidth - initialResizeTransX.toInt()).coerceAtLeast(minWidth)
                        val effectiveMaxWidth = minOf(maxWidth, maxAvailableWidth)
                        val targetWidth = (initialResizeWidth + dx).coerceIn(minWidth, effectiveMaxWidth)

                        val maxAvailableHeight = (screenHeight - initialResizeTransY.toInt()).coerceAtLeast(minHeight)
                        val effectiveMaxHeight = minOf(maxHeight, maxAvailableHeight)
                        val targetHeight = (initialResizeHeight + dy).coerceIn(minHeight, effectiveMaxHeight)

                        val heightRatio = targetHeight.toFloat() / initialResizeHeight
                        val minAllowedScale = 0.65f
                        val maxAllowedScale = 1.35f
                        val minAllowedRatio = minAllowedScale / initialResizeScale
                        val maxAllowedRatio = maxAllowedScale / initialResizeScale
                        val clampedRatio = heightRatio.coerceIn(minAllowedRatio, maxAllowedRatio)

                        val scaleX = targetWidth.toFloat() / initialResizeWidth
                        val scaleY = clampedRatio

                        frame.scaleX = scaleX
                        frame.scaleY = scaleY

                        lastTargetWidth = targetWidth
                        lastTargetHeight = (initialResizeHeight * clampedRatio).toInt()
                    }
                    true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    val pointerIndex = event.actionIndex
                    if (event.getPointerId(pointerIndex) == activeResizePointerId) {
                        val newIndex = if (pointerIndex == 0) 1 else 0
                        if (newIndex < event.pointerCount) {
                            activeResizePointerId = event.getPointerId(newIndex)
                            initialResizeTouchX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) event.getRawX(newIndex) else event.rawX
                            initialResizeTouchY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) event.getRawY(newIndex) else event.rawY
                            initialResizeWidth = lastTargetWidth
                            initialResizeHeight = lastTargetHeight
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isResizing = false
                    activeResizePointerId = MotionEvent.INVALID_POINTER_ID
                    resizeBg.setColor(defaultBgColor)

                    frame.scaleX = 1.0f
                    frame.scaleY = 1.0f

                    val finalWidth = lastTargetWidth
                    val heightRatio = if (initialResizeHeight > 0) lastTargetHeight.toFloat() / initialResizeHeight else 1.0f
                    val finalScale = (initialResizeScale * heightRatio).coerceIn(0.65f, 1.35f)

                    val lp = frame.layoutParams
                    lp.width = finalWidth
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    frame.layoutParams = lp

                    val isLandscape = isLandscape()
                    prefs.edit()
                        .putInt(getOrientationKey(PREF_X, isLandscape), frame.translationX.toInt())
                        .putInt(getOrientationKey(PREF_Y, isLandscape), frame.translationY.toInt())
                        .putInt(getOrientationKey(PREF_WIDTH, isLandscape), finalWidth)
                        .putFloat(getOrientationKey(PREF_SCALE, isLandscape), finalScale)
                        .putInt(PREF_X, frame.translationX.toInt())
                        .putInt(PREF_Y, frame.translationY.toInt())
                        .putInt(PREF_WIDTH, finalWidth)
                        .putFloat(PREF_SCALE, finalScale)
                        .apply()

                    ResourceUtils.setFloatingKeyboardWidth(finalWidth)
                    ResourceUtils.setFloatingKeyboardScale(finalScale)
                    KeyboardSwitcher.getInstance().reloadKeyboard()
                    latinIME.requestInsetsUpdate()

                    frame.post {
                        if (!isFloating) return@post
                        frame.findViewById<View>(R.id.strip_container)?.requestLayout()
                        (frame.findViewById<View>(R.id.keyboard_view_wrapper) as? View)?.requestLayout()
                        frame.requestLayout()
                        KeyboardSwitcher.getInstance().reloadKeyboard()
                    }

                    val kb = KeyboardSwitcher.getInstance().mainKeyboardView?.keyboard
                    Log.i(TAG, "floating geometry resize: occupied=${kb?.mOccupiedHeight} base=${kb?.mBaseHeight} " +
                            "topPad=${kb?.mTopPadding} frameH=${frame.height} viewPadBottom=${latinIME.mInputView?.paddingBottom}")
                    true
                }
                else -> false
            }
        }
        bottomBar.addView(resizeBtn)

        // Drag listener on the entire bottom bar
        bottomBar.setOnTouchListener { _, event ->
            val frame = getKeyboardFrame() ?: return@setOnTouchListener false
            val inputView = latinIME.mInputView
            val availableWidth = inputView?.width?.takeIf { it > 0 } ?: dm.widthPixels
            val availableHeight = inputView?.height?.takeIf { it > 0 } ?: dm.heightPixels

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    activeDragPointerId = event.getPointerId(0)
                    isDragging = true
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    initialTransX = frame.translationX
                    initialTransY = frame.translationY
                    dragHandleBg.setColor(activePillColor)
                    latinIME.requestInsetsUpdate()
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN -> true
                MotionEvent.ACTION_MOVE -> {
                    val pointerIndex = event.findPointerIndex(activeDragPointerId)
                    if (pointerIndex != -1) {
                        val currentRawX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) event.getRawX(pointerIndex) else event.rawX
                        val currentRawY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) event.getRawY(pointerIndex) else event.rawY
                        val dx = currentRawX - initialTouchX
                        val dy = currentRawY - initialTouchY

                        val maxW = (availableWidth - frame.width).coerceAtLeast(0)
                        val maxH = (availableHeight - frame.height).coerceAtLeast(0)

                        val newX = (initialTransX + dx).coerceIn(0f, maxW.toFloat())
                        val newY = (initialTransY + dy).coerceIn(0f, maxH.toFloat())

                        frame.translationX = newX
                        frame.translationY = newY
                    }
                    true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    val pointerIndex = event.actionIndex
                    if (event.getPointerId(pointerIndex) == activeDragPointerId) {
                        val newIndex = if (pointerIndex == 0) 1 else 0
                        if (newIndex < event.pointerCount) {
                            activeDragPointerId = event.getPointerId(newIndex)
                            initialTouchX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) event.getRawX(newIndex) else event.rawX
                            initialTouchY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) event.getRawY(newIndex) else event.rawY
                            initialTransX = frame.translationX
                            initialTransY = frame.translationY
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    activeDragPointerId = MotionEvent.INVALID_POINTER_ID
                    dragHandleBg.setColor(defaultPillColor)
                    val isLandscape = isLandscape()
                    prefs.edit()
                        .putInt(getOrientationKey(PREF_X, isLandscape), frame.translationX.toInt())
                        .putInt(getOrientationKey(PREF_Y, isLandscape), frame.translationY.toInt())
                        .putInt(PREF_X, frame.translationX.toInt())
                        .putInt(PREF_Y, frame.translationY.toInt())
                        .apply()
                    latinIME.requestInsetsUpdate()
                    true
                }
                else -> false
            }
        }

        return bottomBar
    }
}
