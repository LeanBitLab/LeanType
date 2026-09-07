/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin

/**
 * Factory for instantiating DictionaryFacilitator objects.
 */
object DictionaryFacilitatorProvider {
    @JvmStatic
    fun getDictionaryFacilitator(isNeededForSpellChecking: Boolean): DictionaryFacilitator {
        return DictionaryFacilitatorImpl()
    }
}
