/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.utils

import helium314.keyboard.latin.utils.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * This class is a holder of the result of an asynchronous computation.
 *
 * @param E the type of the result.
 */
class AsyncResultHolder<E>(private val mTag: String) {

    private val mLock = Any()

    private var mResult: E? = null
    private val mLatch = CountDownLatch(1)

    /**
     * Sets the result value of this holder.
     *
     * @param result the value to set.
     */
    fun set(result: E?) {
        synchronized(mLock) {
            if (mLatch.count > 0) {
                mResult = result
                mLatch.countDown()
            }
        }
    }

    /**
     * Gets the result value held in this holder.
     * Causes the current thread to wait unless the value is set or the specified time is elapsed.
     *
     * @param defaultValue the default value.
     * @param timeOut the maximum time to wait.
     * @return if the result is set before the time limit then the result, otherwise defaultValue.
     */
    fun get(defaultValue: E?, timeOut: Long): E? {
        return try {
            if (mLatch.await(timeOut, TimeUnit.MILLISECONDS)) mResult else defaultValue
        } catch (e: InterruptedException) {
            Log.w(mTag, "get() : Interrupted after $timeOut ms")
            defaultValue
        }
    }
}
