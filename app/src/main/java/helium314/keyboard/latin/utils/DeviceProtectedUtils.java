/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.UserManager;

import java.io.File;

public final class DeviceProtectedUtils {

    static final String TAG = DeviceProtectedUtils.class.getSimpleName();
    private static SharedPreferences prefs;

    public static SharedPreferences getSharedPreferences(final Context context) {
        return getSharedPreferences(context, context.getPackageName() + "_preferences");
    }

    public static SharedPreferences getSharedPreferences(final Context context, final String name) {
        if (prefs != null && name.equals(context.getPackageName() + "_preferences"))
            return prefs;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            final SharedPreferences p = context.getSharedPreferences(name, Context.MODE_PRIVATE);
            if (name.equals(context.getPackageName() + "_preferences"))
                prefs = p;
            return p;
        }
        final Context deviceProtectedContext = getDeviceProtectedContext(context);
        final SharedPreferences p = deviceProtectedContext.getSharedPreferences(name, Context.MODE_PRIVATE);
        if (name.equals(context.getPackageName() + "_preferences"))
            prefs = p;
        if (p.getAll() == null)
            return p; // happens for compose previews
        if (p.getAll().isEmpty()) {
            final UserManager userManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
            if (userManager != null && userManager.isUserUnlocked()) {
                Log.i(TAG, "Device encrypted storage for " + name + " is empty, copying values from credential encrypted storage");
                deviceProtectedContext.moveSharedPreferencesFrom(context, name);
            }
        }
        return p;
    }

    // keep this private to avoid accidental use of device protected context anywhere in the app
    private static Context getDeviceProtectedContext(final Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return context;
        final Context ctx = context.isDeviceProtectedStorage() ? context : context.createDeviceProtectedStorageContext();
        if (ctx == null) return context; // happens for compose previews
        else return ctx;
    }

    private static SharedPreferences getDefaultSharedPreferences(Context context) {
        // from androidx.preference.PreferenceManager
        return context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
    }

    public static File getFilesDir(final Context context) {
        return getDeviceProtectedContext(context).getFilesDir();
    }

    private DeviceProtectedUtils() {
        // This utility class is not publicly instantiable.
    }
}
