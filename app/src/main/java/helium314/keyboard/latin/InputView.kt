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
import androidx.core.view.doOnNextLayout
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

    override fun onFinishInflate() {
        super.onFinishInflate()
        val suggestionStripView: SuggestionStripView? = findViewById(R.id.suggestion_strip_view)
        val mainKeyboardView: MainKeyboardView? = findViewById(R.id.keyboard_view)
        mMainKeyboardView = mainKeyboardView
        if (mainKeyboardView != null && suggestionStripView != null) {
            mKeyboardTopPaddingForwarder = KeyboardTopPaddingForwarder(mainKeyboardView, suggestionStripView)
            mMoreSuggestionsViewCanceler = MoreSuggestionsViewCanceler(mainKeyboardView, suggestionStripView)
        }

        doOnNextLayout { onNextLayout(it) }
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

    private fun onNextLayout(v: View) {
        findViewById<View>(R.id.main_keyboard_frame)?.let { frame ->
            Settings.getValues().mColors.setBackground(frame, ColorType.MAIN_BACKGROUND)
        }
        // Work around inset application being unreliable
        requestApplyInsets()
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
