/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.utils

import android.annotation.SuppressLint
import android.os.Build
import android.text.TextUtils
import helium314.keyboard.latin.App
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.settings.Settings
import java.io.File

@SuppressLint("PrivateApi")
object JniUtils {
    private const val TAG = "JniUtils"
    const val JNI_LIB_NAME = "jni_latinime"
    const val JNI_LIB_NAME_GOOGLE = "jni_latinimegoogle"
    const val JNI_LIB_IMPORT_FILE_NAME = "libjni_latinime.so"
    private const val CHECKSUM_ARM64 = "b1049983e6ac5cfc6d1c66e38959751044fad213dff0637a6cf1d2a2703e754f"
    private const val CHECKSUM_ARM32 = "442a2a8bfcb25489564bc9433a916fa4dc0dba9000fe6f6f03f5939b985091e6"
    private const val CHECKSUM_X86_64 = "c882e12e6d48dd946e0b644c66868a720bd11ac3fecf152000e21a3d5abd59c9"
    private const val CHECKSUM_X86 = "bd946d126c957b5a6dea3bafa07fa36a27950b30e2b684dffc60746d0a1c7ad8"

    @JvmStatic
    fun expectedDefaultChecksum(): String {
        val abi = Build.SUPPORTED_ABIS[0]
        return when (abi) {
            "arm64-v8a" -> CHECKSUM_ARM64
            "armeabi-v7a" -> CHECKSUM_ARM32
            "x86_64" -> CHECKSUM_X86_64
            "x86" -> CHECKSUM_X86
            else -> "-"
        }
    }

    @JvmField
    var sHaveGestureLib: Boolean = false

    @JvmField
    var sHaveNativeGestureLib: Boolean = false

    init {
        @SuppressLint("SdCardPath")
        var filesDir = "/data/data/${BuildConfig.APPLICATION_ID}/files"
        val app = App.getApp()
        if (app != null && app.filesDir != null) {
            filesDir = app.filesDir.absolutePath
        }

        var userSuppliedLibrary: File? = try {
            val file = File(filesDir + File.separator + JNI_LIB_IMPORT_FILE_NAME)
            if (file.isFile) file else null
        } catch (e: Exception) {
            null
        }

        if (BuildConfig.BUILD_TYPE != "nouserlib" && userSuppliedLibrary != null) {
            var wantedChecksum = expectedDefaultChecksum()
            try {
                if (app != null) {
                    wantedChecksum = app.protectedPrefs().getString(Settings.PREF_LIBRARY_CHECKSUM, expectedDefaultChecksum()) ?: expectedDefaultChecksum()
                }
                val checksum = ChecksumCalculator.checksum(userSuppliedLibrary)
                if (TextUtils.equals(wantedChecksum, checksum)) {
                    System.load(userSuppliedLibrary.absolutePath)
                    sHaveGestureLib = true
                    sHaveNativeGestureLib = true
                } else {
                    userSuppliedLibrary.delete()
                    sHaveGestureLib = false
                }
            } catch (t: Throwable) {
                if (t !is IllegalStateException || "SharedPreferences in credential encrypted storage are not available until after user is unlocked" != t.message) {
                    Log.w(TAG, "Could not load user-supplied library", t)
                }
            }
        }

        if (!sHaveGestureLib) {
            try {
                System.loadLibrary(JNI_LIB_NAME_GOOGLE)
                sHaveGestureLib = true
                sHaveNativeGestureLib = true
            } catch (ul: UnsatisfiedLinkError) {
                Log.w(TAG, "Could not load system glide typing library $JNI_LIB_NAME_GOOGLE: ${ul.message}")
            }
        }

        if (!sHaveGestureLib) {
            try {
                System.loadLibrary(JNI_LIB_NAME)
                sHaveGestureLib = true
                sHaveNativeGestureLib = false
            } catch (ul: UnsatisfiedLinkError) {
                Log.w(TAG, "Could not load native library $JNI_LIB_NAME", ul)
            }
        }
    }

    @JvmStatic
    fun loadNativeLibrary() {
        // Ensures the static initializer is called
    }
}
