package helium314.keyboard.latin.utils

import java.util.Locale
import helium314.keyboard.latin.common.StringUtils

enum class RecapitalizeMode {
    ORIGINAL_MIXED_CASE {
        override fun apply(text: String, sortedSeparators: IntArray, locale: Locale): String {
            return text
        }
    },
    ALL_LOWER {
        override fun apply(text: String, sortedSeparators: IntArray, locale: Locale): String {
            return text.lowercase(locale)
        }
    },
    FIRST_WORD_UPPER {
        override fun apply(text: String, sortedSeparators: IntArray, locale: Locale): String {
            return StringUtils.capitalizeEachWord(text, sortedSeparators, locale)
        }
    },
    ALL_UPPER {
        override fun apply(text: String, sortedSeparators: IntArray, locale: Locale): String {
            return text.uppercase(locale)
        }
    };

    abstract fun apply(text: String, sortedSeparators: IntArray, locale: Locale): String

    companion object {
        private val sCarousel = entries.toTypedArray()

        fun of(string: String, sortedSeparators: IntArray): RecapitalizeMode {
            return when {
                StringUtils.isIdenticalAfterUpcase(string) -> ALL_UPPER
                StringUtils.isIdenticalAfterDowncase(string) -> ALL_LOWER
                StringUtils.isIdenticalAfterCapitalizeEachWord(string, sortedSeparators) -> FIRST_WORD_UPPER
                else -> ORIGINAL_MIXED_CASE
            }
        }

        fun count(): Int {
            return sCarousel.size
        }
    }

    fun rotate(skipOriginalMixedCaseMode: Boolean): RecapitalizeMode {
        var position = ordinal + 1
        if (position == sCarousel.size) {
            position = if (skipOriginalMixedCaseMode) 1 else 0
        }
        return sCarousel[position]
    }
}
