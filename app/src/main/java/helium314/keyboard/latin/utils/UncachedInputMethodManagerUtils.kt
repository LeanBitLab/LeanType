/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.utils

import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager

/*
 * A utility class for {@link InputMethodManager}. Unlike {@link RichInputMethodManager}, this
 * class provides synchronous, non-cached access to {@link InputMethodManager}. The setup activity
 * is a good example to use this class because {@link InputMethodManagerService} may not be aware of
 * this IME immediately after this IME is installed.
 */
object UncachedInputMethodManagerUtils {
    /**
     * Check if the IME specified by the context is enabled.
     * CAVEAT: This may cause a round trip IPC.
     *
     * @param context package context of the IME to be checked.
     * @param imm the {@link InputMethodManager}.
     * @return true if this IME is enabled.
     */
    fun isThisImeEnabled(context: Context, imm: InputMethodManager): Boolean {
        val packageName = context.packageName
        for (imi in imm.enabledInputMethodList.orEmpty()) {
            if (packageName == imi.packageName) {
                return true
            }
        }
        return false
    }

    /**
     * Check if the IME specified by the context is the current IME.
     * CAVEAT: This may cause a round trip IPC.
     *
     * @param context package context of the IME to be checked.
     * @param imm the {@link InputMethodManager}.
     * @return true if this IME is the current IME.
     */
    fun isThisImeCurrent(context: Context, imm: InputMethodManager): Boolean {
        val imi = getInputMethodInfoOf(context.packageName, imm) ?: return false
        val currentImeId = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
        )
        if (TextUtils.isEmpty(currentImeId)) {
            val enabled = imm.enabledInputMethodList
            return enabled.size == 1 && enabled[0].packageName == context.packageName
        }
        return imi.id == currentImeId
    }

    /**
     * Get {@link InputMethodInfo} of the IME specified by the package name.
     * CAVEAT: This may cause a round trip IPC.
     *
     * @param packageName package name of the IME.
     * @param imm the {@link InputMethodManager}.
     * @return the {@link InputMethodInfo} of the IME specified by the <code>packageName</code>,
     * or null if not found.
     */
    fun getInputMethodInfoOf(packageName: String, imm: InputMethodManager): InputMethodInfo? {
        for (imi in imm.inputMethodList.orEmpty()) {
            if (packageName == imi.packageName) {
                return imi
            }
        }
        return null
    }
}
