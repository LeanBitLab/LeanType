/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard

import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import helium314.keyboard.keyboard.emoji.EmojiViewCallback

interface PopupKeysPanel {
    interface Controller {
        /**
         * Set the layout gravity.
         * @param layoutGravity requested by the popup
         */
        fun setLayoutGravity(layoutGravity: Int) {}

        /**
         * Add the [PopupKeysPanel] to the target view.
         * @param panel the panel to be shown.
         */
        fun onShowPopupKeysPanel(panel: PopupKeysPanel)

        /**
         * Remove the current [PopupKeysPanel] from the target view.
         */
        fun onDismissPopupKeysPanel()

        /**
         * Instructs the parent to cancel the panel (e.g., when entering a different input mode).
         */
        fun onCancelPopupKeysPanel()
    }

    /**
     * Initializes the layout and event handling of this [PopupKeysPanel] and calls the
     * controller's onShowPopupKeysPanel to add the panel's container view.
     *
     * @param parentView the parent view of this [PopupKeysPanel]
     * @param controller the controller that can dismiss this [PopupKeysPanel]
     * @param pointX x coordinate of this [PopupKeysPanel]
     * @param pointY y coordinate of this [PopupKeysPanel]
     * @param listener the listener that will receive keyboard action from this
     * [PopupKeysPanel].
     */
    fun showPopupKeysPanel(
        parentView: View,
        controller: Controller,
        pointX: Int,
        pointY: Int,
        listener: KeyboardActionListener
    )

    /**
     * Initializes the layout and event handling of this [PopupKeysPanel] and calls the
     * controller's onShowPopupKeysPanel to add the panel's container view.
     * Same as [PopupKeysPanel.showPopupKeysPanel], but with a [EmojiViewCallback].
     *
     * @param parentView the parent view of this [PopupKeysPanel]
     * @param controller the controller that can dismiss this [PopupKeysPanel]
     * @param pointX x coordinate of this [PopupKeysPanel]
     * @param pointY y coordinate of this [PopupKeysPanel]
     * @param emojiViewCallback to receive keyboard actions from this [PopupKeysPanel].
     */
    fun showPopupKeysPanel(
        parentView: View,
        controller: Controller,
        pointX: Int,
        pointY: Int,
        emojiViewCallback: EmojiViewCallback
    )

    /**
     * Dismisses the popup keys panel and calls the controller's onDismissPopupKeysPanel to remove
     * the panel's container view.
     */
    fun dismissPopupKeysPanel()

    /**
     * Process a move event on the popup keys panel.
     *
     * @param x translated x coordinate of the touch point
     * @param y translated y coordinate of the touch point
     * @param pointerId pointer id touch point
     * @param eventTime timestamp of touch point
     */
    fun onMoveEvent(x: Int, y: Int, pointerId: Int, eventTime: Long)

    /**
     * Process a down event on the popup keys panel.
     *
     * @param x translated x coordinate of the touch point
     * @param y translated y coordinate of the touch point
     * @param pointerId pointer id touch point
     * @param eventTime timestamp of touch point
     */
    fun onDownEvent(x: Int, y: Int, pointerId: Int, eventTime: Long)

    /**
     * Process an up event on the popup keys panel.
     *
     * @param x translated x coordinate of the touch point
     * @param y translated y coordinate of the touch point
     * @param pointerId pointer id touch point
     * @param eventTime timestamp of touch point
     */
    fun onUpEvent(x: Int, y: Int, pointerId: Int, eventTime: Long)

    /**
     * Translate X-coordinate of touch event to the local X-coordinate of this
     * [PopupKeysPanel].
     *
     * @param x the global X-coordinate
     * @return the local X-coordinate to this [PopupKeysPanel]
     */
    fun translateX(x: Int): Int

    /**
     * Translate Y-coordinate of touch event to the local Y-coordinate of this
     * [PopupKeysPanel].
     *
     * @param y the global Y-coordinate
     * @return the local Y-coordinate to this [PopupKeysPanel]
     */
    fun translateY(y: Int): Int

    fun getContainerView(): View = (this as View).parent as View

    /**
     * Show this [PopupKeysPanel] in the parent view with a smooth entrance animation.
     *
     * @param parentView the [ViewGroup] that hosts this [PopupKeysPanel].
     */
    fun showInParent(parentView: ViewGroup) {
        val containerView = getContainerView()
        val currentParent = containerView.parent as? ViewGroup
        if (currentParent === parentView) {
            containerView.animate().cancel()
        } else {
            removeFromParent()
            parentView.addView(containerView)
        }
        containerView.alpha = 0f
        containerView.scaleX = 0.75f
        containerView.scaleY = 0.75f
        containerView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(110)
            .setInterpolator(OvershootInterpolator(0.85f))
            .start()
    }

    /**
     * Dismiss this [PopupKeysPanel] with a smooth exit animation.
     */
    fun dismissInParent(onEnd: () -> Unit = {}) {
        val containerView = getContainerView()
        val currentParent = containerView.parent as? ViewGroup
        if (currentParent == null) {
            onEnd()
            return
        }
        containerView.animate().cancel()
        containerView.animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(70)
            .setInterpolator(AccelerateInterpolator(1.5f))
            .withEndAction {
                removeFromParent()
                onEnd()
            }
            .start()
    }

    /**
     * Remove this [PopupKeysPanel] from the parent view immediately.
     */
    fun removeFromParent() {
        val containerView = getContainerView()
        containerView.animate().cancel()
        containerView.scaleX = 1f
        containerView.scaleY = 1f
        containerView.alpha = 1f
        val currentParent = containerView.parent as? ViewGroup
        currentParent?.removeView(containerView)
    }

    /**
     * Return whether the panel is currently being shown.
     */
    val isShowingInParent: Boolean
        get() = getContainerView().parent != null

    companion object {
        val EMPTY_CONTROLLER: Controller = object : Controller {
            override fun onShowPopupKeysPanel(panel: PopupKeysPanel) {}
            override fun onDismissPopupKeysPanel() {}
            override fun onCancelPopupKeysPanel() {}
        }
    }
}
