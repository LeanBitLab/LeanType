/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.os.SystemClock
import android.provider.ContactsContract.Contacts
import helium314.keyboard.latin.ContactsManager.ContactsChangedListener
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.permissions.PermissionsUtil
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ExecutorUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A content observer that listens to updates to content provider [Contacts.CONTENT_URI].
 */
class ContactsContentObserver(
    private val mManager: ContactsManager,
    private val mContext: Context
) : Runnable {

    private val mRunning = AtomicBoolean(false)
    private var mContentObserver: ContentObserver? = null
    private var mContactsChangedListener: ContactsChangedListener? = null

    fun registerObserver(listener: ContactsChangedListener) {
        val useContacts = mContext.prefs().getBoolean(
            Settings.PREF_USE_CONTACTS,
            Defaults.PREF_USE_CONTACTS
        )
        if (!useContacts) {
            Log.i(TAG, "Contacts dictionary disabled in settings. Not registering.")
            return
        }
        if (!PermissionsUtil.checkAllPermissionsGranted(mContext, Manifest.permission.READ_CONTACTS)) {
            Log.i(TAG, "No permission to read contacts. Not registering the observer.")
            return
        }

        if (DebugFlags.DEBUG_ENABLED) {
            Log.d(TAG, "registerObserver()")
        }
        mContactsChangedListener = listener
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD)
                    .execute(this@ContactsContentObserver)
            }
        }
        mContentObserver = observer
        val contentResolver: ContentResolver = mContext.contentResolver
        contentResolver.registerContentObserver(Contacts.CONTENT_URI, true, observer)
    }

    override fun run() {
        if (!PermissionsUtil.checkAllPermissionsGranted(mContext, Manifest.permission.READ_CONTACTS)) {
            Log.i(TAG, "No permission to read contacts. Not updating the contacts.")
            unregister()
            return
        }

        if (!mRunning.compareAndSet(false, true)) {
            if (DebugFlags.DEBUG_ENABLED) {
                Log.d(TAG, "run() : Already running. Don't waste time checking again.")
            }
            return
        }
        if (mContext is LatinIME && !mContext.isInputViewShown) {
            mRunning.set(false)
            return
        }
        if (haveContentsChanged()) {
            if (DebugFlags.DEBUG_ENABLED) {
                Log.d(TAG, "run() : Contacts have changed. Notifying listeners.")
            }
            mContactsChangedListener?.onContactsChange()
        }
        mRunning.set(false)
    }

    fun haveContentsChanged(): Boolean {
        if (!PermissionsUtil.checkAllPermissionsGranted(mContext, Manifest.permission.READ_CONTACTS)) {
            Log.i(TAG, "No permission to read contacts. Marking contacts as not changed.")
            return false
        }

        val startTime = SystemClock.uptimeMillis()
        val contactCount = mManager.getContactCount()
        if (contactCount > ContactsDictionaryConstants.MAX_CONTACTS_PROVIDER_QUERY_LIMIT) {
            // If there are too many contacts then return false. In this rare case it is impossible
            // to include all of them anyways and the cost of rebuilding the dictionary is too high.
            return false
        }
        if (contactCount != mManager.getContactCountAtLastRebuild()) {
            if (DebugFlags.DEBUG_ENABLED) {
                Log.d(
                    TAG, "haveContentsChanged() : Count changed from "
                            + mManager.getContactCountAtLastRebuild() + " to " + contactCount
                )
            }
            return true
        }
        val names = mManager.getValidNames(Contacts.CONTENT_URI)
        if (names.hashCode() != mManager.getHashCodeAtLastRebuild()) {
            return true
        }
        if (DebugFlags.DEBUG_ENABLED) {
            Log.d(
                TAG, "haveContentsChanged() : No change detected in "
                        + (SystemClock.uptimeMillis() - startTime) + " ms)"
            )
        }
        return false
    }

    fun unregister() {
        mContentObserver?.let { observer ->
            try {
                mContext.contentResolver.unregisterContentObserver(observer)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister contacts content observer", e)
            }
            mContentObserver = null
        }
    }

    companion object {
        private const val TAG = "ContactsContentObserver"
    }
}
