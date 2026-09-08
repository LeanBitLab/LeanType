/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.utils

import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference

open class LeakGuardHandlerWrapper<T : Any> : Handler {
    private val mOwnerInstanceRef: WeakReference<T>

    constructor(ownerInstance: T) : this(ownerInstance, Looper.myLooper() ?: Looper.getMainLooper())

    constructor(ownerInstance: T, looper: Looper) : super(looper) {
        mOwnerInstanceRef = WeakReference(ownerInstance)
    }

    fun getOwnerInstance(): T? {
        return mOwnerInstanceRef.get()
    }
}
