package helium314.keyboard.latin

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.content.Intent
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.DeviceProtectedUtils

/**
 * Manages the floating keyboard by reparenting the existing main_keyboard_frame
 * from the IME's InputView into a TYPE_APPLICATION_OVERLAY window.
 *
 * Key sizes are dynamically adjusted by setting a floating width override in
 * ResourceUtils before triggering a keyboard reload.
 */
class FloatingKeyboardManager(private val context: Context, private val latinIME: LatinIME) {

    companion object {
        private const val TAG = "FloatingKeyboardManager"
        private const val PREFS_NAME = "floating_keyboard_prefs"
        private const val PREF_X = "floating_x"
        private const val PREF_Y = "floating_y"
        private const val FLOATING_WIDTH_FRACTION = 0.75f
        private const val HEADER_HEIGHT_DP = 28
        private const val CORNER_RADIUS_DP = 16f
    }

    private val prefs: SharedPreferences =
        DeviceProtectedUtils.getSharedPreferences(context, PREFS_NAME)

    private var overlayRoot: FrameLayout? = null
    private var windowManager: WindowManager? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var savedParent: ViewGroup? = null
    private var savedLayoutParams: ViewGroup.LayoutParams? = null
    private var savedParentIndex: Int = -1

    // Touch tracking for drag
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    var isFloating = false
        private set

    fun canDrawOverlays(): Boolean = AndroidSettings.canDrawOverlays(context)

    fun requestOverlayPermission() {
        val intent = Intent(
            AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (!canDrawOverlays() || isFloating) return

        val inputView = latinIME.mInputView ?: run {
            Log.w(TAG, "show(): mInputView is null")
            return
        }

        val mainKeyboardFrame = inputView.findViewById<View>(R.id.main_keyboard_frame) ?: run {
            Log.w(TAG, "show(): main_keyboard_frame not found")
            return
        }

        // Save original parent info so we can restore later
        val parent = mainKeyboardFrame.parent as? ViewGroup ?: return
        savedParent = parent
        savedLayoutParams = mainKeyboardFrame.layoutParams
        savedParentIndex = parent.indexOfChild(mainKeyboardFrame)

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Calculate the floating keyboard width
        val dm = context.resources.displayMetrics
        val floatingWidth = (dm.widthPixels * FLOATING_WIDTH_FRACTION).toInt()

        // Set the floating width override so keyboard keys re-measure at this width
        ResourceUtils.setFloatingKeyboardWidth(floatingWidth)

        // Force keyboard reload so keys re-measure at the new width
        KeyboardSwitcher.getInstance().reloadKeyboard()

        // Get theme colors
        val colors = Settings.getValues().mColors
        val bgColor = colors.get(ColorType.MAIN_BACKGROUND)
        val textColor = colors.get(ColorType.KEY_TEXT)
        val density = dm.density
        val cornerRadius = CORNER_RADIUS_DP * density
        val headerHeight = (HEADER_HEIGHT_DP * density).toInt()

        // Create the overlay root with rounded corners and clipping
        val overlayBg = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            this.cornerRadius = cornerRadius
        }
        overlayRoot = FrameLayout(context).apply {
            background = overlayBg
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        }

        // Create header bar with theme-matching colors and rounded top corners
        val headerBar = createHeaderBar(headerHeight, bgColor, textColor, density, cornerRadius)

        // Build content: header on top, keyboard below
        val contentContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            // Rounded corner background on the container
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadii = floatArrayOf(
                    cornerRadius, cornerRadius,   // top-left
                    cornerRadius, cornerRadius,   // top-right
                    cornerRadius, cornerRadius,   // bottom-right
                    cornerRadius, cornerRadius    // bottom-left
                )
            }
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        }

        // Reparent: remove keyboard frame from InputView
        parent.removeView(mainKeyboardFrame)

        // Set keyboard frame layout params for the overlay — MATCH the floating width
        mainKeyboardFrame.layoutParams = LinearLayout.LayoutParams(
            floatingWidth,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        contentContainer.addView(headerBar)
        contentContainer.addView(mainKeyboardFrame)
        overlayRoot!!.addView(contentContainer)

        // Calculate window position
        val savedX = prefs.getInt(PREF_X, -1)
        val savedY = prefs.getInt(PREF_Y, -1)

        windowParams = WindowManager.LayoutParams(
            floatingWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (savedX != -1 && savedY != -1) {
                x = savedX
                y = savedY
            } else {
                // Center horizontally, near bottom third
                x = (dm.widthPixels - floatingWidth) / 2
                y = dm.heightPixels / 3
            }
        }

        try {
            windowManager?.addView(overlayRoot, windowParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view", e)
            // Restore keyboard to original parent on failure
            contentContainer.removeView(mainKeyboardFrame)
            mainKeyboardFrame.layoutParams = savedLayoutParams
            ResourceUtils.setFloatingKeyboardWidth(0)
            KeyboardSwitcher.getInstance().reloadKeyboard()
            if (savedParentIndex >= 0 && savedParentIndex <= parent.childCount) {
                parent.addView(mainKeyboardFrame, savedParentIndex, savedLayoutParams)
            } else {
                parent.addView(mainKeyboardFrame, savedLayoutParams)
            }
            return
        }

        isFloating = true

        // Hide the IME window so the bottom nav bar goes away
        latinIME.onFloatingKeyboardShown()

        Log.i(TAG, "Floating keyboard shown at ${floatingWidth}px width")
    }

    fun hide() {
        if (!isFloating) return

        // Clear the floating width override FIRST
        ResourceUtils.setFloatingKeyboardWidth(0)

        val mainKeyboardFrame = overlayRoot?.findViewById<View>(R.id.main_keyboard_frame)
        if (mainKeyboardFrame != null) {
            // Remove from overlay content container so it can be safely GC'd
            (mainKeyboardFrame.parent as? ViewGroup)?.removeView(mainKeyboardFrame)
        }

        // Remove overlay window
        overlayRoot?.let { root ->
            try {
                windowManager?.removeView(root)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove overlay view", e)
            }
        }
        overlayRoot = null
        savedParent = null
        savedLayoutParams = null
        savedParentIndex = -1
        isFloating = false

        // Show the IME window again
        latinIME.onFloatingKeyboardHidden()

        // Reload keyboard at full width so keys re-measure properly
        KeyboardSwitcher.getInstance().reloadKeyboard()

        Log.i(TAG, "Floating keyboard hidden, docked mode restored")
    }

    fun toggle() {
        if (isFloating) {
            hide()
        } else {
            if (canDrawOverlays()) {
                show()
            } else {
                requestOverlayPermission()
            }
        }
    }

    /**
     * Called from LatinIME.setInputView() when the input view is recreated
     * (e.g., theme change, orientation change). If floating mode is active,
     * we need to reparent the new keyboard views into the existing overlay.
     */
    fun onInputViewRecreated(newInputView: View) {
        if (!isFloating) return

        Log.i(TAG, "Input view recreated while floating, re-reparenting keyboard")

        val newMainKeyboardFrame = newInputView.findViewById<View>(R.id.main_keyboard_frame)
            ?: return
        val newParent = newMainKeyboardFrame.parent as? ViewGroup ?: return

        // Save new parent info
        savedParent = newParent
        savedLayoutParams = newMainKeyboardFrame.layoutParams
        savedParentIndex = newParent.indexOfChild(newMainKeyboardFrame)

        // Find the content container in our overlay (the LinearLayout)
        val contentContainer = overlayRoot?.getChildAt(0) as? LinearLayout ?: return

        // Remove old keyboard frame from overlay content container (index 1, after header)
        if (contentContainer.childCount > 1) {
            contentContainer.removeViewAt(1)
        }

        // Reparent new keyboard frame
        val floatingWidth = ResourceUtils.getFloatingKeyboardWidth()
        newParent.removeView(newMainKeyboardFrame)
        newMainKeyboardFrame.layoutParams = LinearLayout.LayoutParams(
            if (floatingWidth > 0) floatingWidth else LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        contentContainer.addView(newMainKeyboardFrame)
    }

    /**
     * Called from LatinIME.onDestroy() to clean up.
     */
    fun destroy() {
        if (isFloating) {
            ResourceUtils.setFloatingKeyboardWidth(0)
            overlayRoot?.let { root ->
                try {
                    windowManager?.removeView(root)
                } catch (_: Exception) {}
            }
            overlayRoot = null
            isFloating = false
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun createHeaderBar(height: Int, bgColor: Int, textColor: Int, density: Float, cornerRadius: Float): FrameLayout {
        // Header with rounded top corners matching the container
        val headerBar = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                height
            )
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadii = floatArrayOf(
                    cornerRadius, cornerRadius,   // top-left
                    cornerRadius, cornerRadius,   // top-right
                    0f, 0f,                       // bottom-right
                    0f, 0f                        // bottom-left
                )
            }
        }

        // Drag handle pill in center
        val pillWidth = (40 * density).toInt()
        val pillHeight = (4 * density).toInt()
        val dragHandle = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(pillWidth, pillHeight).apply {
                gravity = Gravity.CENTER
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                this.cornerRadius = 3 * density
                setColor(textColor and 0x00FFFFFF or 0x55000000) // 33% alpha
            }
            contentDescription = context.getString(R.string.floating_keyboard_drag_handle)
        }
        headerBar.addView(dragHandle)

        // Close button — styled like a toolbar key (rounded, subtle background)
        val closeBtnSize = (height * 0.75f).toInt()
        val closePadding = (3 * density).toInt()
        val closeBtn = ImageButton(context).apply {
            layoutParams = FrameLayout.LayoutParams(closeBtnSize, closeBtnSize).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                marginEnd = (6 * density).toInt()
            }
            setPadding(closePadding, closePadding, closePadding, closePadding)
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(textColor)
            // Toolbar-style background: subtle rounded rectangle
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                this.cornerRadius = 6 * density
                setColor(textColor and 0x00FFFFFF or 0x1A000000) // 10% alpha — subtle
            }
            contentDescription = "Close floating keyboard"
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setOnClickListener { toggle() }
        }
        headerBar.addView(closeBtn)

        // Setup drag on the entire header bar — allows free movement in both axes
        headerBar.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    windowParams?.let { lp ->
                        lp.x = (lp.x + dx).toInt()
                        lp.y = (lp.y + dy).toInt()
                        try {
                            windowManager?.updateViewLayout(overlayRoot, lp)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to update overlay layout", e)
                        }
                    }
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    savePosition()
                    true
                }
                else -> false
            }
        }

        return headerBar
    }

    private fun savePosition() {
        windowParams?.let { lp ->
            prefs.edit()
                .putInt(PREF_X, lp.x)
                .putInt(PREF_Y, lp.y)
                .apply()
        }
    }
}
