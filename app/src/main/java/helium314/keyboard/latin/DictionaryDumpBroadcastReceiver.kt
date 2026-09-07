/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import helium314.keyboard.latin.utils.Log

class DictionaryDumpBroadcastReceiver(
    private val mLatinIme: LatinIME
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == DICTIONARY_DUMP_INTENT_ACTION) {
            val dictName = intent.getStringExtra(DICTIONARY_NAME_KEY)
            if (dictName == null) {
                Log.e(TAG, "Received dictionary dump intent action but the dictionary name is not set.")
                return
            }
            mLatinIme.dumpDictionaryForDebug(dictName)
        }
    }

    companion object {
        private val TAG = DictionaryDumpBroadcastReceiver::class.simpleName
        private const val DOMAIN = "helium314.keyboard.latin"
        const val DICTIONARY_DUMP_INTENT_ACTION = "$DOMAIN.DICT_DUMP"
        const val DICTIONARY_NAME_KEY = "dictName"
    }
}
