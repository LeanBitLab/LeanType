/*
 * Copyright (C) 2010 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal

import helium314.keyboard.latin.utils.Log

class PointerTrackerQueue {
    interface Element {
        fun isModifier(): Boolean
        fun isInDraggingFinger(): Boolean
        fun onPhantomUpEvent(eventTime: Long)
        fun cancelTrackingForAction()
    }

    private val mExpandableArrayOfActivePointers = ArrayList<Element>(INITIAL_CAPACITY)
    private var mArraySize = 0

    fun size(): Int = synchronized(mExpandableArrayOfActivePointers) { mArraySize }

    fun add(pointer: Element) {
        synchronized(mExpandableArrayOfActivePointers) {
            if (DEBUG) Log.d(TAG, "add: $pointer $this")
            val expandableArray = mExpandableArrayOfActivePointers
            val arraySize = mArraySize
            if (arraySize < expandableArray.size) {
                expandableArray[arraySize] = pointer
            } else {
                expandableArray.add(pointer)
            }
            mArraySize = arraySize + 1
        }
    }

    fun remove(pointer: Element) {
        synchronized(mExpandableArrayOfActivePointers) {
            if (DEBUG) Log.d(TAG, "remove: $pointer $this")
            val expandableArray = mExpandableArrayOfActivePointers
            val arraySize = mArraySize
            var newIndex = 0
            for (index in 0 until arraySize) {
                val element = expandableArray[index]
                if (element === pointer) {
                    if (newIndex != index) Log.w(TAG, "Found duplicated element in remove: $pointer")
                    continue
                }
                if (newIndex != index) expandableArray[newIndex] = element
                newIndex++
            }
            mArraySize = newIndex
        }
    }

    fun getOldestElement(): Element? = synchronized(mExpandableArrayOfActivePointers) {
        if (mArraySize == 0) null else mExpandableArrayOfActivePointers[0]
    }

    fun releaseAllPointersOlderThan(pointer: Element, eventTime: Long) {
        synchronized(mExpandableArrayOfActivePointers) {
            if (DEBUG) Log.d(TAG, "releaseAllPointerOlderThan: $pointer $this")
            val expandableArray = mExpandableArrayOfActivePointers
            val arraySize = mArraySize
            var newIndex = 0
            var index = 0
            while (index < arraySize) {
                val element = expandableArray[index]
                if (element === pointer) break
                if (!element.isModifier()) {
                    element.onPhantomUpEvent(eventTime)
                    index++
                    continue
                }
                if (newIndex != index) expandableArray[newIndex] = element
                newIndex++
                index++
            }
            var count = 0
            while (index < arraySize) {
                val element = expandableArray[index]
                if (element === pointer) {
                    count++
                    if (count > 1) Log.w(TAG, "Found duplicated element in releaseAllPointersOlderThan: $pointer")
                }
                if (newIndex != index) expandableArray[newIndex] = expandableArray[index]
                newIndex++
                index++
            }
            mArraySize = newIndex
        }
    }

    fun releaseAllPointers(eventTime: Long) {
        releaseAllPointersExcept(null, eventTime)
    }

    fun releaseAllPointersExcept(pointer: Element?, eventTime: Long) {
        synchronized(mExpandableArrayOfActivePointers) {
            if (DEBUG) {
                if (pointer == null) Log.d(TAG, "releaseAllPointers: $this")
                else Log.d(TAG, "releaseAllPointerExcept: $pointer $this")
            }
            val expandableArray = mExpandableArrayOfActivePointers
            val arraySize = mArraySize
            var newIndex = 0
            var count = 0
            for (index in 0 until arraySize) {
                val element = expandableArray[index]
                if (element === pointer) {
                    count++
                    if (count > 1) Log.w(TAG, "Found duplicated element in releaseAllPointersExcept: $pointer")
                } else {
                    element.onPhantomUpEvent(eventTime)
                    continue
                }
                if (newIndex != index) expandableArray[newIndex] = element
                newIndex++
            }
            mArraySize = newIndex
        }
    }

    fun hasModifierKeyOlderThan(pointer: Element): Boolean {
        synchronized(mExpandableArrayOfActivePointers) {
            val arraySize = mArraySize
            for (index in 0 until arraySize) {
                val element = mExpandableArrayOfActivePointers[index]
                if (element === pointer) return false
                if (element.isModifier()) return true
            }
            return false
        }
    }

    fun isAnyInDraggingFinger(): Boolean {
        synchronized(mExpandableArrayOfActivePointers) {
            val arraySize = mArraySize
            for (index in 0 until arraySize) {
                if (mExpandableArrayOfActivePointers[index].isInDraggingFinger()) return true
            }
            return false
        }
    }

    fun cancelAllPointerTrackers() {
        synchronized(mExpandableArrayOfActivePointers) {
            if (DEBUG) Log.d(TAG, "cancelAllPointerTracker: $this")
            val arraySize = mArraySize
            for (index in 0 until arraySize) {
                mExpandableArrayOfActivePointers[index].cancelTrackingForAction()
            }
        }
    }

    override fun toString(): String {
        synchronized(mExpandableArrayOfActivePointers) {
            val sb = StringBuilder()
            val arraySize = mArraySize
            for (index in 0 until arraySize) {
                if (sb.isNotEmpty()) sb.append(" ")
                sb.append(mExpandableArrayOfActivePointers[index].toString())
            }
            return "[$sb]"
        }
    }

    companion object {
        private const val TAG = "PointerTrackerQueue"
        private const val DEBUG = false
        private const val INITIAL_CAPACITY = 10
    }
}
