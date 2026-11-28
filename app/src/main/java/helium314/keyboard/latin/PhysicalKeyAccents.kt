// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import helium314.keyboard.keyboard.Key
import helium314.keyboard.latin.common.StringUtils
import java.util.Locale

/** Picks the character that replaces a letter when its physical key is held. */
object PhysicalKeyAccents {
    // ordinal indicators count as letters, but are not what someone holding "a" or "o" is after
    private val ignored = setOf('ª'.code, 'º'.code)

    /**
     * Returns the first popup key of the on-screen [typed] key that is a different letter, in the case of [typed].
     * Numbers and symbols are skipped. Null if [typed] is not a letter or there is no such popup key.
     * [keys] may come from a shifted or unshifted keyboard.
     */
    fun findAccent(keys: List<Key>, typed: Int, locale: Locale): Int? {
        if (!Character.isLetter(typed)) return null
        val base = lower(typed, locale)
        val key = keys.firstOrNull { it.code > 0 && lower(it.code, locale) == base } ?: return null
        val accent = key.popupKeys?.asSequence()
            ?.map { it.mCode }
            ?.filter { it > 0 && Character.isLetter(it) && it !in ignored }
            ?.filter { Character.getType(it) != Character.MODIFIER_LETTER.toInt() }
            ?.map { lower(it, locale) }
            ?.firstOrNull { it != base }
            ?: return null
        if (!Character.isUpperCase(typed)) return accent
        val upper = StringUtils.toTitleCaseOfKeyCode(accent, locale)
        return if (upper > 0) upper else accent
    }

    private fun lower(codePoint: Int, locale: Locale): Int {
        val lower = StringUtils.newSingleCodePointString(codePoint).lowercase(locale)
        return if (lower.codePointCount(0, lower.length) == 1) lower.codePointAt(0) else Character.toLowerCase(codePoint)
    }
}
