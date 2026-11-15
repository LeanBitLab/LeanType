/*
 * Copyright (C) 2015 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.permissions

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Utility class for permissions.
 */
object PermissionsUtil {
    /**
     * Queries if al the permissions are granted for the given permission strings.
     */
    fun checkAllPermissionsGranted(context: Context?, vararg permissions: String): Boolean {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
            // For all pre-M devices, we should have all the permissions granted on install.
            return true
        }
        if (context == null) return false

        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }
}
