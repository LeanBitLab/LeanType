// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.cjk

import android.view.KeyEvent
import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CjkEngineTest {

    @Test
    fun testKeycodeTranslatorConvertsStandardKeys() {
        val letterEvent = Event.createSoftwareKeypressEvent('a'.code, 0, 0, 0, false)
        assertEquals('a'.code, KeycodeTranslator.toKeysym(letterEvent))

        val spaceEvent = Event.createSoftwareKeypressEvent(Constants.CODE_SPACE, 0, 0, 0, false)
        assertEquals(KeycodeTranslator.XK_SPACE, KeycodeTranslator.toKeysym(spaceEvent))

        val enterEvent = Event.createSoftwareKeypressEvent(Constants.CODE_ENTER, 0, 0, 0, false)
        assertEquals(KeycodeTranslator.XK_RETURN, KeycodeTranslator.toKeysym(enterEvent))

        val deleteEvent = Event.createSoftwareKeypressEvent(KeyCode.DELETE, 0, 0, 0, false)
        assertEquals(KeycodeTranslator.XK_BACKSPACE, KeycodeTranslator.toKeysym(deleteEvent))

        val keyEventDel = Event.createHardwareKeypressEvent(Constants.NOT_A_CODE, KeyEvent.KEYCODE_DEL, 0, null, false)
        assertEquals(KeycodeTranslator.XK_BACKSPACE, KeycodeTranslator.toKeysym(keyEventDel))
    }

    @Test
    fun testMockPinyinEngineComposition() {
        val engine = MockPinyinEngine()
        engine.init(androidx.test.core.app.ApplicationProvider.getApplicationContext(), "", "")
        assertTrue(engine.isAvailable())

        // 1. Type "ni"
        assertTrue(engine.processKey('n'.code))
        assertTrue(engine.processKey('i'.code))

        val ctx1 = engine.getContext()
        assertNotNull(ctx1)
        assertEquals("ni", ctx1?.preedit)
        assertTrue(ctx1?.candidates?.any { it.text == "你" } == true)

        // 2. Type "hao" -> "nihao"
        assertTrue(engine.processKey('h'.code))
        assertTrue(engine.processKey('a'.code))
        assertTrue(engine.processKey('o'.code))

        val ctx2 = engine.getContext()
        assertNotNull(ctx2)
        assertEquals("nihao", ctx2?.preedit)
        assertEquals("你好", ctx2?.candidates?.firstOrNull()?.text)

        // 3. Press Space -> commits "你好"
        assertTrue(engine.processKey(KeycodeTranslator.XK_SPACE))
        assertEquals("你好", engine.getCommit())
        assertNull(engine.getContext())
        assertNull(engine.getCommit()) // Second call should be cleared
    }

    @Test
    fun testMockPinyinEngineBackspaceAndEnter() {
        val engine = MockPinyinEngine()
        engine.init(androidx.test.core.app.ApplicationProvider.getApplicationContext(), "", "")

        // Type "ni"
        engine.processKey('n'.code)
        engine.processKey('i'.code)
        assertEquals("ni", engine.getContext()?.preedit)

        // Backspace pops 'i'
        assertTrue(engine.processKey(KeycodeTranslator.XK_BACKSPACE))
        assertEquals("n", engine.getContext()?.preedit)

        // Enter commits raw pre-edit Latin text
        assertTrue(engine.processKey(KeycodeTranslator.XK_RETURN))
        assertEquals("n", engine.getCommit())
        assertNull(engine.getContext())
    }

    @Test
    fun testMockPinyinEnginePreservesUppercaseLetters() {
        val engine = MockPinyinEngine()
        engine.init(androidx.test.core.app.ApplicationProvider.getApplicationContext(), "", "")

        // Type "WiFi" (Shift + W, i, Shift + F, i)
        assertTrue(engine.processKey('W'.code))
        assertTrue(engine.processKey('i'.code))
        assertTrue(engine.processKey('F'.code))
        assertTrue(engine.processKey('i'.code))

        val ctx = engine.getContext()
        assertNotNull(ctx)
        assertEquals("WiFi", ctx?.preedit)
        assertEquals("WiFi", ctx?.candidates?.firstOrNull()?.text)

        // Enter commits "WiFi"
        assertTrue(engine.processKey(KeycodeTranslator.XK_RETURN))
        assertEquals("WiFi", engine.getCommit())
        assertNull(engine.getContext())
    }

    @Test
    fun testMockPinyinEngineShiftCapitalizedPinyin() {
        val engine = MockPinyinEngine()
        engine.init(androidx.test.core.app.ApplicationProvider.getApplicationContext(), "", "")

        // Type "Ni" (Shift + N, i)
        assertTrue(engine.processKey('N'.code))
        assertTrue(engine.processKey('i'.code))

        val ctx = engine.getContext()
        assertNotNull(ctx)
        assertEquals("Ni", ctx?.preedit)
        // Candidates for "ni" are matched case-insensitively
        assertTrue(ctx?.candidates?.any { it.text == "你" } == true)

        // Enter commits raw Latin text "Ni"
        assertTrue(engine.processKey(KeycodeTranslator.XK_RETURN))
        assertEquals("Ni", engine.getCommit())
    }

    @Test
    fun testMockPinyinEngineNumberCandidateSelection() {
        val engine = MockPinyinEngine()
        engine.init(androidx.test.core.app.ApplicationProvider.getApplicationContext(), "", "")

        // Type "ni" -> candidates: ["你", "泥", "拟", "逆", "妮"]
        engine.processKey('n'.code)
        engine.processKey('i'.code)

        val candidates = engine.getContext()?.candidates
        assertNotNull(candidates)
        assertTrue(candidates!!.size >= 2)
        val secondCandidate = candidates[1].text // "泥"

        // Press '2' (0x32)
        assertTrue(engine.processKey(0x32))
        assertEquals(secondCandidate, engine.getCommit())
        assertNull(engine.getContext())
    }

    @Test
    fun testOnUpdateSelectionRetainsCompositionOnNegativeSpan() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = CjkInputManager.getInstance(context)
        manager.isComposing = true

        // Intermediate Android IME update with -1 composing bounds should NOT clear composition
        manager.onUpdateSelection(
            oldSelStart = 2, oldSelEnd = 2,
            newSelStart = 2, newSelEnd = 2,
            composingSpanStart = -1, composingSpanEnd = -1
        )
        assertTrue(manager.isComposing)
    }

    @Test
    fun testOnUpdateSelectionClearsWhenCursorMovesOutsideSpan() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = CjkInputManager.getInstance(context)
        manager.isComposing = true

        // Cursor moved outside valid composing span [0, 5] to position 10
        manager.onUpdateSelection(
            oldSelStart = 2, oldSelEnd = 2,
            newSelStart = 10, newSelEnd = 10,
            composingSpanStart = 0, composingSpanEnd = 5
        )
        assertFalse(manager.isComposing)
    }

    @Test
    fun testOnUpdateSelectionClearsOnActiveSelectionRange() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = CjkInputManager.getInstance(context)
        manager.isComposing = true

        // Text selection range created by user
        manager.onUpdateSelection(
            oldSelStart = 2, oldSelEnd = 2,
            newSelStart = 2, newSelEnd = 5,
            composingSpanStart = 2, composingSpanEnd = 5
        )
        assertFalse(manager.isComposing)
    }

    @Test
    fun testIsCjkSubtypeActiveLanguageRouting() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = CjkInputManager.getInstance(context)

        // Chinese subtype should be active
        val zhSubtype = helium314.keyboard.latin.RichInputMethodSubtype.get(
            android.view.inputmethod.InputMethodSubtype.InputMethodSubtypeBuilder()
                .setSubtypeLocale("zh_CN")
                .setSubtypeExtraValue("KeyboardLayoutSet=MAIN:qwerty,CjkEngine")
                .build()
        )
        assertTrue(manager.isCjkSubtypeActive(zhSubtype))

        // Japanese subtype should be active
        val jaSubtype = helium314.keyboard.latin.RichInputMethodSubtype.get(
            android.view.inputmethod.InputMethodSubtype.InputMethodSubtypeBuilder()
                .setSubtypeLocale("ja_JP")
                .setSubtypeExtraValue("KeyboardLayoutSet=MAIN:qwerty,CjkEngine")
                .build()
        )
        assertTrue(manager.isCjkSubtypeActive(jaSubtype))

        // Standard Korean subtype without CjkEngine should NOT be captured by CJK manager
        val koSubtype = helium314.keyboard.latin.RichInputMethodSubtype.get(
            android.view.inputmethod.InputMethodSubtype.InputMethodSubtypeBuilder()
                .setSubtypeLocale("ko_KR")
                .setSubtypeExtraValue("KeyboardLayoutSet=MAIN:korean,CombiningRules=hangul")
                .build()
        )
        assertFalse(manager.isCjkSubtypeActive(koSubtype))

        // English subtype should not be active
        val enSubtype = helium314.keyboard.latin.RichInputMethodSubtype.get(
            android.view.inputmethod.InputMethodSubtype.InputMethodSubtypeBuilder()
                .setSubtypeLocale("en_US")
                .setSubtypeExtraValue("KeyboardLayoutSet=MAIN:qwerty")
                .build()
        )
        assertFalse(manager.isCjkSubtypeActive(enSubtype))
    }
}
