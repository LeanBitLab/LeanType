/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.common

import java.util.ArrayList

/**
 * Utility methods for working with collections.
 */
object CollectionUtils {
    /**
     * Converts a sub-range of the given array to an ArrayList of the appropriate type.
     * @param array Array to be converted.
     * @param start First index inclusive to be converted.
     * @param end Last index exclusive to be converted.
     * @throws IllegalArgumentException if start or end are out of range or start > end.
     */
    @JvmStatic
    fun <E> arrayAsList(array: Array<E>, start: Int, end: Int): ArrayList<E> {
        if (start < 0 || start > end || end > array.size) {
            throw IllegalArgumentException(
                "Invalid start: $start end: $end with array.length: ${array.size}"
            )
        }

        val list = ArrayList<E>(end - start)
        for (i in start until end) {
            list.add(array[i])
        }
        return list
    }
}
