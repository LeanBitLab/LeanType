/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.emoji

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import helium314.keyboard.accessibility.AccessibilityUtils
import helium314.keyboard.accessibility.KeyboardAccessibilityDelegate
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.KeyDetector
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.KeyboardView
import helium314.keyboard.keyboard.PopupKeysKeyboard
import helium314.keyboard.keyboard.PopupKeysKeyboardView
import helium314.keyboard.keyboard.PopupKeysPanel
import helium314.keyboard.keyboard.PopupTextView
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.CoordinateUtils
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import java.util.WeakHashMap

/**
 * This is an extended [KeyboardView] class that hosts an emoji page keyboard.
 * Multi-touch unsupported. No gesture support.
 */
class EmojiPageKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.keyboardViewStyle
) : KeyboardView(context, attrs, defStyleAttr), PopupKeysPanel.Controller {

    private var mEmojiViewCallback: EmojiViewCallback = EMPTY_EMOJI_VIEW_CALLBACK
    private val mKeyDetector = KeyDetector()
    private var mAccessibilityDelegate: KeyboardAccessibilityDelegate<EmojiPageKeyboardView>? = null

    // Touch inputs
    private var mPointerId = MotionEvent.INVALID_POINTER_ID
    private var mLastX = 0
    private var mLastY = 0
    private var mCurrentKey: Key? = null
    private var mPendingKeyDown: Runnable? = null
    private var mPendingLongPress: Runnable? = null
    private val mHandler = Handler(Looper.getMainLooper())

    // More keys keyboard
    private val mPopupKeysKeyboardContainer: View
    private val mDescriptionView: PopupTextView
    private val mPopupKeysKeyboardView: PopupKeysKeyboardView
    private val mPopupKeysKeyboardCache = WeakHashMap<Key, Keyboard>()
    private val mConfigShowPopupKeysKeyboardAtTouchedPoint: Boolean
    private val mPopupKeysPlacerView: ViewGroup
    // More keys panel (used by popup keys keyboard view)
    private var mPopupKeysPanel: PopupKeysPanel? = null

    init {
        mPopupKeysPlacerView = FrameLayout(context, attrs)

        val keyboardViewAttr = context.obtainStyledAttributes(
            attrs, R.styleable.MainKeyboardView, defStyleAttr, R.style.MainKeyboardView
        )
        val popupKeysKeyboardLayoutId = keyboardViewAttr.getResourceId(
            R.styleable.MainKeyboardView_popupKeysKeyboardLayout, 0
        )
        mConfigShowPopupKeysKeyboardAtTouchedPoint = keyboardViewAttr.getBoolean(
            R.styleable.MainKeyboardView_showPopupKeysKeyboardAtTouchedPoint, false
        )
        keyboardViewAttr.recycle()

        val inflater = LayoutInflater.from(context)
        mPopupKeysKeyboardContainer = inflater.inflate(popupKeysKeyboardLayoutId, null)
        mDescriptionView = mPopupKeysKeyboardContainer.findViewById(R.id.description_view)
        mPopupKeysKeyboardView = mPopupKeysKeyboardContainer.findViewById(R.id.popup_keys_keyboard_view)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val currentKeyboard = keyboard
        if (currentKeyboard is DynamicGridKeyboard) {
            val width = currentKeyboard.mOccupiedWidth + paddingLeft + paddingRight
            val occupiedHeight = currentKeyboard.getDynamicOccupiedHeight()
            val height = occupiedHeight + paddingTop + paddingBottom
            setMeasuredDimension(width, height)
            return
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun setHardwareAcceleratedDrawingEnabled(enabled: Boolean) {
        super.setHardwareAcceleratedDrawingEnabled(enabled)
        if (!enabled) return
        val layerPaint = Paint()
        layerPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        mPopupKeysPlacerView.setLayerType(LAYER_TYPE_HARDWARE, layerPaint)
    }

    private fun installPopupKeysPlacerView(uninstall: Boolean) {
        val floatingManager = KeyboardSwitcher.getInstance()?.floatingKeyboardManager
        if (!uninstall && floatingManager?.isFloating == true) {
            val overlayRoot = floatingManager.overlayRoot
            if (overlayRoot != null) {
                (mPopupKeysPlacerView.parent as? ViewGroup)?.removeView(mPopupKeysPlacerView)
                overlayRoot.addView(mPopupKeysPlacerView)
                return
            }
        }
        val rootView = rootView
        if (rootView == null) {
            Log.w(TAG, "Cannot find root view")
            return
        }
        val windowContentView = rootView.findViewById<ViewGroup>(android.R.id.content)
        if (windowContentView == null) {
            Log.w(TAG, "Cannot find android.R.id.content view to add DrawingPreviewPlacerView")
            return
        }

        if (uninstall) {
            windowContentView.removeView(mPopupKeysPlacerView)
        } else {
            windowContentView.addView(mPopupKeysPlacerView)
        }
    }

    fun setEmojiViewCallback(emojiViewCallback: EmojiViewCallback?) {
        mEmojiViewCallback = emojiViewCallback ?: EMPTY_EMOJI_VIEW_CALLBACK
    }

    override fun setKeyboard(keyboard: Keyboard) {
        super.setKeyboard(keyboard)
        mKeyDetector.setKeyboard(keyboard, 0f, 0f)
        mPopupKeysKeyboardCache.clear()
        if (AccessibilityUtils.instance.isAccessibilityEnabled) {
            if (mAccessibilityDelegate == null) {
                mAccessibilityDelegate = KeyboardAccessibilityDelegate(this, mKeyDetector)
            }
            mAccessibilityDelegate?.keyboard = keyboard
        } else {
            mAccessibilityDelegate = null
        }
    }

    private fun showPopupKeysKeyboard(key: Key): PopupKeysPanel? {
        mPopupKeysKeyboardView.visibility = GONE
        val popupKeys = key.popupKeys ?: return null
        var popupKeysKeyboard = mPopupKeysKeyboardCache[key]
        if (popupKeysKeyboard == null) {
            val builder = PopupKeysKeyboard.Builder(
                context, key, keyboard, false, 0, 0, newLabelPaint(key)
            )
            popupKeysKeyboard = builder.build()
            mPopupKeysKeyboardCache[key] = popupKeysKeyboard
        }

        mPopupKeysKeyboardView.setKeyboard(popupKeysKeyboard)
        mPopupKeysKeyboardView.visibility = VISIBLE
        return mPopupKeysKeyboardView
    }

    private fun dismissPopupKeysPanel() {
        if (isShowingPopupKeysPanel()) {
            mPopupKeysPanel?.dismissPopupKeysPanel()
        }
    }

    fun isShowingPopupKeysPanel(): Boolean {
        return mPopupKeysPanel != null
    }

    override fun setLayoutGravity(layoutGravity: Int) {
        val layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        layoutParams.gravity = layoutGravity
        mPopupKeysKeyboardContainer.layoutParams = layoutParams
        mDescriptionView.layoutParams = layoutParams
    }

    override fun onShowPopupKeysPanel(panel: PopupKeysPanel) {
        installPopupKeysPlacerView(false)
        panel.showInParent(mPopupKeysPlacerView)
        mPopupKeysPanel = panel
    }

    override fun onDismissPopupKeysPanel() {
        if (isShowingPopupKeysPanel()) {
            mPopupKeysPanel?.removeFromParent()
            mPopupKeysPanel = null
            installPopupKeysPlacerView(true)
        }
    }

    override fun onCancelPopupKeysPanel() {
        if (isShowingPopupKeysPanel()) {
            dismissPopupKeysPanel()
        }
    }

    override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent): Boolean {
        return true
    }

    private fun getLongPressTimeout(): Int {
        return Settings.getValues().mKeyLongpressTimeout
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        val accessibilityDelegate = mAccessibilityDelegate
        if (accessibilityDelegate != null && AccessibilityUtils.instance.isTouchExplorationEnabled) {
            return accessibilityDelegate.onHoverEvent(event)
        }
        return super.onHoverEvent(event)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        return when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mPointerId = e.getPointerId(0)
                onDown(e)
            }
            MotionEvent.ACTION_UP -> onUp(e)
            MotionEvent.ACTION_MOVE -> onMove(e)
            MotionEvent.ACTION_CANCEL -> onCancel(e)
            else -> false
        }
    }

    private fun getKey(x: Int, y: Int): Key? {
        return mKeyDetector.detectHitKey(x, y)
    }

    private fun onLongPressed(key: Key?) {
        if (isShowingPopupKeysPanel()) {
            return
        }

        if (key == null) {
            if (LOG) Log.d(TAG, "Long press ignored because detected key is null")
            return
        }

        val descriptionPanel = showDescription(key)
        val popupKeysPanel = showPopupKeysKeyboard(key)

        val x = mLastX
        val y = mLastY
        val panel = popupKeysPanel ?: descriptionPanel
        if (panel != null) {
            mPopupKeysKeyboardContainer.measure(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            val lastCoords = CoordinateUtils.newCoordinateArray(1, x, y)
            val pointX = if (mConfigShowPopupKeysKeyboardAtTouchedPoint)
                CoordinateUtils.x(lastCoords)
            else
                key.x + key.width / 2
            val pointY = key.y - (keyboard?.mVerticalGap ?: 0)
            panel.showPopupKeysPanel(this, this, pointX, pointY, mEmojiViewCallback)
        }

        if (popupKeysPanel != null) {
            val translatedX = popupKeysPanel.translateX(x)
            val translatedY = popupKeysPanel.translateY(y)
            popupKeysPanel.onDownEvent(translatedX, translatedY, mPointerId, 0)
            disallowParentInterceptTouchEvent(true)
        }
    }

    private fun showDescription(key: Key): PopupKeysPanel? {
        mDescriptionView.visibility = GONE
        val description = mEmojiViewCallback.getDescription(key.label ?: "")
        if (description == null) {
            return null
        }

        mDescriptionView.text = description
        mDescriptionView.setKeyDrawParams(key, keyDrawParams)
        mDescriptionView.visibility = VISIBLE
        return mDescriptionView
    }

    private fun registerPress(key: Key) {
        val r = Runnable { callListenerOnPressKey(key) }
        mPendingKeyDown = r
        mHandler.postDelayed(r, KEY_PRESS_DELAY_TIME)
    }

    private fun registerLongPress(key: Key) {
        val r = Runnable { onLongPressed(key) }
        mPendingLongPress = r
        mHandler.postDelayed(r, getLongPressTimeout().toLong())
    }

    fun callListenerOnReleaseKey(releasedKey: Key, withKeyRegistering: Boolean) {
        releasedKey.onReleased()
        invalidateKey(releasedKey)
        if (withKeyRegistering) {
            mEmojiViewCallback.onReleaseKey(releasedKey)
        }
    }

    fun callListenerOnPressKey(pressedKey: Key) {
        mPendingKeyDown = null
        pressedKey.onPressed()
        invalidateKey(pressedKey)
        mEmojiViewCallback.onPressKey(pressedKey)
    }

    fun releaseCurrentKey(withKeyRegistering: Boolean) {
        mPendingKeyDown?.let { mHandler.removeCallbacks(it) }
        mPendingKeyDown = null
        val currentKey = mCurrentKey ?: return
        callListenerOnReleaseKey(currentKey, withKeyRegistering)
        mCurrentKey = null
    }

    override fun cancelLongPress() {
        super.cancelLongPress()
        mPendingLongPress?.let { mHandler.removeCallbacks(it) }
        mPendingLongPress = null
    }

    fun onDown(e: MotionEvent): Boolean {
        val x = e.x.toInt()
        val y = e.y.toInt()
        val key = getKey(x, y)
        releaseCurrentKey(false)
        mCurrentKey = key
        if (key == null) {
            return false
        }
        registerPress(key)
        registerLongPress(key)

        mLastX = x
        mLastY = y
        return true
    }

    fun onUp(e: MotionEvent): Boolean {
        val x = e.x.toInt()
        val y = e.y.toInt()
        val key = getKey(x, y)
        val pendingKeyDown = mPendingKeyDown
        val currentKey = mCurrentKey
        releaseCurrentKey(false)

        val isShowingPopupKeysPanel = isShowingPopupKeysPanel()
        val popupPanel = mPopupKeysPanel
        if (isShowingPopupKeysPanel && popupPanel != null) {
            val eventTime = e.eventTime
            val translatedX = popupPanel.translateX(x)
            val translatedY = popupPanel.translateY(y)
            popupPanel.onUpEvent(translatedX, translatedY, mPointerId, eventTime)
            dismissPopupKeysPanel()
        } else if (key != null && key == currentKey && pendingKeyDown != null) {
            pendingKeyDown.run()
            mHandler.postDelayed({ callListenerOnReleaseKey(key, true) }, KEY_RELEASE_DELAY_TIME)
        } else if (key != null) {
            callListenerOnReleaseKey(key, true)
        }

        cancelLongPress()
        return true
    }

    fun onCancel(e: MotionEvent): Boolean {
        releaseCurrentKey(false)
        dismissPopupKeysPanel()
        cancelLongPress()
        return true
    }

    fun onMove(e: MotionEvent): Boolean {
        val x = e.x.toInt()
        val y = e.y.toInt()
        val key = getKey(x, y)
        val isShowingPopupKeysPanel = isShowingPopupKeysPanel()

        if (key != mCurrentKey && !isShowingPopupKeysPanel) {
            releaseCurrentKey(false)
            mCurrentKey = key
            if (key == null) {
                return false
            }
            registerPress(key)
            cancelLongPress()
            registerLongPress(key)
        }

        val movePanel = mPopupKeysPanel
        if (isShowingPopupKeysPanel && movePanel != null) {
            val eventTime = e.eventTime
            val translatedX = movePanel.translateX(x)
            val translatedY = movePanel.translateY(y)
            movePanel.onMoveEvent(translatedX, translatedY, mPointerId, eventTime)
        }

        mLastX = x
        mLastY = y
        return true
    }

    private fun disallowParentInterceptTouchEvent(disallow: Boolean) {
        val parent = parent
        if (parent == null) {
            Log.w(TAG, "Cannot disallow touch event interception, no parent found.")
            return
        }
        parent.requestDisallowInterceptTouchEvent(disallow)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mHandler.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val TAG = "EmojiPageKeyboardView"
        private const val LOG = false
        private const val KEY_PRESS_DELAY_TIME = 250L
        private const val KEY_RELEASE_DELAY_TIME = 30L

        private val EMPTY_EMOJI_VIEW_CALLBACK = object : EmojiViewCallback {
            override fun onPressKey(key: Key) {}
            override fun onReleaseKey(key: Key) {}
            override fun getDescription(emoji: String): String? = null
        }
    }
}
