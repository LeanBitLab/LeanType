/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.UserManager
import java.io.File

object DeviceProtectedUtils {
    private const val TAG = "DeviceProtectedUtils"
    private var prefs: SharedPreferences? = null

    fun getSharedPreferences(context: Context): SharedPreferences {
        return getSharedPreferences(context, "${context.packageName}_preferences")
    }

    fun getSharedPreferences(context: Context, name: String): SharedPreferences {
        val defaultName = "${context.packageName}_preferences"
        if (prefs != null && name == defaultName) {
            return prefs!!
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            val p = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            if (name == defaultName) {
                prefs = p
            }
            return p
        }
        val deviceProtectedContext = getDeviceProtectedContext(context)
        val p = deviceProtectedContext.getSharedPreferences(name, Context.MODE_PRIVATE)
        if (name == defaultName) {
            prefs = p
        }
        val all = p.all ?: return p
        if (all.isEmpty()) {
            val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
            if (userManager != null && userManager.isUserUnlocked) {
                Log.i(TAG, "Device encrypted storage for $name is empty, copying values from credential encrypted storage")
                deviceProtectedContext.moveSharedPreferencesFrom(context, name)
            }
        }
        return p
    }

    private fun getDeviceProtectedContext(context: Context): Context {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return context
        val ctx = if (context.isDeviceProtectedStorage) context else context.createDeviceProtectedStorageContext()
        return ctx ?: context
    }

    fun getFilesDir(context: Context): File {
        return getDeviceProtectedContext(context).filesDir
    }
}
