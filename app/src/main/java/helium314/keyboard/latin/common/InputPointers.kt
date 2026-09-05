/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.common

// TODO: This class is not thread-safe.
class InputPointers(defaultCapacity: Int) {
    private val mDefaultCapacity: Int = defaultCapacity
    private val mXCoordinates: ResizableIntArray = ResizableIntArray(defaultCapacity)
    private val mYCoordinates: ResizableIntArray = ResizableIntArray(defaultCapacity)
    private val mPointerIds: ResizableIntArray = ResizableIntArray(defaultCapacity)
    private val mTimes: ResizableIntArray = ResizableIntArray(defaultCapacity)

    companion object {
        private const val DEBUG_TIME = false
    }

    private fun fillWithLastTimeUntil(index: Int) {
        val fromIndex = mTimes.length
        // Fill the gap with the latest time.
        // See [getTimes] and [isValidTimeStamps].
        if (fromIndex <= 0) {
            return
        }
        val fillLength = index - fromIndex + 1
        if (fillLength <= 0) {
            return
        }
        val lastTime = mTimes.get(fromIndex - 1)
        mTimes.fill(lastTime, fromIndex, fillLength)
    }

    fun addPointerAt(
        index: Int, x: Int, y: Int, pointerId: Int,
        time: Int
    ) {
        mXCoordinates.addAt(index, x)
        mYCoordinates.addAt(index, y)
        mPointerIds.addAt(index, pointerId)
        if (DEBUG_TIME) {
            fillWithLastTimeUntil(index)
        }
        mTimes.addAt(index, time)
    }

    fun addPointer(x: Int, y: Int, pointerId: Int, time: Int) {
        mXCoordinates.add(x)
        mYCoordinates.add(y)
        mPointerIds.add(pointerId)
        mTimes.add(time)
    }

    fun set(ip: InputPointers) {
        mXCoordinates.set(ip.mXCoordinates)
        mYCoordinates.set(ip.mYCoordinates)
        mPointerIds.set(ip.mPointerIds)
        mTimes.set(ip.mTimes)
    }

    fun copy(ip: InputPointers) {
        mXCoordinates.copy(ip.mXCoordinates)
        mYCoordinates.copy(ip.mYCoordinates)
        mPointerIds.copy(ip.mPointerIds)
        mTimes.copy(ip.mTimes)
    }

    /**
     * Append the times, x-coordinates and y-coordinates in the specified [ResizableIntArray]
     * to the end of this.
     * @param pointerId the pointer id of the source.
     * @param times the source [ResizableIntArray] to read the event times from.
     * @param xCoordinates the source [ResizableIntArray] to read the x-coordinates from.
     * @param yCoordinates the source [ResizableIntArray] to read the y-coordinates from.
     * @param startPos the starting index of the data in `times` and etc.
     * @param length the number of data to be appended.
     */
    fun append(
        pointerId: Int, times: ResizableIntArray,
        xCoordinates: ResizableIntArray,
        yCoordinates: ResizableIntArray, startPos: Int, length: Int
    ) {
        if (length == 0) {
            return
        }
        mXCoordinates.append(xCoordinates, startPos, length)
        mYCoordinates.append(yCoordinates, startPos, length)
        mPointerIds.fill(pointerId, mPointerIds.length, length)
        mTimes.append(times, startPos, length)
    }

    /**
     * Shift to the left by elementCount, discarding elementCount pointers at the start.
     * @param elementCount how many elements to shift.
     */
    fun shift(elementCount: Int) {
        mXCoordinates.shift(elementCount)
        mYCoordinates.shift(elementCount)
        mPointerIds.shift(elementCount)
        mTimes.shift(elementCount)
    }

    fun reset() {
        val defaultCapacity = mDefaultCapacity
        mXCoordinates.reset(defaultCapacity)
        mYCoordinates.reset(defaultCapacity)
        mPointerIds.reset(defaultCapacity)
        mTimes.reset(defaultCapacity)
    }

    val pointerSize: Int
        get() = mXCoordinates.length

    val xCoordinates: IntArray
        get() = mXCoordinates.primitiveArray

    val yCoordinates: IntArray
        get() = mYCoordinates.primitiveArray

    val pointerIds: IntArray
        get() = mPointerIds.primitiveArray

    /**
     * Gets the time each point was registered, in milliseconds, relative to the first event in the
     * sequence.
     * @return The time each point was registered, in milliseconds, relative to the first event in
     * the sequence.
     */
    val times: IntArray
        get() = mTimes.primitiveArray

    override fun toString(): String {
        return "size=$pointerSize id=$mPointerIds time=$mTimes x=$mXCoordinates y=$mYCoordinates"
    }
}
