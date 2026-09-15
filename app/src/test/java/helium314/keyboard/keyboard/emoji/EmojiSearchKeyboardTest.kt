// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import android.app.Activity
import android.content.res.AssetManager
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowLocaleManagerCompat
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.MainKeyboardView
import helium314.keyboard.keyboard.PointerTracker
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.latin.SingleDictionaryFacilitator
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.LayoutType
import helium314.keyboard.latin.utils.LayoutType.Companion.toExtraValue
import helium314.keyboard.latin.utils.LayoutUtilsCustom
import helium314.keyboard.latin.utils.SubtypeUtilsAdditional
import helium314.keyboard.latin.utils.prefs
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [
    ShadowInputMethodManager2::class,
    ShadowLocaleManagerCompat::class,
    ShadowProximityInfo::class,
])
class EmojiSearchKeyboardTest {
    private lateinit var ime: LatinIME
    private lateinit var activity: Activity
    private lateinit var root: LinearLayout
    private lateinit var tabs: LinearLayout
    private lateinit var palettes: EmojiPalettesView
    private lateinit var main: MainKeyboardView
    private lateinit var bottom: MainKeyboardView
    private lateinit var subtype: RichInputMethodSubtype
    private val switcher get() = KeyboardSwitcher.getInstance()
    private val host = Mockito.mock(KeyboardActionListener::class.java)
    private val searchListener get() = field(bottom, "mKeyboardActionListener") as KeyboardActionListener
    private val searchBar get() = descendants(tabs).filterIsInstance<EditText>().single()
    private val stateMode get() = field(field(switcher, "mState")!!, "mode").toString()

    @Before
    fun setUp() {
        ime = Robolectric.setupService(LatinIME::class.java)
        ime.prefs().edit()
            .clear()
            .putBoolean(Settings.PREF_SHOW_NUMBER_ROW, true)
            .putBoolean(Settings.PREF_SHOW_NUMBER_ROW_IN_SYMBOLS, true)
            .putBoolean(Settings.PREF_COMPACT_NUMBER_ROW_IN_SYMBOLS, true)
            .putBoolean(Settings.PREF_SPLIT_TOOLBAR, false)
            .commit()
        val layouts = LayoutType.getLayoutMap(null).apply { put(LayoutType.MAIN, "qwerty") }
        for ((index, type) in customTypes.withIndex()) {
            val name = LayoutUtilsCustom.getLayoutName("Search slot ${index + 1}", type)
            LayoutUtilsCustom.getLayoutFile(name + "txt", type, ime).writeText("z\nx\nv\n\nj\nk\nl\n\n${index + 1}\nb\nn\n")
            layouts[type] = name
        }
        LayoutUtilsCustom.onLayoutFileChanged()
        subtype = RichInputMethodSubtype.get(SubtypeUtilsAdditional.createAdditionalSubtype(
            Locale.US, "${Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET}=${layouts.toExtraValue()}", true, true
        ))
        RichInputMethodManager.forceSubtype(subtype.rawSubtype)
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(KeyboardTheme.getKeyboardTheme(ime).mStyleId)
        // Android asset paths use '/', including when these tests run on Windows.
        val assets = Mockito.mock(AssetManager::class.java)
        Mockito.`when`(assets.list(Mockito.anyString())).thenAnswer {
            ime.assets.list(it.getArgument<String>(0).replace('\\', '/'))
        }
        Mockito.`when`(assets.open(Mockito.anyString())).thenAnswer {
            ime.assets.open(it.getArgument<String>(0).replace('\\', '/'))
        }
        val context = object : ContextThemeWrapper(ime, KeyboardTheme.getKeyboardTheme(ime).mStyleId) {
            override fun getAssets() = assets
        }
        root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        tabs = LinearLayout(context)
        main = MainKeyboardView(context)
        palettes = LayoutInflater.from(context).inflate(R.layout.emoji_palettes_view, root, false) as EmojiPalettesView
        bottom = palettes.findViewById(R.id.bottom_row_keyboard)
        root.addView(tabs)
        root.addView(main)
        root.addView(palettes)
        activity.setContentView(root)
        setField(switcher, "mThemeContext", context)
        setField(switcher, "mKeyboardView", main)
        setField(switcher, "mEmojiPalettesView", palettes)
        setField(switcher, "mEmojiTabStripView", tabs)
        main.setKeyboardActionListener(host)
        switcher.loadKeyboard(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }, Settings.getValues(), 0, null, null)
        assertTrue(palettes.isAttachedToWindow)
        assertEquals(KeyboardId.ELEMENT_ALPHABET, main.keyboard?.mId?.mElementId)
        assertEquals(subtype, main.keyboard!!.mId.mSubtype)
        customTypes.forEach { type ->
            assertTrue(LayoutUtilsCustom.getLayoutFiles(type, context).any { it.name.startsWith(subtype.layouts[type]!!) })
        }
    }

    @After
    fun tearDown() {
        if (::activity.isInitialized) activity.finish()
        if (::ime.isInitialized) ime.onDestroy()
        RichInputMethodManager::class.java.getDeclaredField("forcedSubtypeForTesting")
            .apply { isAccessible = true }.set(null, null)
    }

    @Test
    fun magnifierKeepsEachCustomSlotAndQueryOutOfHost() {
        for (index in customTypes.indices) {
            event(customCodes[index])
            val original = main.keyboard!!
            assertEquals(customElements[index], original.mId.mElementId)
            assertEquals(listOf("z", "x", "v"), keyRows(original).first().mapNotNull { it.second })
            event(KeyCode.EMOJI)
            palettes.setKeyboardActionListener(host)
            openSearch()
            assertEquals(customElements[index], bottom.keyboard!!.mId.mElementId)
            assertEquals(subtype, bottom.keyboard!!.mId.mSubtype)
            assertEquals(subtype, RichInputMethodManager.getInstance().currentSubtype)
            assertEquals(keyRows(original), keyRows(bottom.keyboard!!))
            searchListener.onCodeInput('x'.code, 0, 0, false)
            searchListener.onTextInput("cat")
            assertEquals("xcat", searchBar.text.toString())
            Mockito.verifyNoInteractions(host)
            closeSearch()
        }
    }

    @Test
    fun searchKeepsNormalKeyboardOptions() {
        val original = main.keyboard!!
        event(KeyCode.EMOJI)
        palettes.setKeyboardActionListener(host)
        openSearch()
        assertTrue(original.mId.mNumberRowEnabled)
        assertTrue(bottom.keyboard!!.mId.mNumberRowEnabled)
        assertEquals(keyRows(original), keyRows(bottom.keyboard!!))
        assertEquals(original.mId.mNumberRowInSymbols, bottom.keyboard!!.mId.mNumberRowInSymbols)
        assertEquals(original.mId.mCompactNumberRowInSymbols, bottom.keyboard!!.mId.mCompactNumberRowInSymbols)
        assertEquals(original.mId.mEmojiKeyEnabled, bottom.keyboard!!.mId.mEmojiKeyEnabled)
        assertEquals(original.mId.mLanguageSwitchKeyEnabled, bottom.keyboard!!.mId.mLanguageSwitchKeyEnabled)
        assertEquals(original.mId.mHasShortcutKey, bottom.keyboard!!.mId.mHasShortcutKey)
    }

    @Test
    fun cancelRestoresCustomLayoutAndState() {
        event(KeyCode.CUSTOM3)
        val original = main.keyboard!!
        event(KeyCode.EMOJI)
        palettes.setKeyboardActionListener(host)
        openSearch()
        closeSearch()
        assertFalse(palettes.isShown)
        assertEquals(View.VISIBLE, main.visibility)
        assertEquals(original.mId, main.keyboard!!.mId)
        assertEquals("CUSTOM3", stateMode)
        assertSame(host, field(bottom, "mKeyboardActionListener"))
        assertSame(host, staticField(PointerTracker::class.java, "sListener"))
    }

    @Test
    fun cancelAlsoReturnsWhenEmojiWasShownWithoutAStateEvent() {
        // Hardware-keyboard toggling and keyboard reloads can show the palette directly.
        for (code in listOf(KeyCode.CUSTOM2, KeyCode.ALPHA)) {
            event(code)
            val original = main.keyboard!!
            val originalMode = stateMode
            switcher.setEmojiKeyboard()
            palettes.setKeyboardActionListener(host)
            openSearch()
            closeSearch()
            assertEquals(View.GONE, palettes.visibility)
            assertEquals(View.VISIBLE, main.visibility)
            assertEquals(original.mId, main.keyboard!!.mId)
            assertEquals(originalMode, stateMode)
            assertSame(main, staticField(PointerTracker::class.java, "sDrawingProxy"))
        }
    }

    @Test
    fun symbolsAndShiftReturnToTheSearchCustomSlot() {
        event(KeyCode.CUSTOM5)
        val original = main.keyboard!!
        event(KeyCode.SYMBOL)
        event(KeyCode.EMOJI)
        palettes.setKeyboardActionListener(host)
        openSearch()
        assertEquals(keyRows(original), keyRows(bottom.keyboard!!))
        code(KeyCode.SHIFT)
        assertEquals(KeyboardId.ELEMENT_CUSTOM5, bottom.keyboard!!.mId.mElementId)
        code(KeyCode.SYMBOL_ALPHA)
        assertEquals(KeyboardId.ELEMENT_SYMBOLS, bottom.keyboard!!.mId.mElementId)
        assertTrue(bottom.keyboard!!.mId.mNumberRowInSymbols)
        assertTrue(bottom.keyboard!!.mId.mCompactNumberRowInSymbols)
        code(KeyCode.SHIFT)
        assertEquals(KeyboardId.ELEMENT_SYMBOLS_SHIFTED, bottom.keyboard!!.mId.mElementId)
        code(KeyCode.SHIFT)
        assertEquals(KeyboardId.ELEMENT_SYMBOLS, bottom.keyboard!!.mId.mElementId)
        code(KeyCode.SYMBOL_ALPHA)
        assertEquals(keyRows(original), keyRows(bottom.keyboard!!))
        code(KeyCode.LANGUAGE_SWITCH)
        customCodes.forEach { code(it) }
        assertEquals(KeyboardId.ELEMENT_CUSTOM5, bottom.keyboard!!.mId.mElementId)
        assertEquals("EMOJI", stateMode)
        Mockito.verifyNoInteractions(host)
        closeSearch()
        assertEquals("CUSTOM5", stateMode)
        assertEquals(original.mId, main.keyboard!!.mId)
    }

    @Test
    fun mainSearchStillSupportsShiftAndSymbols() {
        event(KeyCode.EMOJI)
        palettes.setKeyboardActionListener(host)
        openSearch()
        code(KeyCode.SHIFT)
        assertEquals(KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED, bottom.keyboard!!.mId.mElementId)
        code('A'.code)
        code(KeyCode.SHIFT)
        assertEquals(KeyboardId.ELEMENT_ALPHABET, bottom.keyboard!!.mId.mElementId)
        code(KeyCode.SYMBOL)
        code('1'.code)
        code(KeyCode.SYMBOL_ALPHA)
        assertEquals(KeyboardId.ELEMENT_ALPHABET, bottom.keyboard!!.mId.mElementId)
        assertEquals("A1", searchBar.text.toString())
        Mockito.verifyNoInteractions(host)
        closeSearch()
        assertEquals("ALPHABET", stateMode)
    }

    @Test
    fun hideCleansUpWithoutChangingPanelsOrState() {
        event(KeyCode.CUSTOM2)
        event(KeyCode.EMOJI)
        palettes.setKeyboardActionListener(host)
        openSearch()
        val query = searchBar
        code('x'.code)
        palettes.visibility = View.GONE
        assertEquals("", query.text.toString())
        assertFalse(field(palettes, "mInSearchMode") as Boolean)
        assertEquals("EMOJI", stateMode)
        assertEquals(View.GONE, main.visibility)
        assertSame(host, field(bottom, "mKeyboardActionListener"))
        assertSame(host, staticField(PointerTracker::class.java, "sListener"))
        event(KeyCode.EMOJI)
        palettes.setKeyboardActionListener(host)
        openSearch()
        assertEquals(KeyboardId.ELEMENT_CUSTOM2, bottom.keyboard!!.mId.mElementId)
    }

    @Test
    fun detachCleansUpWithoutShowingMainKeyboard() {
        event(KeyCode.CUSTOM4)
        event(KeyCode.EMOJI)
        palettes.setKeyboardActionListener(host)
        openSearch()
        root.removeView(palettes)
        assertFalse(palettes.isAttachedToWindow)
        assertFalse(field(palettes, "mInSearchMode") as Boolean)
        assertEquals("EMOJI", stateMode)
        assertEquals(View.GONE, main.visibility)
        assertSame(host, field(bottom, "mKeyboardActionListener"))
        assertSame(host, staticField(PointerTracker::class.java, "sListener"))
    }

    @Test
    fun switchingToClipboardDoesNotRestoreMainDuringCleanup() {
        event(KeyCode.CUSTOM2)
        event(KeyCode.EMOJI)
        palettes.setKeyboardActionListener(host)
        openSearch()
        assertTrue(Settings.getValues().mClipboardHistoryEnabled)
        event(KeyCode.CLIPBOARD)
        assertEquals("CLIPBOARD", stateMode)
        assertEquals(View.GONE, main.visibility)
        assertEquals(View.GONE, palettes.visibility)
        assertSame(host, staticField(PointerTracker::class.java, "sListener"))
    }

    @Test
    fun switchingToNumpadKeepsMainPointerTarget() {
        event(KeyCode.CUSTOM2)
        event(KeyCode.EMOJI)
        palettes.setKeyboardActionListener(host)
        openSearch()
        code(KeyCode.SYMBOL)
        event(KeyCode.NUMPAD)
        assertEquals("NUMPAD", stateMode)
        assertEquals(View.GONE, palettes.visibility)
        assertEquals(View.VISIBLE, main.visibility)
        assertSame(main, staticField(PointerTracker::class.java, "sDrawingProxy"))
        assertSame(host, staticField(PointerTracker::class.java, "sListener"))
        val key = main.keyboard!!.sortedKeys.first { it.code == '1'.code }
        val x = key.x + key.width / 2f + main.paddingLeft
        val y = key.y + key.height / 2f + main.paddingTop
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(0, 10, MotionEvent.ACTION_UP, x, y, 0)
        try {
            main.onTouchEvent(down)
            main.onTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
        Mockito.verify(host).onCodeInput(Mockito.eq('1'.code), Mockito.anyInt(), Mockito.anyInt(), Mockito.eq(false))
    }

    @Test
    fun searchResultCommitsOnlyEmojiAndKeepsSearchOpen() {
        event(KeyCode.CUSTOM1)
        event(KeyCode.EMOJI)
        palettes.setKeyboardActionListener(host)
        EmojiPalettesView::class.java.getDeclaredField("sDictionaryFacilitator")
            .apply { isAccessible = true }.set(null, Mockito.mock(SingleDictionaryFacilitator::class.java))
        openSearch()
        val adapter = field(palettes, "mSearchAdapter") as EmojiSearchAdapter
        val emoji = String(Character.toChars(0x1F600))
        adapter.submitList(listOf(emoji))
        val holder = adapter.onCreateViewHolder(RecyclerView(activity), 0)
        adapter.onBindViewHolder(holder, 0)
        assertTrue(holder.itemView.performClick())
        Mockito.verify(host).onTextInput(emoji)
        Mockito.verifyNoMoreInteractions(host)
        assertTrue(field(palettes, "mInSearchMode") as Boolean)
        assertEquals("EMOJI", stateMode)
        assertEquals(KeyboardId.ELEMENT_CUSTOM1, bottom.keyboard!!.mId.mElementId)
        closeSearch()
        assertEquals("CUSTOM1", stateMode)
        assertSame(host, staticField(PointerTracker::class.java, "sListener"))
    }

    @Test
    fun searchKeepsSplitAndOneHandedOptionsAndGeometry() {
        Settings.getInstance().writeSplitKeyboardEnabled(true, false)
        Settings.getInstance().writeOneHandedModeEnabled(true)
        ime.prefs().edit().putBoolean(Settings.PREF_SHOW_EMOJI_KEY, true).commit()
        KeyboardLayoutSet.onKeyboardThemeChanged()
        switcher.loadKeyboard(EditorInfo(), Settings.getValues(), 0, null, null)
        val original = main.keyboard!!
        assertTrue(original.mId.mIsSplitLayout)
        assertTrue(original.mId.mOneHandedModeEnabled)
        assertTrue(original.mId.mEmojiKeyEnabled)
        event(KeyCode.EMOJI)
        palettes.setKeyboardActionListener(host)
        openSearch()
        val search = bottom.keyboard!!
        assertEquals(original.mId.mWidth, search.mId.mWidth)
        assertEquals(original.mId.mIsSplitLayout, search.mId.mIsSplitLayout)
        assertEquals(original.mId.mOneHandedModeEnabled, search.mId.mOneHandedModeEnabled)
        assertEquals(original.mId.mEmojiKeyEnabled, search.mId.mEmojiKeyEnabled)
        assertEquals(keyRows(original), keyRows(search))
        closeSearch()
        assertEquals(original.mId, main.keyboard!!.mId)
    }

    @Test
    fun rtlSubtypeAndSearchCursorStayLocal() {
        val layouts = subtype.layouts.apply { put(LayoutType.MAIN, "arabic") }
        subtype = RichInputMethodSubtype.get(SubtypeUtilsAdditional.createAdditionalSubtype(
            Locale.forLanguageTag("ar"), "${Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET}=${layouts.toExtraValue()}", false, true
        ))
        RichInputMethodManager.forceSubtype(subtype.rawSubtype)
        switcher.loadKeyboard(EditorInfo(), Settings.getValues(), 0, null, null)
        event(KeyCode.CUSTOM4)
        val original = main.keyboard!!
        assertTrue(original.mId.mSubtype.isRtlSubtype)
        event(KeyCode.EMOJI)
        palettes.setKeyboardActionListener(host)
        openSearch()
        assertEquals(subtype, bottom.keyboard!!.mId.mSubtype)
        assertEquals(keyRows(original), keyRows(bottom.keyboard!!))
        searchListener.onTextInput("\u0642\u0637")
        searchBar.setSelection(2)
        assertTrue(searchListener.onHorizontalSpaceSwipe(1))
        assertEquals(1, searchBar.selectionStart)
        code(KeyCode.DELETE)
        assertEquals("\u0637", searchBar.text.toString())
        Mockito.verifyNoInteractions(host)
        closeSearch()
        assertEquals(original.mId, main.keyboard!!.mId)
        assertEquals("CUSTOM4", stateMode)
    }

    private fun code(code: Int) = searchListener.onCodeInput(code, 0, 0, false)

    private fun openSearch() {
        val magnifier = descendants(tabs).single { it.contentDescription == "Search Emojis" }
        assertTrue(magnifier.performClick())
        assertNotEquals(KeyboardId.ELEMENT_EMOJI_BOTTOM_ROW, bottom.keyboard!!.mId.mElementId)
    }

    private fun closeSearch() {
        assertTrue(descendants(tabs).filterIsInstance<ImageButton>().single().performClick())
    }

    private fun event(code: Int) {
        switcher.onEvent(Event.createSoftwareKeypressEvent(code, 0, 0, 0, false), 0, null)
    }

    private fun keyRows(keyboard: Keyboard) = keyboard.sortedKeys
        .groupBy { it.y }.toSortedMap().values.map { row -> row.sortedBy { it.x }.map { it.code to it.label } }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun field(target: Any, name: String): Any? =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)

    private fun setField(target: Any, name: String, value: Any?) {
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
    }

    private fun staticField(type: Class<*>, name: String): Any? =
        type.getDeclaredField(name).apply { isAccessible = true }.get(null)

    companion object {
        private val customTypes = listOf(LayoutType.CUSTOM1, LayoutType.CUSTOM2, LayoutType.CUSTOM3, LayoutType.CUSTOM4, LayoutType.CUSTOM5)
        private val customCodes = listOf(KeyCode.CUSTOM1, KeyCode.CUSTOM2, KeyCode.CUSTOM3, KeyCode.CUSTOM4, KeyCode.CUSTOM5)
        private val customElements = listOf(KeyboardId.ELEMENT_CUSTOM1, KeyboardId.ELEMENT_CUSTOM2, KeyboardId.ELEMENT_CUSTOM3, KeyboardId.ELEMENT_CUSTOM4, KeyboardId.ELEMENT_CUSTOM5)
    }
}
