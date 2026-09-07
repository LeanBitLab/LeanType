/*
 * Copyright (C) 2015 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin

import android.provider.BaseColumns
import android.provider.ContactsContract.Contacts

/**
 * Constants related to Contacts Content Provider.
 */
object ContactsDictionaryConstants {
    @JvmField
    val PROJECTION: Array<String> = arrayOf(
        BaseColumns._ID,
        Contacts.DISPLAY_NAME,
        Contacts.TIMES_CONTACTED,
        Contacts.LAST_TIME_CONTACTED,
        Contacts.IN_VISIBLE_GROUP
    )

    @JvmField
    val PROJECTION_ID_ONLY: Array<String> = arrayOf(BaseColumns._ID)

    /**
     * Frequency for contacts information into the dictionary
     */
    const val FREQUENCY_FOR_CONTACTS: Int = 100 // much increased from original frequency because contacts were barely suggested
    const val FREQUENCY_FOR_CONTACTS_BIGRAM: Int = 200 // todo: seems broken, how to actually get bigrams?

    /**
     * Do not attempt to query contacts if there are more than this many entries.
     */
    const val MAX_CONTACTS_PROVIDER_QUERY_LIMIT: Int = 10000

    /**
     * Index of the column for 'name' in content providers:
     * Contacts & ContactsContract.Profile.
     */
    const val NAME_INDEX: Int = 1
    const val TIMES_CONTACTED_INDEX: Int = 2
    const val LAST_TIME_CONTACTED_INDEX: Int = 3
    const val IN_VISIBLE_GROUP_INDEX: Int = 4
}
