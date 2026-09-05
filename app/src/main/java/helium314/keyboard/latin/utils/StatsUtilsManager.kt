/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.utils

import android.content.Context
import helium314.keyboard.latin.DictionaryFacilitator
import helium314.keyboard.latin.settings.SettingsValues

@Suppress("unused")
open class StatsUtilsManager {

    companion object {
        private val sInstance = StatsUtilsManager()
        private var sTestInstance: StatsUtilsManager? = null

        /**
         * @return the singleton instance of [StatsUtilsManager].
         */
        @JvmStatic
        fun getInstance(): StatsUtilsManager {
            return sTestInstance ?: sInstance
        }

        @JvmStatic
        fun setTestInstance(testInstance: StatsUtilsManager?) {
            sTestInstance = testInstance
        }
    }

    open fun onCreate(context: Context?, dictionaryFacilitator: DictionaryFacilitator?) {
    }

    open fun onLoadSettings(context: Context?, settingsValues: SettingsValues?) {
    }

    open fun onStartInputView() {
    }

    open fun onFinishInputView() {
    }

    open fun onDestroy(context: Context?) {
    }
}
