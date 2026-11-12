// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard

import android.graphics.Paint
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.common.Constants
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    ShadowInputMethodManager2::class,
    ShadowProximityInfo::class,
])
class PopupKeysKeyboardTest {
    private lateinit var latinIME: LatinIME
    private lateinit var kbdParams: KeyboardParams

    @BeforeTest fun setUp() {
        latinIME = Robolectric.setupService(LatinIME::class.java)
        ShadowLog.setupLogging()
        ShadowLog.stream = System.out

        kbdParams = KeyboardParams()
        kbdParams.mOccupiedWidth = 1000
        kbdParams.mBaseWidth = 1000
        kbdParams.mOccupiedHeight = 400
        kbdParams.mBaseHeight = 400
        kbdParams.mDefaultRowHeight = 100f
        kbdParams.mDefaultAbsoluteKeyWidth = 100
        kbdParams.mAbsolutePopupKeyWidth = 100
        kbdParams.mMaxPopupKeysKeyboardColumn = 8
        kbdParams.GRID_WIDTH = 10
        kbdParams.GRID_HEIGHT = 4
        val layoutParams = KeyboardLayoutSet.Params()
        layoutParams.mEditorInfo = android.view.inputmethod.EditorInfo()
        layoutParams.mSubtype = helium314.keyboard.latin.RichInputMethodSubtype.emojiSubtype
        layoutParams.mKeyboardWidth = 1000
        layoutParams.mKeyboardHeight = 400
        kbdParams.mId = KeyboardId(KeyboardId.ELEMENT_ALPHABET, layoutParams)
    }

    @Test fun testCustomSpacebarPopupKeysGridLayout() {
        // Reproduce issue #569: wide spacebar (e.g. 600px) with 20 custom popup keys
        val emojis = listOf(
            "😊", "👍", "🤣", "😉", "👏", "😍", "😋", "👌", "✌️", "🤔",
            "😬", "☹️", "😲", "🙌", "😱", "🤷", "🤬", "☝️", "🤘", "🤦"
        )
        val popupKeySpecs = emojis.joinToString(",")
        val keyParams = Key.KeyParams("space", Constants.CODE_SPACE, null, popupKeySpecs, 0, kbdParams)
        keyParams.mWidth = 0.6f
        keyParams.mHeight = 100f
        keyParams.mAbsoluteWidth = 600f
        keyParams.mAbsoluteHeight = 100f
        val spaceKey = Key(keyParams)

        assertEquals(600, spaceKey.drawWidth)
        assertEquals(20, spaceKey.popupKeys?.size)

        val keyboard = Keyboard(kbdParams)
        val builder = PopupKeysKeyboard.Builder(latinIME, spaceKey, keyboard, Paint())
        val popupKeyboard = builder.build()

        // Key width must use standard popup key width (100px), not spacebar width (600px)
        val popupKeys = popupKeyboard.sortedKeys.filter { !it.isSpacer }
        assertEquals(20, popupKeys.size)
        assertEquals(100, popupKeys.first().drawWidth)

        // Columns must be multi-column grid layout (7 keys per row across 3 rows), not collapsed into 1 single column
        val keysByRow = popupKeys.groupBy { it.y }
        assertEquals(3, keysByRow.size, "Expected 3 rows in grid layout")
        val maxKeysInRow = keysByRow.values.maxOf { it.size }
        assertEquals(7, maxKeysInRow, "Expected 7 columns in grid layout")
    }

    @Test fun testFixedOrderCustomSpacebarPopupKeys() {
        // With explicit !fixedOrder!5 directive
        val emojis = listOf(
            "!fixedOrder!5",
            "😊", "👍", "🤣", "😉", "👏", "😍", "😋", "👌", "✌️", "🤔",
            "😬", "☹️", "😲", "🙌", "😱", "🤷", "🤬", "☝️", "🤘", "🤦"
        )
        val popupKeySpecs = emojis.joinToString(",")
        val keyParams = Key.KeyParams("space", Constants.CODE_SPACE, null, popupKeySpecs, 0, kbdParams)
        keyParams.mWidth = 0.6f
        keyParams.mHeight = 100f
        keyParams.mAbsoluteWidth = 600f
        keyParams.mAbsoluteHeight = 100f
        val spaceKey = Key(keyParams)

        val keyboard = Keyboard(kbdParams)
        val builder = PopupKeysKeyboard.Builder(latinIME, spaceKey, keyboard, Paint())
        val popupKeyboard = builder.build()

        val popupKeys = popupKeyboard.sortedKeys.filter { !it.isSpacer }
        assertEquals(20, popupKeys.size)
        assertEquals(100, popupKeys.first().drawWidth)

        val columnCount = popupKeys.map { it.x }.distinct().size
        val rowCount = popupKeys.map { it.y }.distinct().size
        assertEquals(5, columnCount)
        assertEquals(4, rowCount)
    }

    @Test fun testMultiRowArabicPopupVerticalHitDetection() {
        val arabicPunctuationSpecs = listOf(
            "!fixedOrder!8",
            "ٔ◌|ٔ", "ّ◌|ّ", "ٍ◌|ٍ", "ً◌|ً", "ْ◌|ْ", "ٌ◌|ٌ", "؟", "!",
            "ٖ◌|ٖ", "ٰ◌|ٰ", "ٓ◌|ٓ", "ِ◌|ِ", "ُ◌|ُ", "ـــ|ـ", "ٕ◌|ٕ", "َ◌|َ"
        ).joinToString(",")
        val keyParams = Key.KeyParams(".", '.'.code, null, arabicPunctuationSpecs, 0, kbdParams)
        keyParams.mWidth = 0.1f
        keyParams.mHeight = 100f
        keyParams.mAbsoluteWidth = 100f
        keyParams.mAbsoluteHeight = 100f
        val periodKey = Key(keyParams)

        val keyboard = Keyboard(kbdParams)
        val popupKeyboard = PopupKeysKeyboard.Builder(latinIME, periodKey, keyboard, Paint()).build()

        val popupKeys = popupKeyboard.sortedKeys.filter { !it.isSpacer }
        assertEquals(16, popupKeys.size)

        val rowsByY = popupKeys.groupBy { it.y }.toSortedMap()
        assertEquals(2, rowsByY.size, "Expected 2 rows for 16-key Arabic punctuation popup")

        val topRowKeys = rowsByY.values.first()
        val bottomRowKeys = rowsByY.values.last()
        assertEquals(8, topRowKeys.size)
        assertEquals(8, bottomRowKeys.size)

        // Bottom row keys must not extend hitBox upward into top row, and vice versa
        for (topKey in topRowKeys) {
            assertEquals(popupKeyboard.mTopPadding, topKey.hitBox.top)
            assertTrue(topKey.hitBox.bottom <= bottomRowKeys.first().y)
        }
        for (bottomKey in bottomRowKeys) {
            assertTrue(bottomKey.hitBox.top >= topRowKeys.first().y + topRowKeys.first().height)
            assertTrue(bottomKey.hitBox.bottom >= popupKeyboard.mOccupiedHeight)
        }

        val detector = PopupKeysDetector(20f)
        detector.setKeyboard(popupKeyboard, 0f, 0f)
        for (key in popupKeys) {
            val hit = detector.detectHitKey(key.x + key.width / 2, key.y + key.height / 2)
            assertEquals(key, hit, "Expected hit detection at center of key '${key.label}' at y=${key.y}")
        }
        // Sliding slightly above top row or below bottom row should still track the respective row
        val firstTopKey = topRowKeys.first()
        val firstBottomKey = bottomRowKeys.first()
        assertEquals(
            firstTopKey,
            detector.detectHitKey(firstTopKey.x + firstTopKey.width / 2, firstTopKey.y - 10)
        )
        assertEquals(
            firstBottomKey,
            detector.detectHitKey(firstBottomKey.x + firstBottomKey.width / 2, firstBottomKey.y + firstBottomKey.height + 10)
        )
        // Crossing the boundary between top row and bottom row must switch cleanly
        val midX = firstTopKey.x + firstTopKey.width / 2
        assertEquals(firstTopKey, detector.detectHitKey(midX, firstTopKey.y + firstTopKey.height - 5))
        assertEquals(firstBottomKey, detector.detectHitKey(midX, firstBottomKey.y + 5))
    }
}
