/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import helium314.keyboard.keyboard.emoji.EmojiViewCallback
import helium314.keyboard.keyboard.internal.KeyDrawParams
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.CoordinateUtils
import helium314.keyboard.latin.settings.Settings
import kotlin.math.max
import kotlin.math.min

/**
 * A view that displays popup text.
 */
class PopupTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = R.attr.popupKeysKeyboardViewStyle
) : TextView(context, attrs, defStyle), PopupKeysPanel {

    private val mCoordinates: IntArray = CoordinateUtils.newInstance()
    private val mTypeface: Typeface? = Settings.getInstance().customTypeface
    private var mController: PopupKeysPanel.Controller = PopupKeysPanel.EMPTY_CONTROLLER
    private var mOriginX = 0
    private var mOriginY = 0
    private var mKey: Key? = null
    private var mEmojiViewCallback: EmojiViewCallback? = null

    fun setKeyDrawParams(key: Key, drawParams: KeyDrawParams) {
        mKey = key
        Settings.getValues().mColors.setBackground(this, ColorType.KEY_PREVIEW_BACKGROUND)
        setTextColor(drawParams.mPreviewTextColor)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, (key.selectHintTextSize(drawParams) shl 1).toFloat())
        typeface = mTypeface ?: key.selectTypeface(drawParams)
    }

    override fun showPopupKeysPanel(
        parentView: View,
        controller: PopupKeysPanel.Controller,
        pointX: Int,
        pointY: Int,
        listener: KeyboardActionListener
    ) {
        showPopupKeysPanelInternal(parentView, controller, pointX, pointY)
    }

    override fun showPopupKeysPanel(
        parentView: View,
        controller: PopupKeysPanel.Controller,
        pointX: Int,
        pointY: Int,
        emojiViewCallback: EmojiViewCallback
    ) {
        mEmojiViewCallback = emojiViewCallback
        showPopupKeysPanelInternal(parentView, controller, pointX, pointY)
    }

    private fun showPopupKeysPanelInternal(
        parentView: View,
        controller: PopupKeysPanel.Controller,
        pointX: Int,
        pointY: Int
    ) {
        mController = controller
        val container = getContainerView()
        // The coordinates of panel's left-top corner in parentView's coordinate system.
        // We need to consider background drawable paddings and user-configured vertical offset.
        val verticalOffsetPx = (Settings.getValues().mPopupKeysVerticalOffset * resources.displayMetrics.density).toInt()
        val x = pointX - measuredWidth / 2 - container.paddingLeft - paddingLeft
        val y = pointY - container.measuredHeight + container.paddingBottom + paddingBottom - verticalOffsetPx

        parentView.getLocationInWindow(mCoordinates)
        // Ensure the horizontal position of the panel does not extend past the parentView edges.
        val maxX = parentView.measuredWidth - container.measuredWidth
        val panelFinalX = max(0, min(maxX, x))
        val panelX = panelFinalX + CoordinateUtils.x(mCoordinates)
        val rawPanelY = y + CoordinateUtils.y(mCoordinates)
        val panelY = max(0, rawPanelY)
        container.x = panelX.toFloat()
        container.y = panelY.toFloat()

        val clampedY = panelY - CoordinateUtils.y(mCoordinates)
        mOriginX = panelFinalX + container.paddingLeft
        mOriginY = clampedY + container.paddingTop

        val keyCenterXInContainer = (pointX - panelFinalX).toFloat()
        container.pivotX = keyCenterXInContainer.coerceIn(0f, container.measuredWidth.toFloat())
        container.pivotY = container.measuredHeight.toFloat()

        controller.setLayoutGravity(Gravity.NO_GRAVITY)
        controller.onShowPopupKeysPanel(this)
    }

    override fun onDownEvent(x: Int, y: Int, pointerId: Int, eventTime: Long) {}

    override fun onMoveEvent(x: Int, y: Int, pointerId: Int, eventTime: Long) {}

    override fun onUpEvent(x: Int, y: Int, pointerId: Int, eventTime: Long) {
        mKey?.let { mEmojiViewCallback?.onReleaseKey(it) }
    }

    override fun dismissPopupKeysPanel() {
        if (!isShowingInParent) {
            return
        }
        mController.onDismissPopupKeysPanel()
    }

    override fun translateX(x: Int): Int {
        return x - mOriginX
    }

    override fun translateY(y: Int): Int {
        return y - mOriginY
    }
}
