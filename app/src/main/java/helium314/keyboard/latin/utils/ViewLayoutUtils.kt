/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.utils

import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.Window
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout

object ViewLayoutUtils {
    @JvmStatic
    fun newLayoutParam(placer: ViewGroup?, width: Int, height: Int): MarginLayoutParams {
        return when (placer) {
            is FrameLayout -> FrameLayout.LayoutParams(width, height)
            is RelativeLayout -> RelativeLayout.LayoutParams(width, height)
            null -> throw NullPointerException("placer is null")
            else -> throw IllegalArgumentException("placer is neither FrameLayout nor RelativeLayout: ${placer.javaClass.name}")
        }
    }

    @JvmStatic
    fun placeViewAt(view: View, x: Int, y: Int, w: Int, h: Int) {
        val lp = view.layoutParams
        if (lp is MarginLayoutParams) {
            lp.width = w
            lp.height = h
            lp.setMargins(x, y, 0, 0)
        }
    }

    @JvmStatic
    fun updateLayoutHeightOf(window: Window, layoutHeight: Int) {
        val params = window.attributes
        if (params != null && params.height != layoutHeight) {
            params.height = layoutHeight
            window.attributes = params
        }
    }

    @JvmStatic
    fun updateLayoutHeightOf(view: View?, layoutHeight: Int) {
        val params = view?.layoutParams
        if (params != null && params.height != layoutHeight) {
            params.height = layoutHeight
            view.layoutParams = params
        }
    }

    @JvmStatic
    fun updateLayoutGravityOf(view: View?, layoutGravity: Int) {
        val lp = view?.layoutParams ?: return
        when (lp) {
            is LinearLayout.LayoutParams -> {
                if (lp.gravity != layoutGravity) {
                    lp.gravity = layoutGravity
                    view.layoutParams = lp
                }
            }
            is FrameLayout.LayoutParams -> {
                if (lp.gravity != layoutGravity) {
                    lp.gravity = layoutGravity
                    view.layoutParams = lp
                }
            }
            else -> {
                throw IllegalArgumentException("Layout parameter doesn't have gravity: ${lp.javaClass.name}")
            }
        }
    }
}
