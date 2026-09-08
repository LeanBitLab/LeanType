/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal

import helium314.keyboard.keyboard.Key
import java.util.HashMap

abstract class UniqueKeysCache {
    abstract fun setEnabled(enabled: Boolean)
    abstract fun clear()
    abstract fun getUniqueKey(key: Key): Key

    companion object {
        val NO_CACHE: UniqueKeysCache = object : UniqueKeysCache() {
            override fun setEnabled(enabled: Boolean) {}
            override fun clear() {}
            override fun getUniqueKey(key: Key): Key = key
        }

        fun newInstance(): UniqueKeysCache = UniqueKeysCacheImpl()
    }

    private class UniqueKeysCacheImpl : UniqueKeysCache() {
        private val mCache = HashMap<Key, Key>()
        private var mEnabled = false

        override fun setEnabled(enabled: Boolean) {
            mEnabled = enabled
        }

        override fun clear() {
            mCache.clear()
        }

        override fun getUniqueKey(key: Key): Key {
            if (!mEnabled) return key
            val existingKey = mCache[key]
            if (existingKey != null) return existingKey
            mCache[key] = key
            return key
        }
    }
}
