// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import android.content.SharedPreferences
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.content.edit
import android.view.View
import android.view.MotionEvent
import android.os.Handler
import android.os.Looper
import android.annotation.SuppressLint
import androidx.core.view.forEach
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Constants.Separators
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ToolbarKey.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.EnumMap
import java.util.Locale
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.PixelFormat
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.content.res.ColorStateList

// Process-wide scope used for fire-and-forget tasks triggered by
// SharedPreferences listeners (which don't carry a coroutine scope).
// SupervisorJob prevents a single failure from cancelling unrelated
// preference-driven updates, and the exception handler keeps crashes
// from surfacing as silent uncaught exceptions in the default
// handler. UI mutations still hop to Dispatchers.Main explicitly.
private val toolbarPrefScope = CoroutineScope(SupervisorJob() + Dispatchers.Default +
    CoroutineExceptionHandler { _, throwable ->
        android.util.Log.w("ToolbarUtils", "preference update failed", throwable)
    })

fun createToolbarKey(context: Context, key: ToolbarKey): ImageButton {
    val button = ImageButton(context, null, R.attr.suggestionWordStyle)
    button.scaleType = ImageView.ScaleType.CENTER_INSIDE
    val defaultStripHeight = context.resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height).toFloat()
    val stripHeight = ResourceUtils.getSuggestionsStripHeight(context.resources).toFloat()
    val effectiveScale = if (defaultStripHeight > 0f) stripHeight / defaultStripHeight else 1.0f
    val padding = (9 * effectiveScale).toInt().dpToPx(context.resources).coerceAtLeast(2)
    button.setPadding(padding, padding, padding, padding)
    button.tag = key
    button.contentDescription = key.name.lowercase().getStringResourceOrName("", context)
    button.setBackgroundResource(R.drawable.toolbar_key_background)
    
    val index = if (key.name.startsWith("CUSTOM_AI_")) {
        key.name.removePrefix("CUSTOM_AI_").toIntOrNull()
    } else null
    
    val showTags = context.prefs().getBoolean("pref_custom_ai_show_tags_on_toolbar", false)
    val tag = if (index != null) {
        context.prefs().getString("pref_custom_ai_tag_$index", "") ?: ""
    } else ""
    
    val rawDrawable = if (showTags && tag.isNotBlank()) {
        TagDrawable(tag.take(3).uppercase(Locale.US))
    } else {
        KeyboardIconsSet.instance.getNewDrawable(key.name, context)
    }

    val showLongPressHint = context.prefs()
        .getBoolean(Settings.PREF_TOOLBAR_LONG_PRESS_HINT, Defaults.PREF_TOOLBAR_LONG_PRESS_HINT)
    val finalDrawable = if (rawDrawable != null && showLongPressHint
        && getCodeForToolbarKeyLongClick(key) != KeyCode.UNSPECIFIED
    ) {
        LongPressHintDrawable(rawDrawable)
    } else {
        rawDrawable
    }
    button.setImageDrawable(finalDrawable)
    setToolbarButtonActivatedState(button)
    return button
}

class TagDrawable(private val text: String) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private var tintList: ColorStateList? = null
    private var tintMode: PorterDuff.Mode = PorterDuff.Mode.MULTIPLY
    private var tintFilter: ColorFilter? = null
    private var internalColorFilter: ColorFilter? = null

    override fun setTintList(tint: ColorStateList?) {
        tintList = tint
        updateTintFilter()
        invalidateSelf()
    }

    override fun setTintMode(tintMode: PorterDuff.Mode?) {
        this.tintMode = tintMode ?: PorterDuff.Mode.MULTIPLY
        updateTintFilter()
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        internalColorFilter = colorFilter
        updateTintFilter()
        invalidateSelf()
    }

    override fun onStateChange(state: IntArray): Boolean {
        if (tintList != null) {
            updateTintFilter()
            invalidateSelf()
            return true
        }
        return super.onStateChange(state)
    }

    override fun isStateful(): Boolean {
        return tintList?.isStateful == true || super.isStateful()
    }

    private fun updateTintFilter() {
        val colors = tintList
        if (colors != null) {
            val color = colors.getColorForState(state, Color.WHITE)
            tintFilter = PorterDuffColorFilter(color, tintMode)
        } else {
            tintFilter = null
        }
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()

        // Apply theme's active color/tint filter dynamically to text paint
        val activeFilter = tintFilter ?: internalColorFilter
        paint.colorFilter = activeFilter

        // Scaled text size based on height
        paint.textSize = bounds.height() * 0.37f
        
        // Draw centered text
        val textHeight = paint.descent() - paint.ascent()
        val textOffset = textHeight / 2 - paint.descent()
        canvas.drawText(text, cx, cy + textOffset, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

fun setToolbarButtonsActivatedStateOnPrefChange(buttonsGroup: ViewGroup, key: String?) {
    // settings need to be updated when buttons change
    if (key != Settings.PREF_AUTO_CORRECTION
        && key != Settings.PREF_ALWAYS_INCOGNITO_MODE
        && key?.startsWith(Settings.PREF_ONE_HANDED_MODE_PREFIX) == false)
        return

    // Use a process-wide scope with a SupervisorJob and exception handler.
    // The previous code used GlobalScope, which is uncancellable and
    // doesn't have a structured way to handle errors. The buttonsGroup
    // can be detached if the IME is torn down quickly, so we also need
    // to use the main thread.
    toolbarPrefScope.launch {
        delay(10) // need to wait until SettingsValues are reloaded
        withContext(Dispatchers.Main) {
            buttonsGroup.forEach { if (it is ImageButton) setToolbarButtonActivatedState(it) }
        }
    }
}

fun setToolbarButtonActivatedState(button: ImageButton) {
    val activated = when (button.tag) {
        INCOGNITO -> button.context.prefs().getBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, Defaults.PREF_ALWAYS_INCOGNITO_MODE)
        ONE_HANDED -> Settings.getValues().mOneHandedModeEnabled
        SPLIT -> Settings.getValues().mIsSplitKeyboardEnabled
        AUTOCORRECT -> Settings.getValues().mAutoCorrectionEnabledPerUserSettings
        SELECT_MODE -> helium314.keyboard.keyboard.KeyboardActionListenerImpl.sPersistentSelectionModeActive
        else -> true
    }
    button.isActivated = activated
    val colors = Settings.getValues().mColors
    if (activated && button.tag in listOf(INCOGNITO, ONE_HANDED, SPLIT, AUTOCORRECT, SELECT_MODE)) {
        colors.setColor(button.background, ColorType.TOOL_BAR_KEY_ENABLED_BACKGROUND)
        if (button.drawable != null) {
            colors.setColor(button, ColorType.ACTION_KEY_ICON)
        }
    } else {
        colors.setColor(button.background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)
        if (button.drawable != null) {
            colors.setColor(button, ColorType.TOOL_BAR_KEY)
        }
    }
}

fun getCodeForToolbarKey(key: ToolbarKey) = Settings.getInstance().getCustomToolbarKeyCode(key) ?: when (key) {
    VOICE -> KeyCode.VOICE_INPUT
    CLIPBOARD -> KeyCode.CLIPBOARD
    CLIPBOARD_SEARCH -> KeyCode.CLIPBOARD_SEARCH
    NUMPAD -> KeyCode.NUMPAD
    HANDWRITING -> KeyCode.HANDWRITING
    UNDO -> KeyCode.UNDO
    REDO -> KeyCode.REDO
    SETTINGS -> KeyCode.SETTINGS
    SELECT_ALL -> KeyCode.CLIPBOARD_SELECT_ALL
    SELECT_WORD -> KeyCode.CLIPBOARD_SELECT_WORD
    COPY -> KeyCode.CLIPBOARD_COPY
    CUT -> KeyCode.CLIPBOARD_CUT
    PASTE -> KeyCode.CLIPBOARD_PASTE
    ONE_HANDED -> KeyCode.TOGGLE_ONE_HANDED_MODE
    FLOATING -> KeyCode.TOGGLE_FLOATING_KEYBOARD
    INCOGNITO -> KeyCode.TOGGLE_INCOGNITO_MODE
    TOUCHPAD -> KeyCode.TOGGLE_TOUCHPAD_MODE
    TEXT_EDIT -> KeyCode.TOGGLE_TEXT_EDIT_MODE
    AUTOCORRECT -> KeyCode.TOGGLE_AUTOCORRECT
    CLEAR_CLIPBOARD -> KeyCode.CLIPBOARD_CLEAR_HISTORY
    CLOSE_HISTORY -> KeyCode.ALPHA
    EMOJI -> KeyCode.EMOJI
    LEFT -> KeyCode.ARROW_LEFT
    RIGHT -> KeyCode.ARROW_RIGHT
    UP -> KeyCode.ARROW_UP
    DOWN -> KeyCode.ARROW_DOWN
    WORD_LEFT -> KeyCode.WORD_LEFT
    WORD_RIGHT -> KeyCode.WORD_RIGHT
    PAGE_UP -> KeyCode.PAGE_UP
    PAGE_DOWN -> KeyCode.PAGE_DOWN
    FULL_LEFT -> KeyCode.MOVE_START_OF_LINE
    FULL_RIGHT -> KeyCode.MOVE_END_OF_LINE
    PAGE_START -> KeyCode.MOVE_START_OF_PAGE
    PAGE_END -> KeyCode.MOVE_END_OF_PAGE
    SPLIT -> KeyCode.SPLIT_LAYOUT
    PROOFREAD -> KeyCode.PROOFREAD
    TRANSLATE -> KeyCode.TRANSLATE
    OCR -> KeyCode.OCR
    SELECT_MODE -> KeyCode.TOGGLE_SELECTION_MODE
    CUSTOM_AI_1 -> KeyCode.CUSTOM_AI_1
    CUSTOM_AI_2 -> KeyCode.CUSTOM_AI_2
    CUSTOM_AI_3 -> KeyCode.CUSTOM_AI_3
    CUSTOM_AI_4 -> KeyCode.CUSTOM_AI_4
    CUSTOM_AI_5 -> KeyCode.CUSTOM_AI_5
    CUSTOM_AI_6 -> KeyCode.CUSTOM_AI_6
    CUSTOM_AI_7 -> KeyCode.CUSTOM_AI_7
    CUSTOM_AI_8 -> KeyCode.CUSTOM_AI_8
    CUSTOM_AI_9 -> KeyCode.CUSTOM_AI_9
    CUSTOM_AI_10 -> KeyCode.CUSTOM_AI_10
}

fun getCodeForToolbarKeyLongClick(key: ToolbarKey) = Settings.getInstance().getCustomToolbarLongpressCode(key) ?: when (key) {
    CLIPBOARD -> KeyCode.CLIPBOARD_PASTE
    UNDO -> KeyCode.REDO
    REDO -> KeyCode.UNDO
    SELECT_ALL -> KeyCode.CLIPBOARD_SELECT_WORD
    SELECT_WORD -> KeyCode.CLIPBOARD_SELECT_ALL
    COPY -> KeyCode.CLIPBOARD_CUT
    PASTE -> KeyCode.CLIPBOARD
    LEFT -> KeyCode.WORD_LEFT
    RIGHT -> KeyCode.WORD_RIGHT
    UP -> KeyCode.PAGE_UP
    DOWN -> KeyCode.PAGE_DOWN
    WORD_LEFT -> KeyCode.MOVE_START_OF_LINE
    WORD_RIGHT -> KeyCode.MOVE_END_OF_LINE
    PAGE_UP -> KeyCode.MOVE_START_OF_PAGE
    PAGE_DOWN -> KeyCode.MOVE_END_OF_PAGE
    TRANSLATE -> KeyCode.SHOW_TRANSLATE_LANGUAGES
    else -> KeyCode.UNSPECIFIED
}

// names need to be aligned with resources strings (using lowercase of key.name)
enum class ToolbarKey {
    VOICE, CLIPBOARD, CLIPBOARD_SEARCH, NUMPAD, HANDWRITING, UNDO, REDO, SETTINGS, SELECT_ALL, SELECT_WORD, COPY, CUT, PASTE, ONE_HANDED, SPLIT, FLOATING,
    INCOGNITO, TOUCHPAD, TEXT_EDIT, AUTOCORRECT, CLEAR_CLIPBOARD, CLOSE_HISTORY, EMOJI, LEFT, RIGHT, UP, DOWN, WORD_LEFT, WORD_RIGHT,
    PAGE_UP, PAGE_DOWN, FULL_LEFT, FULL_RIGHT, PAGE_START, PAGE_END, PROOFREAD, TRANSLATE, OCR, SELECT_MODE,
    CUSTOM_AI_1, CUSTOM_AI_2, CUSTOM_AI_3, CUSTOM_AI_4, CUSTOM_AI_5,
    CUSTOM_AI_6, CUSTOM_AI_7, CUSTOM_AI_8, CUSTOM_AI_9, CUSTOM_AI_10
}

enum class ToolbarMode {
    EXPANDABLE, TOOLBAR_KEYS, SUGGESTION_STRIP, HIDDEN,
}

val toolbarKeyStrings = entries.associateWithTo(EnumMap(ToolbarKey::class.java)) { it.toString().lowercase(Locale.US) }

private val flavorExcludedKeys by lazy {
    val customAiKeys = if (BuildConfig.FLAVOR != "standard" && BuildConfig.FLAVOR != "standardfull" && BuildConfig.FLAVOR != "offline")
        ToolbarKey.entries.filter { it.name.startsWith("CUSTOM_AI_") }
    else emptyList()
    val otherKeys = mutableListOf<ToolbarKey>()
    if (BuildConfig.FLAVOR == "offline" && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
        otherKeys.add(PROOFREAD)
        otherKeys.addAll(ToolbarKey.entries.filter { it.name.startsWith("CUSTOM_AI_") })
    }
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
        otherKeys.add(HANDWRITING)
        otherKeys.add(OCR)
    }
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
        otherKeys.add(TRANSLATE)
    }
    (customAiKeys + otherKeys).distinct()
}

private val mainToolbarExcludedKeys = listOf(CLOSE_HISTORY, CLIPBOARD_SEARCH)

private val excludedKeys by lazy {
    flavorExcludedKeys + mainToolbarExcludedKeys
}

val defaultToolbarPref by lazy {
    val default = when (helium314.keyboard.latin.BuildConfig.FLAVOR) {
        "offline" -> listOf(SETTINGS, VOICE, CLIPBOARD, HANDWRITING, OCR, CUSTOM_AI_1, CUSTOM_AI_2, CUSTOM_AI_3, UNDO, INCOGNITO, COPY, PASTE, PROOFREAD, TRANSLATE, TEXT_EDIT)
        else -> listOf(SETTINGS, VOICE, CLIPBOARD, HANDWRITING, OCR, CUSTOM_AI_1, CUSTOM_AI_2, CUSTOM_AI_3, UNDO, PROOFREAD, TRANSLATE, INCOGNITO, TOUCHPAD, TEXT_EDIT, FLOATING, NUMPAD, COPY, PASTE, SELECT_ALL, SELECT_MODE)
    }
        
    val others = entries.filterNot { it in default || it in excludedKeys }
    default.joinToString(Separators.ENTRY) { it.name + Separators.KV + true } + Separators.ENTRY +
            others.joinToString(Separators.ENTRY) { it.name + Separators.KV + false }
}

val defaultPinnedToolbarPref by lazy {
    val pinnedDefault = listOf(CLIPBOARD, PROOFREAD, TOUCHPAD, TEXT_EDIT, FLOATING)

    entries.filterNot { it in excludedKeys }.joinToString(Separators.ENTRY) {
        it.name + Separators.KV + (it in pinnedDefault)
    }
}

val defaultClipboardToolbarPref by lazy {
    val default = listOf(CLIPBOARD_SEARCH, CLEAR_CLIPBOARD, SELECT_ALL, SELECT_WORD, COPY, CUT, PASTE, UNDO, REDO, SETTINGS, CLOSE_HISTORY)
    val others = entries.filterNot { it in default }
    default.joinToString(Separators.ENTRY) { it.name + Separators.KV + true } + Separators.ENTRY +
            others.joinToString(Separators.ENTRY) { it.name + Separators.KV + false }
}

/** add missing keys, typically because a new key has been added */
fun upgradeToolbarPrefs(prefs: SharedPreferences) {
    upgradeToolbarPref(prefs, Settings.PREF_TOOLBAR_KEYS, defaultToolbarPref)
    upgradeToolbarPref(prefs, Settings.PREF_PINNED_TOOLBAR_KEYS, defaultPinnedToolbarPref)
    upgradeToolbarPref(prefs, Settings.PREF_CLIPBOARD_TOOLBAR_KEYS, defaultClipboardToolbarPref)
}

private fun upgradeToolbarPref(prefs: SharedPreferences, pref: String, default: String) {
    if (!prefs.contains(pref)) return
    val originalString = prefs.getString(pref, default) ?: default
    val list = originalString.split(Separators.ENTRY).toMutableList()
    val splitDefault = default.split(Separators.ENTRY)
    splitDefault.forEach { entry ->
        val keyWithSeparator = entry.substringBefore(Separators.KV) + Separators.KV
        if (list.none { it.startsWith(keyWithSeparator) })
            list.add(entry)
    }
    // likely not needed, but better prepare for possibility of key removal
    list.removeAll {
        try {
            ToolbarKey.valueOf(it.substringBefore(Separators.KV))
            false
        } catch (_: IllegalArgumentException) {
            true
        }
    }
    val newString = list.joinToString(Separators.ENTRY)
    if (newString != originalString) {
        prefs.edit { putString(pref, newString) }
    }
}

fun getEnabledToolbarKeys(prefs: SharedPreferences) = getEnabledToolbarKeys(prefs, Settings.PREF_TOOLBAR_KEYS, defaultToolbarPref)

fun getPinnedToolbarKeys(prefs: SharedPreferences) = getEnabledToolbarKeys(prefs, Settings.PREF_PINNED_TOOLBAR_KEYS, defaultPinnedToolbarPref)

fun getEnabledClipboardToolbarKeys(prefs: SharedPreferences) = getEnabledToolbarKeys(prefs, Settings.PREF_CLIPBOARD_TOOLBAR_KEYS, defaultClipboardToolbarPref, flavorExcludedKeys)

fun addPinnedKey(prefs: SharedPreferences, key: ToolbarKey) {
    // remove the existing version of this key and add the enabled one after the last currently enabled key
    val string = prefs.getString(Settings.PREF_PINNED_TOOLBAR_KEYS, defaultPinnedToolbarPref) ?: defaultPinnedToolbarPref
    val keys = string.split(Separators.ENTRY).toMutableList()
    keys.removeAll { it.startsWith(key.name + Separators.KV) }
    val lastEnabledIndex = keys.indexOfLast { it.endsWith("true") }
    keys.add(lastEnabledIndex + 1, key.name + Separators.KV + "true")
    prefs.edit { putString(Settings.PREF_PINNED_TOOLBAR_KEYS, keys.joinToString(Separators.ENTRY)) }
}

fun removePinnedKey(prefs: SharedPreferences, key: ToolbarKey) {
    // just set it to disabled
    val string = prefs.getString(Settings.PREF_PINNED_TOOLBAR_KEYS, defaultPinnedToolbarPref) ?: defaultPinnedToolbarPref
    val result = string.split(Separators.ENTRY).joinToString(Separators.ENTRY) {
        if (it.startsWith(key.name + Separators.KV))
            key.name + Separators.KV + "false"
        else it
    }
    prefs.edit { putString(Settings.PREF_PINNED_TOOLBAR_KEYS, result) }
}

private fun getEnabledToolbarKeys(prefs: SharedPreferences, pref: String, default: String, exclusions: Collection<ToolbarKey> = excludedKeys): List<ToolbarKey> {
    val string = prefs.getString(pref, default) ?: default
    return string.split(Separators.ENTRY).mapNotNull {
        val split = it.split(Separators.KV)
        if (split.last() == "true") {
            try {
                val key = ToolbarKey.valueOf(split.first())
                if (key in exclusions) null else key
            } catch (_: IllegalArgumentException) {
                null
            }
        } else null
    }
}

fun writeCustomKeyCodes(prefs: SharedPreferences, codes: EnumMap<ToolbarKey, Pair<Int?, Int?>>) {
    val string = codes.mapNotNull { entry -> entry.value?.let { "${entry.key.name},${it.first},${it.second}" } }.joinToString(";")
    prefs.edit { putString(Settings.PREF_TOOLBAR_CUSTOM_KEY_CODES, string) }
}

fun readCustomKeyCodes(prefs: SharedPreferences): EnumMap<ToolbarKey, Pair<Int?, Int?>> {
    val map = EnumMap<ToolbarKey, Pair<Int?, Int?>>(ToolbarKey::class.java)
    (prefs.getString(Settings.PREF_TOOLBAR_CUSTOM_KEY_CODES, Defaults.PREF_TOOLBAR_CUSTOM_KEY_CODES) ?: Defaults.PREF_TOOLBAR_CUSTOM_KEY_CODES)
        .split(";").forEach {
            runCatching {
                val s = it.split(",")
                map[ToolbarKey.valueOf(s[0])] = s[1].toIntOrNull() to s[2].toIntOrNull()
            }
        }
    return map
}

fun getCustomKeyCode(key: ToolbarKey, prefs: SharedPreferences): Int? {
    val codes = customToolbarKeyCodes ?: readCustomKeyCodes(prefs).also { customToolbarKeyCodes = it }
    return codes[key]?.first
}

fun getCustomLongpressKeyCode(key: ToolbarKey, prefs: SharedPreferences): Int? {
    val codes = customToolbarKeyCodes ?: readCustomKeyCodes(prefs).also { customToolbarKeyCodes = it }
    return codes[key]?.second
}

fun clearCustomToolbarKeyCodes() {
    customToolbarKeyCodes = null
}

private var customToolbarKeyCodes: EnumMap<ToolbarKey, Pair<Int?, Int?>>? = null

fun isRepeatableToolbarKey(key: ToolbarKey): Boolean {
    return when (key) {
        LEFT, RIGHT, UP, DOWN,
        WORD_LEFT, WORD_RIGHT,
        PAGE_UP, PAGE_DOWN -> true
        else -> false
    }
}

class RepeatableKeyTouchListener(
    private val onClick: (repeatCount: Int) -> Unit
) : View.OnTouchListener {
    private val handler = Handler(Looper.getMainLooper())
    private var repeatCount = 0
    private val runnable = object : Runnable {
        override fun run() {
            repeatCount++
            onClick(repeatCount)
            handler.postDelayed(this, 50L)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                repeatCount = 0
                onClick(0)
                handler.postDelayed(runnable, 400L)
                v.isPressed = true
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(runnable)
                v.isPressed = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val x = event.x
                val y = event.y
                if (x < 0 || x > v.width || y < 0 || y > v.height) {
                    handler.removeCallbacks(runnable)
                    v.isPressed = false
                }
                return true
            }
        }
        return false
    }
}

class LongPressHintDrawable(private val base: Drawable) : Drawable() {
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    init {
        bounds = base.bounds
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val h = bounds.height().toFloat()
        val w = bounds.width().toFloat()
        if (h <= 0f || w <= 0f) return

        val shiftY = (h * 0.08f).coerceAtLeast(2f).toInt()
        base.setBounds(bounds.left, bounds.top - shiftY, bounds.right, bounds.bottom - shiftY)
        base.draw(canvas)
        base.bounds = bounds

        val pillWidth = (w * 0.28f).coerceIn(10f, 32f)
        val pillHeight = (h * 0.07f).coerceIn(3f, 7f)
        val cornerRadius = pillHeight / 2f
        val cx = bounds.exactCenterX()
        val cy = bounds.bottom.toFloat() - pillHeight / 2f + 1f

        val left = cx - pillWidth / 2f
        val top = cy - pillHeight / 2f
        val right = cx + pillWidth / 2f
        val bottom = cy + pillHeight / 2f

        val baseColor = Settings.getValues().mColors.get(ColorType.CLIPBOARD_PIN)
        val alpha25 = (Color.alpha(baseColor) * 0.25f).toInt()
        hintPaint.color = (alpha25 shl 24) or (baseColor and 0x00FFFFFF)
        canvas.drawRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, hintPaint)
    }

    override fun onBoundsChange(bounds: Rect) {
        base.bounds = bounds
        super.onBoundsChange(bounds)
    }

    override fun setAlpha(alpha: Int) {
        base.alpha = alpha
        hintPaint.alpha = (alpha * 0.25f).toInt()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        base.colorFilter = colorFilter
    }

    override fun setTint(tintColor: Int) {
        base.setTint(tintColor)
    }

    override fun setTintList(tint: ColorStateList?) {
        base.setTintList(tint)
    }

    override fun setTintMode(tintMode: PorterDuff.Mode?) {
        base.setTintMode(tintMode)
    }

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.UNKNOWN", "android.graphics.PixelFormat"))
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = base.opacity

    override fun isStateful(): Boolean = base.isStateful

    override fun onStateChange(state: IntArray): Boolean {
        return base.setState(state)
    }

    override fun getIntrinsicWidth(): Int = base.intrinsicWidth
    override fun getIntrinsicHeight(): Int = base.intrinsicHeight
    override fun getMinimumWidth(): Int = base.minimumWidth
    override fun getMinimumHeight(): Int = base.minimumHeight
}
