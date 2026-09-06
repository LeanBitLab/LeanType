/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.android.inputmethod.keyboard

import android.graphics.Rect
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.internal.TouchPositionCorrection
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.Log
import java.util.Collections

class ProximityInfo(
    gridWidth: Int,
    gridHeight: Int,
    minWidth: Int,
    height: Int,
    mostCommonKeyWidth: Int,
    mostCommonKeyHeight: Int,
    sortedKeys: List<Key>,
    touchPositionCorrection: TouchPositionCorrection
) {
    companion object {
        private const val TAG = "ProximityInfo"
        private const val DEBUG = false
        const val MAX_PROXIMITY_CHARS_SIZE = 16
        private const val SEARCH_DISTANCE = 1.2f
        private val EMPTY_KEY_LIST = Collections.emptyList<Key>()
        private const val DEFAULT_TOUCH_POSITION_CORRECTION_RADIUS = 0.15f

        init {
            JniUtils.loadNativeLibrary()
        }

        @JvmStatic
        fun needsProximityInfo(key: Key): Boolean {
            return key.code >= Constants.CODE_SPACE
        }

        private fun getProximityInfoKeysCount(keys: List<Key>): Int {
            var count = 0
            for (key in keys) {
                if (needsProximityInfo(key)) count++
            }
            return count
        }

        @JvmStatic
        private external fun setProximityInfoNative(
            displayWidth: Int, displayHeight: Int, gridWidth: Int, gridHeight: Int,
            mostCommonKeyWidth: Int, mostCommonKeyHeight: Int, proximityCharsArray: IntArray,
            keyCount: Int, keyXCoordinates: IntArray, keyYCoordinates: IntArray,
            keyWidths: IntArray, keyHeights: IntArray, keyCharCodes: IntArray,
            sweetSpotCenterXs: FloatArray?, sweetSpotCenterYs: FloatArray?, sweetSpotRadii: FloatArray?
        ): Long

        @JvmStatic
        private external fun releaseProximityInfoNative(nativeProximityInfo: Long)
    }

    private val mGridWidth: Int = gridWidth
    private val mGridHeight: Int = gridHeight
    private val mGridSize: Int = mGridWidth * mGridHeight
    private val mCellWidth: Int = (minWidth + mGridWidth - 1) / mGridWidth
    private val mCellHeight: Int = (height + mGridHeight - 1) / mGridHeight
    private val mKeyboardMinWidth: Int = minWidth
    private val mKeyboardHeight: Int = height
    private val mMostCommonKeyWidth: Int = mostCommonKeyWidth
    private val mMostCommonKeyHeight: Int = mostCommonKeyHeight
    private val mSortedKeys: List<Key> = sortedKeys
    @Suppress("UNCHECKED_CAST")
    private val mGridNeighbors: Array<List<Key>?> = arrayOfNulls(mGridSize)
    private var mNativeProximityInfo: Long = 0

    init {
        if (minWidth != 0 && height != 0) {
            computeNearestNeighbors()
            try {
                mNativeProximityInfo = createNativeProximityInfo(touchPositionCorrection)
            } catch (e: Throwable) {
                Log.e(TAG, "could not create proximity info", e)
                mNativeProximityInfo = 0
            }
        }
    }

    private fun createNativeProximityInfo(touchPositionCorrection: TouchPositionCorrection): Long {
        val proximityCharsArray = IntArray(mGridSize * MAX_PROXIMITY_CHARS_SIZE)
        proximityCharsArray.fill(Constants.NOT_A_CODE)
        for (i in 0 until mGridSize) {
            val neighborKeys = mGridNeighbors[i] ?: continue
            val proximityCharsLength = neighborKeys.size
            var infoIndex = i * MAX_PROXIMITY_CHARS_SIZE
            for (j in 0 until proximityCharsLength) {
                val neighborKey = neighborKeys[j]
                if (!needsProximityInfo(neighborKey)) continue
                proximityCharsArray[infoIndex] = neighborKey.code
                infoIndex++
            }
        }

        val keyCount = getProximityInfoKeysCount(mSortedKeys)
        val keyXCoordinates = IntArray(keyCount)
        val keyYCoordinates = IntArray(keyCount)
        val keyWidths = IntArray(keyCount)
        val keyHeights = IntArray(keyCount)
        val keyCharCodes = IntArray(keyCount)
        val sweetSpotCenterXs: FloatArray?
        val sweetSpotCenterYs: FloatArray?
        val sweetSpotRadii: FloatArray?

        var infoIndex = 0
        for (keyIndex in 0 until mSortedKeys.size) {
            val key = mSortedKeys[keyIndex]
            if (!needsProximityInfo(key)) continue
            keyXCoordinates[infoIndex] = key.x
            keyYCoordinates[infoIndex] = key.y
            keyWidths[infoIndex] = key.width
            keyHeights[infoIndex] = key.height
            keyCharCodes[infoIndex] = key.code
            infoIndex++
        }

        if (touchPositionCorrection.isValid) {
            sweetSpotCenterXs = FloatArray(keyCount)
            sweetSpotCenterYs = FloatArray(keyCount)
            sweetSpotRadii = FloatArray(keyCount)
            val rows = touchPositionCorrection.rows
            val defaultRadius = DEFAULT_TOUCH_POSITION_CORRECTION_RADIUS * Math.hypot(mMostCommonKeyWidth.toDouble(), mMostCommonKeyHeight.toDouble()).toFloat()
            infoIndex = 0
            for (keyIndex in 0 until mSortedKeys.size) {
                val key = mSortedKeys[keyIndex]
                if (!needsProximityInfo(key)) continue
                val hitBox: Rect = key.hitBox
                sweetSpotCenterXs[infoIndex] = hitBox.exactCenterX()
                sweetSpotCenterYs[infoIndex] = hitBox.exactCenterY()
                sweetSpotRadii[infoIndex] = defaultRadius
                val row = hitBox.top / mMostCommonKeyHeight
                if (row < rows) {
                    val hitBoxWidth = hitBox.width()
                    val hitBoxHeight = hitBox.height()
                    val hitBoxDiagonal = Math.hypot(hitBoxWidth.toDouble(), hitBoxHeight.toDouble()).toFloat()
                    sweetSpotCenterXs[infoIndex] += touchPositionCorrection.getX(row) * hitBoxWidth
                    sweetSpotCenterYs[infoIndex] += touchPositionCorrection.getY(row) * hitBoxHeight
                    sweetSpotRadii[infoIndex] = touchPositionCorrection.getRadius(row) * hitBoxDiagonal
                }
                infoIndex++
            }
        } else {
            sweetSpotCenterXs = null
            sweetSpotCenterYs = null
            sweetSpotRadii = null
        }

        return setProximityInfoNative(
            mKeyboardMinWidth, mKeyboardHeight, mGridWidth, mGridHeight,
            mMostCommonKeyWidth, mMostCommonKeyHeight, proximityCharsArray, keyCount,
            keyXCoordinates, keyYCoordinates, keyWidths, keyHeights, keyCharCodes,
            sweetSpotCenterXs, sweetSpotCenterYs, sweetSpotRadii
        )
    }

    val nativeProximityInfo: Long get() = mNativeProximityInfo

    @Throws(Throwable::class)
    protected fun finalize() {
        if (mNativeProximityInfo != 0L) {
            releaseProximityInfoNative(mNativeProximityInfo)
            mNativeProximityInfo = 0L
        }
    }

    private fun computeNearestNeighbors() {
        val keyCount = mSortedKeys.size
        val gridSize = mGridNeighbors.size
        val threshold = (mMostCommonKeyWidth * SEARCH_DISTANCE).toInt()
        val thresholdSquared = threshold * threshold
        val lastPixelXCoordinate = mGridWidth * mCellWidth - 1
        val lastPixelYCoordinate = mGridHeight * mCellHeight - 1

        val neighborsFlatBuffer = arrayOfNulls<Key>(gridSize * keyCount)
        val neighborCountPerCell = IntArray(gridSize)
        val halfCellWidth = mCellWidth / 2
        val halfCellHeight = mCellHeight / 2
        
        for (key in mSortedKeys) {
            if (key.isSpacer) continue

            val keyX = key.x
            val keyY = key.y
            val topPixelWithinThreshold = keyY - threshold
            val yDeltaToGrid = topPixelWithinThreshold % mCellHeight
            val yMiddleOfTopCell = topPixelWithinThreshold - yDeltaToGrid + halfCellHeight
            val yStart = maxOf(halfCellHeight, yMiddleOfTopCell + (if (yDeltaToGrid <= halfCellHeight) 0 else mCellHeight))
            val yEnd = minOf(lastPixelYCoordinate, keyY + key.height + threshold)

            val leftPixelWithinThreshold = keyX - threshold
            val xDeltaToGrid = leftPixelWithinThreshold % mCellWidth
            val xMiddleOfLeftCell = leftPixelWithinThreshold - xDeltaToGrid + halfCellWidth
            val xStart = maxOf(halfCellWidth, xMiddleOfLeftCell + (if (xDeltaToGrid <= halfCellWidth) 0 else mCellWidth))
            val xEnd = minOf(lastPixelXCoordinate, keyX + key.width + threshold)

            var baseIndexOfCurrentRow = (yStart / mCellHeight) * mGridWidth + (xStart / mCellWidth)
            var centerY = yStart
            while (centerY <= yEnd) {
                var index = baseIndexOfCurrentRow
                var centerX = xStart
                while (centerX <= xEnd) {
                    if (key.squaredDistanceToEdge(centerX, centerY) < thresholdSquared) {
                        neighborsFlatBuffer[index * keyCount + neighborCountPerCell[index]] = key
                        ++neighborCountPerCell[index]
                    }
                    ++index
                    centerX += mCellWidth
                }
                baseIndexOfCurrentRow += mGridWidth
                centerY += mCellHeight
            }
        }

        for (i in 0 until gridSize) {
            val indexStart = i * keyCount
            val indexEnd = indexStart + neighborCountPerCell[i]
            val neighbors = ArrayList<Key>(indexEnd - indexStart)
            for (index in indexStart until indexEnd) {
                neighbors.add(neighborsFlatBuffer[index]!!)
            }
            mGridNeighbors[i] = Collections.unmodifiableList(neighbors)
        }
    }

    fun fillArrayWithNearestKeyCodes(x: Int, y: Int, primaryKeyCode: Int, dest: IntArray) {
        val destLength = dest.size
        if (destLength < 1) return
        var index = 0
        if (primaryKeyCode > Constants.CODE_SPACE) {
            dest[index++] = primaryKeyCode
        }
        val nearestKeys = getNearestKeys(x, y)
        for (key in nearestKeys) {
            if (index >= destLength) break
            val code = key.code
            if (code <= Constants.CODE_SPACE) break
            dest[index++] = code
        }
        if (index < destLength) {
            dest[index] = Constants.NOT_A_CODE
        }
    }

    fun getNearestKeys(x: Int, y: Int): List<Key> {
        if (x >= 0 && x < mKeyboardMinWidth && y >= 0 && y < mKeyboardHeight) {
            val index = (y / mCellHeight) * mGridWidth + (x / mCellWidth)
            if (index < mGridSize) {
                return mGridNeighbors[index] ?: EMPTY_KEY_LIST
            }
        }
        return EMPTY_KEY_LIST
    }
}
