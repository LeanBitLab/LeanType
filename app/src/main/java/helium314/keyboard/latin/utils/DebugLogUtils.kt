/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.utils

/**
 * A class for logging and debugging utility methods.
 */
object DebugLogUtils {
    /**
     * Get the string representation of the current stack trace, for debugging purposes.
     * @return a readable, carriage-return-separated string for the current stack trace.
     */
    fun getStackTrace(): String {
        return getStackTrace(Int.MAX_VALUE - 1)
    }

    /**
     * Get the string representation of the current stack trace, for debugging purposes.
     * @param limit the maximum number of stack frames to be returned.
     * @return a readable, carriage-return-separated string for the current stack trace.
     */
    fun getStackTrace(limit: Int): String {
        val sb = StringBuilder()
        try {
            throw RuntimeException()
        } catch (e: RuntimeException) {
            val frames = e.stackTrace
            // Start at 1 because the first frame is here and we don't care about it
            var j = 1
            while (j < frames.size && j < limit + 1) {
                sb.append(frames[j].toString()).append("\n")
                ++j
            }
        }
        return sb.toString()
    }
}
