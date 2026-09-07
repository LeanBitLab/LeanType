/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.ContactsContract.Contacts
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.utils.Log
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.pow

/**
 * Manages all interactions with Contacts DB.
 *
 * The manager provides an API for listening to meaningful updates by keeping a
 * measure of the current state of the content provider.
 */
class ContactsManager(private val mContext: Context) {

    protected open class RankedContact(cursor: Cursor) {
        @JvmField
        val mName: String = cursor.getString(ContactsDictionaryConstants.NAME_INDEX)
        @JvmField
        val mTimesContacted: Int = cursor.getInt(ContactsDictionaryConstants.TIMES_CONTACTED_INDEX)
        @JvmField
        val mLastContactedTime: Long = cursor.getLong(ContactsDictionaryConstants.LAST_TIME_CONTACTED_INDEX)
        @JvmField
        val mInVisibleGroup: Boolean = cursor.getInt(ContactsDictionaryConstants.IN_VISIBLE_GROUP_INDEX) == 1

        var affinity: Float = 0.0f
            private set

        /**
         * Calculates the affinity with the contact based on:
         * - How many times it has been contacted
         * - How long since the last contact.
         * - Whether the contact is in the visible group (i.e., Contacts list).
         *
         * Note: This affinity is limited by the fact that some apps currently do not update the
         * LAST_TIME_CONTACTED or TIMES_CONTACTED counters. As a result, a frequently messaged
         * contact may still have 0 affinity.
         */
        fun computeAffinity(maxTimesContacted: Int, currentTime: Long) {
            val timesWeight = (mTimesContacted.toFloat() + 1f) / (maxTimesContacted + 1f)
            val timeSinceLastContact = (currentTime - mLastContactedTime).coerceIn(
                0L,
                TimeUnit.MILLISECONDS.convert(180, TimeUnit.DAYS)
            )
            val lastTimeWeight = 0.5.pow(
                timeSinceLastContact.toDouble() / TimeUnit.MILLISECONDS.convert(10, TimeUnit.DAYS)
            ).toFloat()
            val visibleWeight = if (mInVisibleGroup) 1.0f else 0.0f
            affinity = (timesWeight + lastTimeWeight + visibleWeight) / 3f
        }
    }

    private class AffinityComparator : Comparator<RankedContact> {
        override fun compare(contact1: RankedContact, contact2: RankedContact): Int {
            return contact2.affinity.compareTo(contact1.affinity)
        }
    }

    /**
     * Interface to implement for classes interested in getting notified for updates
     * to Contacts content provider.
     */
    fun interface ContactsChangedListener {
        fun onContactsChange()
    }

    /**
     * The number of contacts observed in the most recent instance of
     * contacts content provider.
     */
    private val mContactCountAtLastRebuild = AtomicInteger(0)

    /**
     * The hash code of list of valid contacts names in the most recent dictionary
     * rebuild.
     */
    private val mHashCodeAtLastRebuild = AtomicInteger(0)

    private val mObserver: ContactsContentObserver = ContactsContentObserver(this, mContext)

    fun registerForUpdates(listener: ContactsChangedListener) {
        mObserver.registerObserver(listener)
    }

    fun getContactCountAtLastRebuild(): Int {
        return mContactCountAtLastRebuild.get()
    }

    fun getHashCodeAtLastRebuild(): Int {
        return mHashCodeAtLastRebuild.get()
    }

    /**
     * Returns all the valid names in the Contacts DB. Callers should also
     * call [updateLocalState] after they are done with result
     * so that the manager can cache local state for determining updates.
     *
     * These names are sorted by their affinity to the user, with favorite
     * contacts appearing first.
     */
    fun getValidNames(uri: Uri): ArrayList<String> {
        // Check all contacts since it's not possible to find out which names have changed.
        // This is needed because it's possible to receive extraneous onChange events even when no
        // name has changed.
        val cursor = mContext.contentResolver.query(
            uri,
            ContactsDictionaryConstants.PROJECTION, null, null, null
        )
        val contacts = ArrayList<RankedContact>()
        var maxTimesContacted = 0
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    while (!cursor.isAfterLast) {
                        val name = cursor.getString(ContactsDictionaryConstants.NAME_INDEX)
                        if (isValidName(name)) {
                            val timesContacted = cursor.getInt(ContactsDictionaryConstants.TIMES_CONTACTED_INDEX)
                            if (timesContacted > maxTimesContacted) {
                                maxTimesContacted = timesContacted
                            }
                            contacts.add(RankedContact(cursor))
                        }
                        cursor.moveToNext()
                    }
                }
            } finally {
                cursor.close()
            }
        }
        val currentTime = System.currentTimeMillis()
        for (contact in contacts) {
            contact.computeAffinity(maxTimesContacted, currentTime)
        }
        Collections.sort(contacts, AffinityComparator())
        val names = LinkedHashSet<String>()
        var i = 0
        while (i < contacts.size && names.size < MAX_CONTACT_NAMES) {
            names.add(contacts[i].mName)
            i++
        }
        return ArrayList(names)
    }

    /**
     * Returns the number of contacts in contacts content provider.
     */
    fun getContactCount(): Int {
        try {
            mContext.contentResolver.query(
                Contacts.CONTENT_URI,
                ContactsDictionaryConstants.PROJECTION_ID_ONLY,
                null, null, null
            )?.use { cursor ->
                return cursor.count
            }
        } catch (e: SQLiteException) {
            Log.e(TAG, "SQLiteException in the remote Contacts process.", e)
        }
        return 0
    }

    /**
     * Updates the local state of the manager. This should be called when the callers
     * are done with all the updates of the content provider successfully.
     */
    fun updateLocalState(names: ArrayList<String>) {
        mContactCountAtLastRebuild.set(getContactCount())
        mHashCodeAtLastRebuild.set(names.hashCode())
    }

    /**
     * Performs any necessary cleanup.
     */
    fun close() {
        mObserver.unregister()
    }

    companion object {
        private const val TAG = "ContactsManager"

        /**
         * Use at most this many of the highest affinity contacts.
         */
        const val MAX_CONTACT_NAMES = 200

        private fun isValidName(name: String?): Boolean {
            if (name.isNullOrEmpty() || name.indexOf(Constants.CODE_COMMERCIAL_AT.toChar()) != -1) {
                return false
            }
            val hasSpace = name.indexOf(Constants.CODE_SPACE.toChar()) != -1
            if (!hasSpace) {
                // Only allow an isolated word if it does not contain a hyphen.
                // This helps to filter out mailing lists.
                return name.indexOf(Constants.CODE_DASH.toChar()) == -1
            }
            return true
        }
    }
}
