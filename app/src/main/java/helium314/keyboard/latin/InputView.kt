/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.os.Build
import android.view.WindowInsets
import helium314.keyboard.accessibility.AccessibilityUtils
import helium314.keyboard.keyboard.MainKeyboardView
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.suggestions.SuggestionStripView
import kotlin.math.min

class InputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val mInputViewRect = Rect()
    private var mMainKeyboardView: MainKeyboardView? = null
    private var mKeyboardTopPaddingForwarder: KeyboardTopPaddingForwarder? = null
    private var mMoreSuggestionsViewCanceler: MoreSuggestionsViewCanceler? = null
    private var mActiveForwarder: MotionEventForwarder<*, *>? = null
    private var mNavBarBottomInsets = 0
    /** the bottom padding for the navigation bar that was applied last, see [navBarOverlap] */
    private var mAppliedNavPadding = -1

    private fun isFloatingMode(): Boolean = FloatingKeyboardManager.isFloatingMode(context)

    override fun dispatchApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (isFloatingMode()) {
            resetChildrenFloatingPadding()
            updateBottomPadding()
            return insets
        }
        return super.dispatchApplyWindowInsets(insets)
    }

    private fun getNavInsets(): Int {
        if (mNavBarBottomInsets > 0) return mNavBarBottomInsets
        val insets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            rootWindowInsets?.getInsets(WindowInsets.Type.navigationBars())?.bottom ?: 0
        } else {
            @Suppress("DEPRECATION")
            rootWindowInsets?.systemWindowInsetBottom ?: 0
        }
        if (insets > 0) {
            mNavBarBottomInsets = insets
        }
        return mNavBarBottomInsets
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val navInsets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        } else {
            @Suppress("DEPRECATION")
            insets.systemWindowInsetBottom
        }
        if (navInsets > 0) {
            mNavBarBottomInsets = navInsets
        }
        if (isFloatingMode()) {
            resetChildrenFloatingPadding()
            updateBottomPadding()
            return insets
        }
        updateBottomPadding()
        return withBottomInset(super.onApplyWindowInsets(insets), mAppliedNavPadding)
    }

    /**
     * The children fit the system windows, so they pad themselves with the bottom inset they get. Some devices
     * (e.g. Samsung on Android 12) report the navigation bar inset although the window already leaves room for it,
     * which doubled the space below the keys. Give them what the keyboard really overlaps instead.
     */
    private fun withBottomInset(insets: WindowInsets, bottom: Int): WindowInsets {
        @Suppress("DEPRECATION")
        return if (bottom < 0 || insets.systemWindowInsetBottom == bottom) insets
        else insets.replaceSystemWindowInsets(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, bottom)
    }

    /** The navigation bar inset as reported, or the height of the IME's own navigation bar if that is taller */
    private fun reportedNavInset(): Int =
        navigationBarFrameHeight().takeIf { it > 0 } ?: if (mNavBarBottomInsets > 0) mNavBarBottomInsets else getNavInsets()

    /** Bottom padding needed for the navigation bar; known only once this view is laid out, until then the reported inset is used */
    private fun navPadding(): Int {
        val reported = reportedNavInset()
        if (reported <= 0 || !isLaidOut || height == 0 || rootView.height == 0) return reported
        val location = IntArray(2)
        getLocationInWindow(location)
        return navBarOverlap(reported, location[1] + height, rootView.height)
    }

    fun resetChildrenFloatingPadding() {
        setPadding(0, 0, 0, 0)
        fitsSystemWindows = false
        val viewIds = intArrayOf(
            R.id.main_keyboard_frame,
            R.id.keyboard_view_wrapper,
            R.id.keyboard_view,
            R.id.emoji_palettes_view,
            R.id.clipboard_history_view,
            R.id.touchpad_view,
            R.id.floating_bottom_bar
        )
        for (id in viewIds) {
            findViewById<View>(id)?.let {
                it.setPadding(0, 0, 0, 0)
                it.fitsSystemWindows = false
            }
        }
    }

    private var observedNavBarFrame: View? = null

    /**
     * Height of the IME's own navigation bar, which is drawn over the bottom of this view. It can be taller than
     * the system's navigation bar inset, so reserve its actual height. Updates padding when it gets laid out.
     */
    private fun navigationBarFrameHeight(): Int {
        val frame = LatinIME.getInstance()?.findNavigationBarFrame() ?: return 0
        if (frame !== observedNavBarFrame) {
            observedNavBarFrame = frame
            frame.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
                if (bottom - top != oldBottom - oldTop) post { updateBottomPadding() }
            }
        }
        return if (frame.visibility == View.VISIBLE) frame.height else 0
    }

    fun updateBottomPadding() {
        val mainKeyboardFrame = findViewById<View>(R.id.main_keyboard_frame) ?: return
        if (isFloatingMode()) {
            resetChildrenFloatingPadding()
            if (mainKeyboardFrame.paddingBottom != 0 || mainKeyboardFrame.paddingTop != 0 || mainKeyboardFrame.paddingLeft != 0 || mainKeyboardFrame.paddingRight != 0) {
                mainKeyboardFrame.setPadding(0, 0, 0, 0)
                requestLayout()
            }
            return
        }
        val viewIds = intArrayOf(
            R.id.keyboard_view,
            R.id.emoji_palettes_view,
            R.id.clipboard_history_view,
            R.id.touchpad_view
        )
        val insets = navPadding()
        mAppliedNavPadding = insets
        val wrapper = findViewById<View>(R.id.keyboard_view_wrapper)
        val showToolbarOnly = wrapper?.visibility != View.VISIBLE
        val bottomPadding = if (showToolbarOnly) insets else 0
        if (mainKeyboardFrame.paddingBottom != bottomPadding) {
            mainKeyboardFrame.setPadding(
                mainKeyboardFrame.paddingLeft,
                mainKeyboardFrame.paddingTop,
                mainKeyboardFrame.paddingRight,
                bottomPadding
            )
            requestLayout()
        }
        val childPadding = if (showToolbarOnly) 0 else insets
        for (id in viewIds) {
            findViewById<View>(id)?.let {
                it.fitsSystemWindows = true
                if (it.paddingBottom != childPadding) {
                    it.setPadding(it.paddingLeft, it.paddingTop, it.paddingRight, childPadding)
                    it.requestLayout()
                }
            }
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        val suggestionStripView: SuggestionStripView? = findViewById(R.id.suggestion_strip_view)
        val mainKeyboardView: MainKeyboardView? = findViewById(R.id.keyboard_view)
        mMainKeyboardView = mainKeyboardView
        if (mainKeyboardView != null && suggestionStripView != null) {
            mKeyboardTopPaddingForwarder = KeyboardTopPaddingForwarder(mainKeyboardView, suggestionStripView)
            mMoreSuggestionsViewCanceler = MoreSuggestionsViewCanceler(mainKeyboardView, suggestionStripView)
        }
        findViewById<View>(R.id.main_keyboard_frame)?.let { frame ->
            Settings.getValues().mColors.setBackground(frame, ColorType.MAIN_BACKGROUND)
        }
        updateBottomPadding()
        // where this view ends in the window is known after layout: correct the padding if it is not what is needed
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (!isFloatingMode() && mAppliedNavPadding != navPadding()) post {
                updateBottomPadding()
                requestApplyInsets()
            }
        }
        // Work around inset application being unreliable
        post { requestApplyInsets() }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        LatinIME.getInstance()?.floatingKeyboardManager?.resetDragAndResizeState()
    }

    fun setKeyboardTopPadding(keyboardTopPadding: Int) {
        mKeyboardTopPaddingForwarder?.setKeyboardTopPadding(keyboardTopPadding)
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        if (AccessibilityUtils.instance.isTouchExplorationEnabled && mMainKeyboardView?.isShowingPopupKeysPanel() == true) {
            // With accessibility mode on, discard hover events while a popup keys keyboard is shown.
            // The PopupKeysKeyboard receives hover events directly from the platform.
            return true
        }
        return super.dispatchHoverEvent(event)
    }

    override fun onInterceptTouchEvent(me: MotionEvent): Boolean {
        if (isFloatingMode()) {
            return false
        }
        val rect = mInputViewRect
        getGlobalVisibleRect(rect)
        val index = me.actionIndex
        val x = me.getX(index).toInt() + rect.left
        val y = me.getY(index).toInt() + rect.top

        // The touch events that hit the top padding of keyboard should be forwarded to
        // SuggestionStripView.
        if (mKeyboardTopPaddingForwarder?.onInterceptTouchEvent(x, y, me) == true) {
            mActiveForwarder = mKeyboardTopPaddingForwarder
            return true
        }

        // To cancel MoreSuggestionsView, we should intercept a touch event to
        // MainKeyboardView and dismiss the MoreSuggestionsView.
        if (mMoreSuggestionsViewCanceler?.onInterceptTouchEvent(x, y, me) == true) {
            mActiveForwarder = mMoreSuggestionsViewCanceler
            return true
        }

        mActiveForwarder = null
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(me: MotionEvent): Boolean {
        val forwarder = mActiveForwarder ?: return super.onTouchEvent(me)

        val rect = mInputViewRect
        getGlobalVisibleRect(rect)
        val index = me.actionIndex
        val x = me.getX(index).toInt() + rect.left
        val y = me.getY(index).toInt() + rect.top
        return forwarder.onTouchEvent(x, y, me)
    }

    /**
     * This class forwards series of [MotionEvent]s from `SenderView` to `ReceiverView`.
     */
    private abstract class MotionEventForwarder<SenderView : View, ReceiverView : View>(
        protected val mSenderView: SenderView,
        protected val mReceiverView: ReceiverView
    ) {
        protected val mEventSendingRect = Rect()
        protected val mEventReceivingRect = Rect()

        // Return true if a touch event of global coordinate x, y needs to be forwarded.
        protected abstract fun needsToForward(x: Int, y: Int): Boolean

        // Translate global x-coordinate to ReceiverView local coordinate.
        protected open fun translateX(x: Int): Int = x - mEventReceivingRect.left

        // Translate global y-coordinate to ReceiverView local coordinate.
        protected open fun translateY(y: Int): Int = y - mEventReceivingRect.top

        /**
         * Callback when a [MotionEvent] is forwarded.
         */
        protected open fun onForwardingEvent(me: MotionEvent) {}

        // Returns true if a MotionEvent is needed to be forwarded to ReceiverView. Otherwise returns false.
        fun onInterceptTouchEvent(x: Int, y: Int, me: MotionEvent): Boolean {
            // Forwards a MotionEvent only if both SenderView and ReceiverView are visible.
            if (mSenderView.visibility != View.VISIBLE || mReceiverView.visibility != View.VISIBLE) {
                return false
            }
            mSenderView.getGlobalVisibleRect(mEventSendingRect)
            if (!mEventSendingRect.contains(x, y)) {
                return false
            }

            if (me.actionMasked == MotionEvent.ACTION_DOWN) {
                // If the down event happens in the forwarding area, successive
                // MotionEvents should be forwarded to ReceiverView.
                return needsToForward(x, y)
            }

            return false
        }

        // Returns true if a MotionEvent is forwarded to ReceiverView. Otherwise returns false.
        fun onTouchEvent(x: Int, y: Int, me: MotionEvent): Boolean {
            mReceiverView.getGlobalVisibleRect(mEventReceivingRect)
            // Translate global coordinates to ReceiverView local coordinates.
            me.setLocation(translateX(x).toFloat(), translateY(y).toFloat())
            mReceiverView.dispatchTouchEvent(me)
            onForwardingEvent(me)
            return true
        }
    }

    /**
     * This class forwards [MotionEvent]s happened in the top padding of
     * [MainKeyboardView] to [SuggestionStripView].
     */
    private class KeyboardTopPaddingForwarder(
        mainKeyboardView: MainKeyboardView,
        suggestionStripView: SuggestionStripView
    ) : MotionEventForwarder<MainKeyboardView, SuggestionStripView>(mainKeyboardView, suggestionStripView) {

        private var mKeyboardTopPadding = 0

        fun setKeyboardTopPadding(keyboardTopPadding: Int) {
            mKeyboardTopPadding = keyboardTopPadding
        }

        private fun isInKeyboardTopPadding(y: Int): Boolean = y < mEventSendingRect.top + mKeyboardTopPadding

        override fun needsToForward(x: Int, y: Int): Boolean {
            // Forwarding an event only when MainKeyboardView is visible.
            // Because the visibility of MainKeyboardView is controlled by its parent
            // view in KeyboardSwitcher#setMainKeyboardFrame(), we should check the
            // visibility of the parent view.
            val mainKeyboardFrame = mSenderView.parent as? View ?: return false
            return mainKeyboardFrame.visibility == View.VISIBLE && isInKeyboardTopPadding(y)
        }

        override fun translateY(y: Int): Int {
            val translatedY = super.translateY(y)
            return if (isInKeyboardTopPadding(y)) {
                // The forwarded event should have coordinates that are inside of the target.
                min(translatedY, mEventReceivingRect.height() - 1)
            } else {
                translatedY
            }
        }
    }

    /**
     * This class forwards [MotionEvent]s happened in the [MainKeyboardView] to
     * [SuggestionStripView] when the [helium314.keyboard.latin.suggestions.MoreSuggestionsView] is showing.
     * [SuggestionStripView] dismisses MoreSuggestionsView when it receives any event outside of it.
     */
    private class MoreSuggestionsViewCanceler(
        mainKeyboardView: MainKeyboardView,
        suggestionStripView: SuggestionStripView
    ) : MotionEventForwarder<MainKeyboardView, SuggestionStripView>(mainKeyboardView, suggestionStripView) {

        override fun needsToForward(x: Int, y: Int): Boolean {
            return mReceiverView.isShowingMoreSuggestionPanel && mEventSendingRect.contains(x, y)
        }

        override fun onForwardingEvent(me: MotionEvent) {
            if (me.actionMasked == MotionEvent.ACTION_DOWN) {
                mReceiverView.dismissMoreSuggestionsPanel()
            }
        }
    }
}

/**
 * The part of the navigation bar (the bottom [reportedInset] pixels of a window that is [windowHeight] high) that a
 * view ending at [viewBottom] (in window coordinates) lies under. When the window already leaves room for the
 * navigation bar, the view ends above it and nothing has to be reserved, whatever the reported inset is.
 */
internal fun navBarOverlap(reportedInset: Int, viewBottom: Int, windowHeight: Int): Int =
    (viewBottom - (windowHeight - reportedInset)).coerceIn(0, maxOf(reportedInset, 0))
