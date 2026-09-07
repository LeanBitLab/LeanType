/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal

import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import helium314.keyboard.keyboard.Key
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.CoordinateUtils
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ViewLayoutUtils
import java.util.ArrayDeque
import java.util.HashMap

class KeyPreviewChoreographer(private val mParams: KeyPreviewDrawParams) {
    private val mFreeKeyPreviewViews = ArrayDeque<KeyPreviewView>()
    private val mShowingKeyPreviewViews = HashMap<Key, KeyPreviewView>()

    fun getKeyPreviewView(key: Key, placerView: ViewGroup): KeyPreviewView {
        val showingView = mShowingKeyPreviewViews.remove(key)
        if (showingView != null) return showingView

        val freeView = mFreeKeyPreviewViews.poll()
        if (freeView != null) return freeView

        val context = placerView.context
        val newView = KeyPreviewView(context, null)
        newView.setBackgroundResource(mParams.mPreviewBackgroundResId)
        placerView.addView(newView, ViewLayoutUtils.newLayoutParam(placerView, 0, 0))
        return newView
    }

    fun isShowingKeyPreview(key: Key?): Boolean {
        return key != null && mShowingKeyPreviewViews.containsKey(key)
    }

    fun dismissKeyPreview(key: Key?) {
        if (key == null) return
        val keyPreviewView = mShowingKeyPreviewViews.remove(key) ?: return

        keyPreviewView.tag = null
        keyPreviewView.animate()
            .scaleX(0.8f)
            .scaleY(0.8f)
            .alpha(0f)
            .setDuration(50)
            .withEndAction {
                keyPreviewView.visibility = View.INVISIBLE
                mFreeKeyPreviewViews.add(keyPreviewView)
            }
            .start()
    }

    fun placeAndShowKeyPreview(
        key: Key,
        iconsSet: KeyboardIconsSet,
        drawParams: KeyDrawParams,
        fullKeyboardViewWidth: Int,
        keyboardOrigin: IntArray,
        placerView: ViewGroup
    ) {
        val keyPreviewView = getKeyPreviewView(key, placerView)
        placeKeyPreview(key, keyPreviewView, iconsSet, drawParams, fullKeyboardViewWidth, keyboardOrigin)
        showKeyPreview(key, keyPreviewView)
    }

    private fun placeKeyPreview(
        key: Key,
        keyPreviewView: KeyPreviewView,
        iconsSet: KeyboardIconsSet,
        drawParams: KeyDrawParams,
        fullKeyboardViewWidth: Int,
        originCoords: IntArray
    ) {
        keyPreviewView.setPreviewVisual(key, iconsSet, drawParams)
        keyPreviewView.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        mParams.setGeometry(keyPreviewView)

        val previewWidth = keyPreviewView.measuredWidth
        val previewHeight = keyPreviewView.measuredHeight
        val keyDrawWidth = key.drawWidth

        val keyPreviewPosition: Int
        var previewX = key.drawX - (previewWidth - keyDrawWidth) / 2 + CoordinateUtils.x(originCoords)
        if (previewX < 0) {
            previewX = 0
            keyPreviewPosition = KeyPreviewView.POSITION_LEFT
        } else if (previewX > fullKeyboardViewWidth - previewWidth) {
            previewX = fullKeyboardViewWidth - previewWidth
            keyPreviewPosition = KeyPreviewView.POSITION_RIGHT
        } else {
            keyPreviewPosition = KeyPreviewView.POSITION_MIDDLE
        }

        val hasPopupKeys = key.popupKeys != null
        keyPreviewView.setPreviewBackground(hasPopupKeys, keyPreviewPosition)
        val colors = Settings.getValues().mColors
        colors.setBackground(keyPreviewView, ColorType.KEY_PREVIEW_BACKGROUND)

        val previewY = key.y - previewHeight + key.height - mParams.mPreviewOffset + CoordinateUtils.y(originCoords)

        ViewLayoutUtils.placeViewAt(keyPreviewView, previewX, previewY, previewWidth, previewHeight)
        keyPreviewView.pivotX = previewWidth / 2.0f
        keyPreviewView.pivotY = previewHeight.toFloat()
    }

    private fun showKeyPreview(key: Key, keyPreviewView: KeyPreviewView) {
        keyPreviewView.visibility = View.VISIBLE
        keyPreviewView.animate().cancel()
        keyPreviewView.scaleX = 0.5f
        keyPreviewView.scaleY = 0.5f
        keyPreviewView.alpha = 0f
        keyPreviewView.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(75)
            .setInterpolator(OvershootInterpolator())
            .start()
        mShowingKeyPreviewViews[key] = keyPreviewView
    }

    fun clear() {
        for (view in mShowingKeyPreviewViews.values) {
            view.visibility = View.INVISIBLE
        }
        mShowingKeyPreviewViews.clear()
        mFreeKeyPreviewViews.clear()
    }
}
