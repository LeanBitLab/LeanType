// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only

package helium314.keyboard.latin.common

class NativeSuggestOptions {
    companion object {
        // Need to update suggest_options.h when you add, remove or reorder options.
        private const val IS_GESTURE = 0
        private const val USE_FULL_EDIT_DISTANCE = 1
        private const val BLOCK_OFFENSIVE_WORDS = 2
        private const val SPACE_AWARE_GESTURE_ENABLED = 3
        private const val WEIGHT_FOR_LOCALE_IN_THOUSANDS = 4
        private const val OPTIONS_SIZE = 5
    }

    private val mOptions = IntArray(OPTIONS_SIZE)

    fun setIsGesture(value: Boolean) {
        setBooleanOption(IS_GESTURE, value)
    }

    fun setIsSpaceAwareGesture(value: Boolean) {
        setBooleanOption(SPACE_AWARE_GESTURE_ENABLED, value)
    }

    fun setUseFullEditDistance(value: Boolean) {
        setBooleanOption(USE_FULL_EDIT_DISTANCE, value)
    }

    fun setBlockOffensiveWords(value: Boolean) {
        setBooleanOption(BLOCK_OFFENSIVE_WORDS, value)
    }

    fun setWeightForLocale(value: Float) {
        // We're passing this option as a fixed point value, in thousands. This is decoded in
        // native code by SuggestOptions#weightForLocale().
        setIntegerOption(WEIGHT_FOR_LOCALE_IN_THOUSANDS, (value * 1000).toInt())
    }

    val options: IntArray
        get() = mOptions

    private fun setBooleanOption(key: Int, value: Boolean) {
        mOptions[key] = if (value) 1 else 0
    }

    private fun setIntegerOption(key: Int, value: Int) {
        mOptions[key] = value
    }
}
