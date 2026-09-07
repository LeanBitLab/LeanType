/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.makedict

import helium314.keyboard.latin.define.DecoderSpecificConstants
import java.util.Date
import java.util.HashMap

/**
 * Dictionary File Format Specification.
 */
object FormatSpec {

    const val MAGIC_NUMBER = 0x9BC13AFE.toInt()
    const val NOT_A_VERSION_NUMBER = -1

    const val VERSION2 = 2
    const val VERSION201 = 201
    const val VERSION202 = 202
    const val VERSION_DELIGHT3 = 86736212
    const val MINIMUM_SUPPORTED_VERSION_OF_CODE_POINT_TABLE = VERSION201
    const val VERSION4_ONLY_FOR_TESTING = 399
    const val VERSION402 = 402
    const val VERSION403 = 403
    const val VERSION4 = VERSION403
    const val MINIMUM_SUPPORTED_STATIC_VERSION = VERSION202
    const val MAXIMUM_SUPPORTED_STATIC_VERSION = VERSION_DELIGHT3
    const val MINIMUM_SUPPORTED_DYNAMIC_VERSION = VERSION4
    const val MAXIMUM_SUPPORTED_DYNAMIC_VERSION = VERSION403

    const val MAX_WORD_LENGTH = DecoderSpecificConstants.DICTIONARY_MAX_WORD_LENGTH

    const val MASK_CHILDREN_ADDRESS_TYPE = 0xC0
    const val FLAG_CHILDREN_ADDRESS_TYPE_NOADDRESS = 0x00
    const val FLAG_CHILDREN_ADDRESS_TYPE_ONEBYTE = 0x40
    const val FLAG_CHILDREN_ADDRESS_TYPE_TWOBYTES = 0x80
    const val FLAG_CHILDREN_ADDRESS_TYPE_THREEBYTES = 0xC0

    const val FLAG_HAS_MULTIPLE_CHARS = 0x20

    const val FLAG_IS_TERMINAL = 0x10
    const val FLAG_HAS_SHORTCUT_TARGETS = 0x08
    const val FLAG_HAS_BIGRAMS = 0x04
    const val FLAG_IS_NOT_A_WORD = 0x02
    const val FLAG_IS_POSSIBLY_OFFENSIVE = 0x01

    const val FLAG_BIGRAM_SHORTCUT_ATTR_HAS_NEXT = 0x80
    const val FLAG_BIGRAM_ATTR_OFFSET_NEGATIVE = 0x40
    const val MASK_BIGRAM_ATTR_ADDRESS_TYPE = 0x30
    const val FLAG_BIGRAM_ATTR_ADDRESS_TYPE_ONEBYTE = 0x10
    const val FLAG_BIGRAM_ATTR_ADDRESS_TYPE_TWOBYTES = 0x20
    const val FLAG_BIGRAM_ATTR_ADDRESS_TYPE_THREEBYTES = 0x30
    const val FLAG_BIGRAM_SHORTCUT_ATTR_FREQUENCY = 0x0F

    const val PTNODE_CHARACTERS_TERMINATOR = 0x1F

    const val PTNODE_TERMINATOR_SIZE = 1
    const val PTNODE_FLAGS_SIZE = 1
    const val PTNODE_FREQUENCY_SIZE = 1
    const val PTNODE_MAX_ADDRESS_SIZE = 3
    const val PTNODE_ATTRIBUTE_FLAGS_SIZE = 1
    const val PTNODE_ATTRIBUTE_MAX_ADDRESS_SIZE = 3
    const val PTNODE_SHORTCUT_LIST_SIZE_SIZE = 2

    const val NO_CHILDREN_ADDRESS = Int.MIN_VALUE
    const val INVALID_CHARACTER = -1

    const val MAX_PTNODES_FOR_ONE_BYTE_PTNODE_COUNT = 0x7F // 127
    const val LARGE_PTNODE_ARRAY_SIZE_FIELD_SIZE_FLAG = 0x8000
    const val MAX_PTNODES_IN_A_PT_NODE_ARRAY = 0x7FFF // 32767
    const val MAX_BIGRAMS_IN_A_PTNODE = 10000
    const val MAX_SHORTCUT_LIST_SIZE_IN_A_PTNODE = 0xFFFF

    const val MAX_TERMINAL_FREQUENCY = 255
    const val MAX_BIGRAM_FREQUENCY = 15

    const val SHORTCUT_WHITELIST_FREQUENCY = 15

    const val NOT_VALID_WORD = -99

    const val UINT8_MAX = 0xFF
    const val UINT16_MAX = 0xFFFF
    const val UINT24_MAX = 0xFFFFFF
    const val MSB8 = 0x80
    const val MINIMAL_ONE_BYTE_CHARACTER_VALUE = 0x20
    const val MAXIMAL_ONE_BYTE_CHARACTER_VALUE = 0xFF

    /**
     * Options global to the dictionary.
     */
    class DictionaryOptions(val mAttributes: HashMap<String, String>) {
        override fun toString(): String {
            return toString(0, false)
        }

        fun toString(indentCount: Int, plumbing: Boolean): String {
            val indent = StringBuilder()
            if (plumbing) {
                indent.append("H:")
            } else {
                for (i in 0 until indentCount) {
                    indent.append(" ")
                }
            }
            val s = StringBuilder()
            for (optionKey in mAttributes.keys) {
                s.append(indent)
                s.append(optionKey)
                s.append(" = ")
                if (optionKey == "date" && !plumbing) {
                    s.append(Date(1000 * mAttributes[optionKey]!!.toLong()))
                } else {
                    s.append(mAttributes[optionKey])
                }
                s.append("\n")
            }
            return s.toString()
        }
    }
}
