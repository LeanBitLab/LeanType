/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.spellcheck

import android.service.textservice.SpellCheckerService

abstract class AndroidSpellCheckerSessionFactory {
    companion object {
        @JvmStatic
        fun newInstance(service: AndroidSpellCheckerService): SpellCheckerService.Session {
            return AndroidSpellCheckerSession(service)
        }
    }
}
