/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal

import helium314.keyboard.latin.define.DebugFlags

class TouchPositionCorrection {
    private var mEnabled = false
    private var mXs: FloatArray? = null
    private var mYs: FloatArray? = null
    private var mRadii: FloatArray? = null

    fun load(data: Array<String>) {
        val dataLength = data.size
        if (dataLength % TOUCH_POSITION_CORRECTION_RECORD_SIZE != 0) {
            if (DebugFlags.DEBUG_ENABLED) {
                throw RuntimeException("the size of touch position correction data is invalid")
            }
            return
        }

        val length = dataLength / TOUCH_POSITION_CORRECTION_RECORD_SIZE
        val xs = FloatArray(length)
        val ys = FloatArray(length)
        val radii = FloatArray(length)
        try {
            for (i in 0 until dataLength) {
                val type = i % TOUCH_POSITION_CORRECTION_RECORD_SIZE
                val index = i / TOUCH_POSITION_CORRECTION_RECORD_SIZE
                val value = data[i].toFloat()
                if (type == 0) {
                    xs[index] = value
                } else if (type == 1) {
                    ys[index] = value
                } else {
                    radii[index] = value
                }
            }
            mXs = xs
            mYs = ys
            mRadii = radii
            mEnabled = dataLength > 0
        } catch (e: NumberFormatException) {
            if (DebugFlags.DEBUG_ENABLED) {
                throw RuntimeException("the number format for touch position correction data is invalid")
            }
            mEnabled = false
            mXs = null
            mYs = null
            mRadii = null
        }
    }

    fun setEnabled(enabled: Boolean) {
        mEnabled = enabled
    }

    val isValid: Boolean
        get() = mEnabled

    val rows: Int
        get() = mRadii?.size ?: 0

    @Suppress("UNUSED_PARAMETER")
    fun getX(row: Int): Float {
        return 0.0f
        // Touch position correction data for X coordinate is obsolete.
        // return mXs!![row]
    }

    fun getY(row: Int): Float {
        return mYs!![row]
    }

    fun getRadius(row: Int): Float {
        return mRadii!![row]
    }

    companion object {
        private const val TOUCH_POSITION_CORRECTION_RECORD_SIZE = 3
    }
}
