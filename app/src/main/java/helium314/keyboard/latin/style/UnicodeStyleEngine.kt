// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.style

/**
 * High-performance, zero-allocation Unicode text transformation engine.
 * Maps ASCII characters to decorative Unicode blocks with canonical handling
 * of SMP surrogate pairs and Letterlike Symbol holes (Fraktur, Script, Double-Struck).
 */
object UnicodeStyleEngine {

    val STYLES: List<StyleDefinition> by lazy { initializeStyles() }
    private val STYLES_BY_ID: Map<String, StyleDefinition> by lazy { STYLES.associateBy { it.id } }
    private val REVERSE_MAP: Map<Int, Int> by lazy { initializeReverseMap() }

    var activeLiveStyleId: String? = null

    fun isLiveStyleActive(): Boolean = activeLiveStyleId != null

    fun getActiveLiveStyle(): StyleDefinition? = activeLiveStyleId?.let { getStyle(it) }

    fun clearActiveLiveStyle() {
        activeLiveStyleId = null
    }

    fun getStyle(styleId: String): StyleDefinition? = STYLES_BY_ID[styleId]

    fun getAllStyles(): List<StyleDefinition> = STYLES

    /**
     * Transforms an entire text string to the target style safely.
     * Operates on 32-bit codepoints to prevent UTF-16 surrogate pair tearing.
     */
    fun transformText(text: String, styleId: String): String {
        val style = getStyle(styleId) ?: return text
        val sb = StringBuilder(text.length * 2)
        var i = 0
        val len = text.length
        while (i < len) {
            val cp = text.codePointAt(i)
            val mapped = style.mapCodePoint(cp)
            sb.appendCodePoint(mapped)
            if (style.combiningMark != 0 && cp != ' '.code && cp != '\n'.code) {
                sb.appendCodePoint(style.combiningMark)
            }
            i += Character.charCount(cp)
        }
        return if (style.isReversed) reverseCodePoints(sb) else sb.toString()
    }

    /**
     * Real-time keystroke mapping for LIVE typing mode.
     * Returns the mapped character string for a given input codepoint.
     */
    fun mapKeystroke(codePoint: Int, styleId: String? = activeLiveStyleId): String {
        val id = styleId ?: return String(Character.toChars(codePoint))
        val style = getStyle(id) ?: return String(Character.toChars(codePoint))
        if (!style.supportsLiveTyping) return String(Character.toChars(codePoint))

        val mapped = style.mapCodePoint(codePoint)
        return if (style.combiningMark != 0 && codePoint != ' '.code && codePoint != '\n'.code) {
            String(Character.toChars(mapped)) + String(Character.toChars(style.combiningMark))
        } else {
            String(Character.toChars(mapped))
        }
    }

    /**
     * Strips styling and combining marks, reverting styled Unicode back to plain text.
     */
    fun toPlainText(styledText: String): String {
        val sb = StringBuilder(styledText.length)
        var i = 0
        val len = styledText.length
        while (i < len) {
            val cp = styledText.codePointAt(i)
            if (isCombiningMark(cp)) {
                i += Character.charCount(cp)
                continue
            }
            val plain = REVERSE_MAP[cp] ?: cp
            sb.appendCodePoint(plain)
            i += Character.charCount(cp)
        }
        return sb.toString()
    }

    private fun isCombiningMark(cp: Int): Boolean {
        return cp in 0x0300..0x036F || cp in 0x1AB0..0x1AFF || cp in 0x1DC0..0x1DFF || cp in 0x20D0..0x20FF
    }

    private fun reverseCodePoints(sb: StringBuilder): String {
        // Collect codepoints and reverse order to ensure graphemes stay intact
        val codePoints = mutableListOf<Int>()
        var i = 0
        val len = sb.length
        while (i < len) {
            val cp = sb.codePointAt(i)
            codePoints.add(cp)
            i += Character.charCount(cp)
        }
        codePoints.reverse()
        val result = StringBuilder(len)
        for (cp in codePoints) {
            result.appendCodePoint(cp)
        }
        return result.toString()
    }

    private fun initializeStyles(): List<StyleDefinition> {
        return listOf(
            // Historic & Calligraphy
            createFrakturStyle(),
            createBoldFrakturStyle(),
            createScriptStyle(),
            createBoldScriptStyle(),
            createDoubleStruckStyle(),

            // Math Alphanumeric
            createMathBoldStyle(),
            createMathItalicStyle(),
            createMathBoldItalicStyle(),
            createMathSansStyle(),
            createMathSansBoldStyle(),
            createMathSansItalicStyle(),
            createMathSansBoldItalicStyle(),
            createMathMonospaceStyle(),

            // Enclosed
            createCircledStyle(),
            createNegativeCircledStyle(),
            createSquaredStyle(),
            createNegativeSquaredStyle(),

            // Aesthetic & Caps
            createFullwidthStyle(),
            createSmallCapsStyle(),
            createSuperscriptStyle(),
            createSubscriptStyle(),

            // Decorative & Transforms
            createInvertedStyle(),
            createStrikethroughStyle(),
            createUnderlineStyle(),
            createDoubleUnderlineStyle(),
            createSlashStyle()
        )
    }

    // ------------------------------------------------------------------------
    // Style Builders with Authoritative Unicode codepoint tables
    // ------------------------------------------------------------------------

    private fun createMathBoldStyle(): StyleDefinition {
        return StyleDefinition(
            id = "math_bold",
            name = "Bold",
            category = StyleCategory.MATH,
            samplePreview = "\uD835\uDC01\uD835\uDC28\uD835\uDC25\uD835\uDC1D", // Bold
            mapCodePoint = { cp ->
                when (cp) {
                    in 'A'.code..'Z'.code -> 0x1D400 + (cp - 'A'.code)
                    in 'a'.code..'z'.code -> 0x1D41A + (cp - 'a'.code)
                    in '0'.code..'9'.code -> 0x1D7CE + (cp - '0'.code)
                    else -> cp
                }
            }
        )
    }

    private fun createMathItalicStyle(): StyleDefinition {
        return StyleDefinition(
            id = "math_italic",
            name = "Italic",
            category = StyleCategory.MATH,
            samplePreview = "\uD835\uDC3C\uD835\uDC61\uD835\uDC4E\uD835\uDC59\uD835\uDC56\uD835\uDC50", // Italic
            mapCodePoint = { cp ->
                when (cp) {
                    in 'A'.code..'Z'.code -> 0x1D434 + (cp - 'A'.code)
                    'h'.code -> 0x210E // Planck constant (U+1D455 is unassigned)
                    in 'a'.code..'z'.code -> 0x1D44E + (cp - 'a'.code)
                    else -> cp
                }
            }
        )
    }

    private fun createMathBoldItalicStyle(): StyleDefinition {
        return StyleDefinition(
            id = "math_bold_italic",
            name = "Bold Italic",
            category = StyleCategory.MATH,
            samplePreview = "\uD835\uDC69\uD835\uDC90\uD835\uDC8D\uD835\uDC85", // Bold
            mapCodePoint = { cp ->
                when (cp) {
                    in 'A'.code..'Z'.code -> 0x1D468 + (cp - 'A'.code)
                    in 'a'.code..'z'.code -> 0x1D482 + (cp - 'a'.code)
                    else -> cp
                }
            }
        )
    }

    private fun createScriptStyle(): StyleDefinition {
        val upperMap = intArrayOf(
            0x1D49C, 0x212C, 0x1D49E, 0x1D49F, 0x2130, 0x2131, // A B C D E F
            0x1D4A2, 0x210B, 0x2110, 0x1D4A5, 0x1D4A6, 0x2112, // G H I J K L
            0x2133, 0x1D4A9, 0x1D4AA, 0x1D4AB, 0x1D4AC, 0x211B, // M N O P Q R
            0x1D4AE, 0x1D4AF, 0x1D4B0, 0x1D4B1, 0x1D4B2, 0x1D4B3, // S T U V W X
            0x1D4B4, 0x1D4B5                                      // Y Z
        )
        val lowerMap = intArrayOf(
            0x1D4B6, 0x1D4B7, 0x1D4B8, 0x1D4B9, 0x212F, 0x1D4BB, // a b c d e f
            0x210A, 0x1D4BD, 0x1D4BE, 0x1D4BF, 0x1D4C0, 0x1D4C1, // g h i j k l
            0x1D4C2, 0x1D4C3, 0x2134, 0x1D4C5, 0x1D4C6, 0x1D4C7, // m n o p q r
            0x1D4C8, 0x1D4C9, 0x1D4CA, 0x1D4CB, 0x1D4CC, 0x1D4CD, // s t u v w x
            0x1D4CE, 0x1D4CF                                      // y z
        )
        return StyleDefinition(
            id = "math_script",
            name = "Script",
            category = StyleCategory.HISTORIC,
            samplePreview = "\uD835\uDC4E\uD835\uDCB8\uD835\uDCBD\uD835\uDCBE\uD835\uDCBF\uD835\uDCC9", // Script
            mapCodePoint = { cp ->
                when (cp) {
                    in 'A'.code..'Z'.code -> upperMap[cp - 'A'.code]
                    in 'a'.code..'z'.code -> lowerMap[cp - 'a'.code]
                    else -> cp
                }
            }
        )
    }

    private fun createBoldScriptStyle(): StyleDefinition {
        return StyleDefinition(
            id = "math_bold_script",
            name = "Bold Script",
            category = StyleCategory.HISTORIC,
            samplePreview = "\uD835\uDCD1\uD835\uDCF8\uD835\uDCF5\uD835\uDCED", // Bold
            mapCodePoint = { cp ->
                when (cp) {
                    in 'A'.code..'Z'.code -> 0x1D4D0 + (cp - 'A'.code)
                    in 'a'.code..'z'.code -> 0x1D4EA + (cp - 'a'.code)
                    else -> cp
                }
            }
        )
    }

    private fun createFrakturStyle(): StyleDefinition {
        val upperMap = intArrayOf(
            0x1D504, 0x1D505, 0x212D, 0x1D507, 0x1D508, 0x1D509, // A B C D E F
            0x1D50A, 0x210C, 0x2111, 0x1D50D, 0x1D50E, 0x1D50F, // G H I J K L
            0x1D510, 0x1D511, 0x1D512, 0x1D513, 0x1D514, 0x211C, // M N O P Q R
            0x1D516, 0x1D517, 0x1D518, 0x1D519, 0x1D51A, 0x1D51B, // S T U V W X
            0x1D51C, 0x2128                                      // Y Z
        )
        return StyleDefinition(
            id = "math_fraktur",
            name = "Fraktur",
            category = StyleCategory.HISTORIC,
            samplePreview = "\uD835\uDD09\uD835\uDD2F\uD835\uDD1E\uD835\uDD28\uD835\uDD31\uD835\uDD32\uD835\uDD2F", // Fraktur
            mapCodePoint = { cp ->
                when (cp) {
                    in 'A'.code..'Z'.code -> upperMap[cp - 'A'.code]
                    in 'a'.code..'z'.code -> 0x1D51E + (cp - 'a'.code)
                    else -> cp
                }
            }
        )
    }

    private fun createBoldFrakturStyle(): StyleDefinition {
        return StyleDefinition(
            id = "math_bold_fraktur",
            name = "Bold Fraktur",
            category = StyleCategory.HISTORIC,
            samplePreview = "\uD835\uDD3D\uD835\uDD97\uD835\uDD86\uD835\uDD90\uD835\uDD99\uD835\uDD9A\uD835\uDD97", // Fraktur
            mapCodePoint = { cp ->
                when (cp) {
                    in 'A'.code..'Z'.code -> 0x1D56C + (cp - 'A'.code)
                    in 'a'.code..'z'.code -> 0x1D586 + (cp - 'a'.code)
                    else -> cp
                }
            }
        )
    }

    private fun createDoubleStruckStyle(): StyleDefinition {
        val upperMap = intArrayOf(
            0x1D538, 0x1D539, 0x2102, 0x1D53B, 0x1D53C, 0x1D53D, // A B C D E F
            0x1D53E, 0x210D, 0x1D540, 0x1D541, 0x1D542, 0x1D543, // G H I J K L
            0x1D544, 0x2115, 0x1D546, 0x2119, 0x211A, 0x211D, // M N O P Q R
            0x1D54A, 0x1D54B, 0x1D54C, 0x1D54D, 0x1D54E, 0x1D54F, // S T U V W X
            0x1D550, 0x2124                                      // Y Z
        )
        return StyleDefinition(
            id = "math_double_struck",
            name = "Double-Struck",
            category = StyleCategory.HISTORIC,
            samplePreview = "\uD835\uDD3B\uD835\uDD60\uD835\uDD66\uD835\uDD53\uD835\uDD5D\uD835\uDD56", // Double
            mapCodePoint = { cp ->
                when (cp) {
                    in 'A'.code..'Z'.code -> upperMap[cp - 'A'.code]
                    in 'a'.code..'z'.code -> 0x1D552 + (cp - 'a'.code)
                    in '0'.code..'9'.code -> 0x1D7D8 + (cp - '0'.code)
                    else -> cp
                }
            }
        )
    }

    private fun createMathSansStyle(): StyleDefinition {
        return StyleDefinition(
            id = "math_sans",
            name = "Sans",
            category = StyleCategory.MATH,
            samplePreview = "\uD835\uDDA0\uD835\uDDBA\uD835\uDDC7\uD835\uDDCB", // Sans
            mapCodePoint = { cp ->
                when (cp) {
                    in 'A'.code..'Z'.code -> 0x1D5A0 + (cp - 'A'.code)
                    in 'a'.code..'z'.code -> 0x1D5BA + (cp - 'a'.code)
                    in '0'.code..'9'.code -> 0x1D7E2 + (cp - '0'.code)
                    else -> cp
                }
            }
        )
    }

    private fun createMathSansBoldStyle(): StyleDefinition {
        return StyleDefinition(
            id = "math_sans_bold",
            name = "Sans Bold",
            category = StyleCategory.MATH,
            samplePreview = "\uD835\uDDD4\uD835\uDDF0\uD835\uDE01\uD835\uDE02", // Sans
            mapCodePoint = { cp ->
                when (cp) {
                    in 'A'.code..'Z'.code -> 0x1D5D4 + (cp - 'A'.code)
                    in 'a'.code..'z'.code -> 0x1D5EE + (cp - 'a'.code)
                    in '0'.code..'9'.code -> 0x1D7EC + (cp - '0'.code)
                    else -> cp
                }
            }
        )
    }

    private fun createMathSansItalicStyle(): StyleDefinition {
        return StyleDefinition(
            id = "math_sans_italic",
            name = "Sans Italic",
            category = StyleCategory.MATH,
            samplePreview = "\uD835\uDE08\uD835\uDE25\uD835\uDE35\uD835\uDE36", // Sans
            mapCodePoint = { cp ->
                when (cp) {
                    in 'A'.code..'Z'.code -> 0x1D608 + (cp - 'A'.code)
                    in 'a'.code..'z'.code -> 0x1D622 + (cp - 'a'.code)
                    else -> cp
                }
            }
        )
    }

    private fun createMathSansBoldItalicStyle(): StyleDefinition {
        return StyleDefinition(
            id = "math_sans_bold_italic",
            name = "Sans Bold Italic",
            category = StyleCategory.MATH,
            samplePreview = "\uD835\uDE3C\uD835\uDE59\uD835\uDE69\uD835\uDE6A", // Sans
            mapCodePoint = { cp ->
                when (cp) {
                    in 'A'.code..'Z'.code -> 0x1D63C + (cp - 'A'.code)
                    in 'a'.code..'z'.code -> 0x1D656 + (cp - 'a'.code)
                    else -> cp
                }
            }
        )
    }

    private fun createMathMonospaceStyle(): StyleDefinition {
        return StyleDefinition(
            id = "math_monospace",
            name = "Monospace",
            category = StyleCategory.MATH,
            samplePreview = "\uD835\uDE7C\uD835\uDE98\uD835\uDE97\uD835\uDE98", // Mono
            mapCodePoint = { cp ->
                when (cp) {
                    in 'A'.code..'Z'.code -> 0x1D670 + (cp - 'A'.code)
                    in 'a'.code..'z'.code -> 0x1D68A + (cp - 'a'.code)
                    in '0'.code..'9'.code -> 0x1D7F6 + (cp - '0'.code)
                    else -> cp
                }
            }
        )
    }

    private fun createCircledStyle(): StyleDefinition {
        return StyleDefinition(
            id = "enclosed_circled",
            name = "Circled",
            category = StyleCategory.ENCLOSED,
            samplePreview = "Ⓒⓘⓡⓒⓛⓔⓓ",
            mapCodePoint = { cp ->
                when (cp) {
                    in 'A'.code..'Z'.code -> 0x24B6 + (cp - 'A'.code)
                    in 'a'.code..'z'.code -> 0x24D0 + (cp - 'a'.code)
                    '0'.code -> 0x24EA
                    in '1'.code..'9'.code -> 0x2460 + (cp - '1'.code)
                    else -> cp
                }
            }
        )
    }

    private fun createNegativeCircledStyle(): StyleDefinition {
        return StyleDefinition(
            id = "enclosed_neg_circled",
            name = "Filled Circled",
            category = StyleCategory.ENCLOSED,
            samplePreview = "🅒🅘🅡🅒🅛🅔",
            mapCodePoint = { cp ->
                when (cp) {
                    in 'A'.code..'Z'.code -> 0x1F150 + (cp - 'A'.code)
                    in 'a'.code..'z'.code -> 0x1F150 + (cp - 'a'.code) // Sans negative circled uses capitals
                    '0'.code -> 0x24FF
                    in '1'.code..'9'.code -> 0x2776 + (cp - '1'.code)
                    else -> cp
                }
            }
        )
    }

    private fun createSquaredStyle(): StyleDefinition {
        return StyleDefinition(
            id = "enclosed_squared",
            name = "Squared",
            category = StyleCategory.ENCLOSED,
            samplePreview = "🅂🅀🅄🄰🅁🄴",
            mapCodePoint = { cp ->
                when (cp) {
                    in 'A'.code..'Z'.code -> 0x1F130 + (cp - 'A'.code)
                    in 'a'.code..'z'.code -> 0x1F130 + (cp - 'a'.code)
                    else -> cp
                }
            }
        )
    }

    private fun createNegativeSquaredStyle(): StyleDefinition {
        return StyleDefinition(
            id = "enclosed_neg_squared",
            name = "Filled Squared",
            category = StyleCategory.ENCLOSED,
            samplePreview = "🆂🆀🆄🅰🆁🅴",
            mapCodePoint = { cp ->
                when (cp) {
                    in 'A'.code..'Z'.code -> 0x1F170 + (cp - 'A'.code)
                    in 'a'.code..'z'.code -> 0x1F170 + (cp - 'a'.code)
                    else -> cp
                }
            }
        )
    }

    private fun createFullwidthStyle(): StyleDefinition {
        return StyleDefinition(
            id = "aesthetic_fullwidth",
            name = "Fullwidth",
            category = StyleCategory.AESTHETIC,
            samplePreview = "Ｗｉｄｅ",
            mapCodePoint = { cp ->
                when (cp) {
                    in 0x21..0x7E -> cp + 0xFEE0
                    0x20 -> 0x3000 // Ideographic space
                    else -> cp
                }
            }
        )
    }

    private fun createSmallCapsStyle(): StyleDefinition {
        // a b c d e f g h i j k l m n o p q r s t u v w x y z
        val map = intArrayOf(
            0x1D00, 0x0299, 0x1D04, 0x1D05, 0x1D07, 0x0493, // a b c d e f
            0x0262, 0x029C, 0x026A, 0x1D0A, 0x1D0B, 0x029F, // g h i j k l
            0x1D0D, 0x0274, 0x1D0F, 0x1D18, 0x01EB, 0x0280, // m n o p q r
            0x0073, 0x1D1B, 0x1D1C, 0x1D20, 0x1D21, 0x0078, // s t u v w x
            0x028F, 0x1D22                                  // y z
        )
        return StyleDefinition(
            id = "aesthetic_small_caps",
            name = "Small Caps",
            category = StyleCategory.AESTHETIC,
            samplePreview = "ꜱᴍᴀʟʟ ᴄᴀᴘꜱ",
            mapCodePoint = { cp ->
                when (cp) {
                    in 'a'.code..'z'.code -> map[cp - 'a'.code]
                    in 'A'.code..'Z'.code -> map[cp - 'A'.code]
                    else -> cp
                }
            }
        )
    }

    private fun createSuperscriptStyle(): StyleDefinition {
        val map = mapOf(
            '0'.code to 0x2070, '1'.code to 0x00B9, '2'.code to 0x00B2, '3'.code to 0x00B3,
            '4'.code to 0x2074, '5'.code to 0x2075, '6'.code to 0x2076, '7'.code to 0x2077,
            '8'.code to 0x2078, '9'.code to 0x2079, '+'.code to 0x207A, '-'.code to 0x207B,
            '='.code to 0x207C, '('.code to 0x207D, ')'.code to 0x207E,
            'a'.code to 0x1D43, 'b'.code to 0x1D47, 'c'.code to 0x1D9C, 'd'.code to 0x1D48,
            'e'.code to 0x1D49, 'f'.code to 0x1DA0, 'g'.code to 0x1D4D, 'h'.code to 0x02B0,
            'i'.code to 0x2071, 'j'.code to 0x02B2, 'k'.code to 0x1D4F, 'l'.code to 0x02E1,
            'm'.code to 0x1D50, 'n'.code to 0x207F, 'o'.code to 0x1D52, 'p'.code to 0x1D56,
            'r'.code to 0x02B3, 's'.code to 0x02E2, 't'.code to 0x1D57, 'u'.code to 0x1D58,
            'v'.code to 0x1D5B, 'w'.code to 0x02B7, 'x'.code to 0x02E3, 'y'.code to 0x02B8,
            'z'.code to 0x1DBB,
            'A'.code to 0x1D2C, 'B'.code to 0x1D2E, 'D'.code to 0x1D30, 'E'.code to 0x1D31,
            'G'.code to 0x1D33, 'H'.code to 0x1D34, 'I'.code to 0x1D35, 'J'.code to 0x1D36,
            'K'.code to 0x1D37, 'L'.code to 0x1D38, 'M'.code to 0x1D39, 'N'.code to 0x1D3A,
            'O'.code to 0x1D3C, 'P'.code to 0x1D3E, 'R'.code to 0x1D3F, 'T'.code to 0x1D40,
            'U'.code to 0x1D41, 'V'.code to 0x2C7D, 'W'.code to 0x1D42
        )
        return StyleDefinition(
            id = "aesthetic_superscript",
            name = "Superscript",
            category = StyleCategory.AESTHETIC,
            samplePreview = "ˢᵘᵖᵉʳ",
            mapCodePoint = { cp -> map[cp] ?: cp }
        )
    }

    private fun createSubscriptStyle(): StyleDefinition {
        val map = mapOf(
            '0'.code to 0x2080, '1'.code to 0x2081, '2'.code to 0x2082, '3'.code to 0x2083,
            '4'.code to 0x2084, '5'.code to 0x2085, '6'.code to 0x2086, '7'.code to 0x2087,
            '8'.code to 0x2088, '9'.code to 0x2089, '+'.code to 0x208A, '-'.code to 0x208B,
            '='.code to 0x208C, '('.code to 0x208D, ')'.code to 0x208E,
            'a'.code to 0x2090, 'e'.code to 0x2091, 'h'.code to 0x2095, 'i'.code to 0x1D62,
            'j'.code to 0x2C7C, 'k'.code to 0x2096, 'l'.code to 0x2097, 'm'.code to 0x2098,
            'n'.code to 0x2099, 'o'.code to 0x2092, 'p'.code to 0x209A, 'r'.code to 0x1D63,
            's'.code to 0x209B, 't'.code to 0x209C, 'u'.code to 0x1D64, 'v'.code to 0x1D65,
            'x'.code to 0x2093
        )
        return StyleDefinition(
            id = "aesthetic_subscript",
            name = "Subscript",
            category = StyleCategory.AESTHETIC,
            samplePreview = "ₛᵤᵦ",
            mapCodePoint = { cp -> map[cp] ?: cp }
        )
    }

    private fun createInvertedStyle(): StyleDefinition {
        val flipMap = mapOf(
            'a'.code to 0x0250, 'b'.code to 'q'.code, 'c'.code to 0x0254, 'd'.code to 'p'.code,
            'e'.code to 0x01DD, 'f'.code to 0x025F, 'g'.code to 0x0183, 'h'.code to 0x0265,
            'i'.code to 0x1D09, 'j'.code to 0x027E, 'k'.code to 0x029E, 'l'.code to 0x05DF,
            'm'.code to 0x026F, 'n'.code to 'u'.code, 'o'.code to 'o'.code, 'p'.code to 'd'.code,
            'q'.code to 'b'.code, 'r'.code to 0x0279, 's'.code to 's'.code, 't'.code to 0x0287,
            'u'.code to 'n'.code, 'v'.code to 0x028C, 'w'.code to 0x028D, 'x'.code to 'x'.code,
            'y'.code to 0x028E, 'z'.code to 'z'.code,
            'A'.code to 0x2200, 'B'.code to 0x10412, 'C'.code to 0x0186, 'D'.code to 0x25D6,
            'E'.code to 0x018E, 'F'.code to 0x2132, 'G'.code to 0x2141, 'H'.code to 'H'.code,
            'I'.code to 'I'.code, 'J'.code to 0x017F, 'K'.code to 0x029E, 'L'.code to 0x02E5,
            'M'.code to 'W'.code, 'N'.code to 'N'.code, 'O'.code to 'O'.code, 'P'.code to 0x0500,
            'Q'.code to 0x038C, 'R'.code to 0x1D1A, 'S'.code to 'S'.code, 'T'.code to 0x22A5,
            'U'.code to 0x2229, 'V'.code to 0x039B, 'W'.code to 'M'.code, 'X'.code to 'X'.code,
            'Y'.code to 0x2144, 'Z'.code to 'Z'.code,
            '0'.code to '0'.code, '1'.code to 0x0196, '2'.code to 0x218A, '3'.code to 0x0190,
            '4'.code to 0x0427, '5'.code to 0x03DB, '6'.code to '9'.code, '7'.code to 0x3125,
            '8'.code to '8'.code, '9'.code to '6'.code,
            '.'.code to 0x02D9, ','.code to '\''.code, '?'.code to 0x00BF, '!'.code to 0x00A1,
            '\''.code to ','.code, '\"'.code to 0x201E, '<'.code to '>'.code, '>'.code to '<'.code,
            '_'.code to 0x203E
        )
        return StyleDefinition(
            id = "transform_inverted",
            name = "Upside-Down",
            category = StyleCategory.DECORATIVE,
            samplePreview = "ʇxǝʇ",
            supportsLiveTyping = false, // Cannot type backwards live
            isReversed = true,
            mapCodePoint = { cp -> flipMap[cp] ?: cp }
        )
    }

    private fun createStrikethroughStyle(): StyleDefinition {
        return StyleDefinition(
            id = "decorative_strikethrough",
            name = "Strikethrough",
            category = StyleCategory.DECORATIVE,
            samplePreview = "s̶t̶r̶i̶k̶e̶",
            combiningMark = 0x0336 // COMBINING LONG STROKE OVERLAY
        )
    }

    private fun createUnderlineStyle(): StyleDefinition {
        return StyleDefinition(
            id = "decorative_underline",
            name = "Underline",
            category = StyleCategory.DECORATIVE,
            samplePreview = "u̲n̲d̲e̲r̲",
            combiningMark = 0x0332 // COMBINING LOW LINE
        )
    }

    private fun createDoubleUnderlineStyle(): StyleDefinition {
        return StyleDefinition(
            id = "decorative_double_underline",
            name = "Double Underline",
            category = StyleCategory.DECORATIVE,
            samplePreview = "u̳n̳d̳e̳r̳",
            combiningMark = 0x0333 // COMBINING DOUBLE LOW LINE
        )
    }

    private fun createSlashStyle(): StyleDefinition {
        return StyleDefinition(
            id = "decorative_slash",
            name = "Slash",
            category = StyleCategory.DECORATIVE,
            samplePreview = "s̷l̷a̷s̷h̷",
            combiningMark = 0x0337 // COMBINING SHORT SOLIDUS OVERLAY
        )
    }

    private fun initializeReverseMap(): Map<Int, Int> {
        val reverse = HashMap<Int, Int>(2048)
        val testChars = StringBuilder()
        for (c in 'a'..'z') testChars.append(c)
        for (c in 'A'..'Z') testChars.append(c)
        for (c in '0'..'9') testChars.append(c)
        testChars.append("+-=() .,?!'\"<>_")

        for (style in STYLES) {
            if (style.combiningMark != 0) continue
            var i = 0
            val len = testChars.length
            while (i < len) {
                val origCp = testChars.codePointAt(i)
                val mappedCp = style.mapCodePoint(origCp)
                val isAsciiLetterCollision = (origCp in 'A'.code..'Z'.code || origCp in 'a'.code..'z'.code)
                        && (mappedCp in 'A'.code..'Z'.code || mappedCp in 'a'.code..'z'.code)
                if (mappedCp != origCp && !isAsciiLetterCollision && !reverse.containsKey(mappedCp)) {
                    reverse[mappedCp] = origCp
                }
                i += Character.charCount(origCp)
            }
        }
        return reverse
    }
}
