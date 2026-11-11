// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.compat

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.UserManager

private const val LOCK_STATE_TTL_MS = 2000L

@Volatile
private var cachedDeviceLocked: Boolean? = null
@Volatile
private var lastLockCheckTime = 0L

fun refreshDeviceLocked(context: Context): Boolean {
    val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    val locked = if (keyguardManager == null) {
        false
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
        keyguardManager.isDeviceLocked
    } else {
        keyguardManager.isKeyguardLocked
    }
    cachedDeviceLocked = locked
    lastLockCheckTime = SystemClock.elapsedRealtime()
    return locked
}

fun isDeviceLocked(context: Context): Boolean {
    val cached = cachedDeviceLocked
    val now = SystemClock.elapsedRealtime()
    if (cached != null && (now - lastLockCheckTime) < LOCK_STATE_TTL_MS) {
        return cached
    }
    return refreshDeviceLocked(context)
}

fun isUserLocked(context: Context): Boolean {
    val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
        userManager?.isUserUnlocked == false
    else
        false
}
