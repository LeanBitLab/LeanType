/*
 * Copyright (C) 2010 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard

import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextUtils
import helium314.keyboard.keyboard.internal.KeyDrawParams
import helium314.keyboard.keyboard.internal.KeySpecParser
import helium314.keyboard.keyboard.internal.KeyVisualAttributes
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.PopupKeySpec
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.PopupSet
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.toolbarKeyStrings
import java.util.Arrays
import java.util.Locale

/**
 * Class for describing the position and characteristics of a single key in the
 * keyboard.
 */
open class Key : Comparable<Key> {
    /** The key code (unicode or custom code) that this key generates. */
    protected val mCode: Int
    open val code: Int get() = mCode

    /** Label to display */
    protected val mLabel: String?
    open val label: String? get() = mLabel

    /** Hint label to display on the key in conjunction with the label */
    protected val mHintLabel: String?
    open val hintLabel: String? get() = mHintLabel

    /** Flags of the label */
    internal val mLabelFlags: Int

    /** Icon to display instead of a label. Icon takes precedence over a label */
    protected val mIconName: String?
    open val iconName: String? get() = mIconName

    /** Width of the key, excluding the gap */
    protected val mWidth: Int
    open val width: Int get() = mWidth

    /** Height of the key, excluding the gap */
    protected val mHeight: Int
    open val height: Int get() = mHeight

    /**
     * The combined width in pixels of the horizontal gaps belonging to this key,
     * both to the left and to the right. I.e., width + horizontalGap = total width belonging to the key.
     */
    protected val mHorizontalGap: Int
    open val horizontalGap: Int get() = mHorizontalGap

    /**
     * The combined height in pixels of the vertical gaps belonging to this key,
     * both above and below. I.e., height + verticalGap = total height belonging to the key.
     */
    protected val mVerticalGap: Int
    open val verticalGap: Int get() = mVerticalGap

    /**
     * X coordinate of the top-left corner of the key in the keyboard layout,
     * excluding the gap.
     */
    protected val mX: Int
    open val x: Int get() = mX

    /**
     * Y coordinate of the top-left corner of the key in the keyboard layout,
     * excluding the gap.
     */
    protected val mY: Int
    open val y: Int get() = mY

    /** Hit bounding box of the key */
    val hitBox: Rect = Rect()

    /**
     * Popup keys. It is guaranteed that this is null or an array of one or more
     * elements
     */
    protected val mPopupKeys: Array<PopupKeySpec>?
    open val popupKeys: Array<PopupKeySpec>? get() = mPopupKeys

    /** Popup keys column number and flags */
    internal val mPopupKeysColumnAndFlags: Int

    /**
     * Background type that represents different key background visual than normal
     * one.
     */
    protected val mBackgroundType: Int
    open val backgroundType: Int get() = mBackgroundType

    internal val mActionFlags: Int

    val visualAttributes: KeyVisualAttributes?
        get() = mKeyVisualAttributes
    internal val mKeyVisualAttributes: KeyVisualAttributes?

    internal val mOptionalAttributes: OptionalAttributes?

    internal class OptionalAttributes private constructor(
        val mOutputText: String?,
        val mAltCode: Int,
        val mDisabledIconName: String?,
        val mVisualInsetsLeft: Int,
        val mVisualInsetsRight: Int
    ) {
        companion object {
            fun newInstance(
                outputText: String?,
                altCode: Int,
                disabledIconName: String?,
                visualInsetsLeft: Int,
                visualInsetsRight: Int
            ): OptionalAttributes? {
                if (outputText == null && altCode == KeyCode.NOT_SPECIFIED &&
                    disabledIconName == null && visualInsetsLeft == 0 &&
                    visualInsetsRight == 0
                ) {
                    return null
                }
                return OptionalAttributes(outputText, altCode, disabledIconName, visualInsetsLeft, visualInsetsRight)
            }
        }
    }

    private val mHashCode: Int

    /** The current pressed state of this key */
    private var mPressed: Boolean = false

    /** Key is enabled and responds on press */
    protected var mEnabled: Boolean
    open var isEnabled: Boolean
        get() = mEnabled
        set(value) { mEnabled = value }

    /** Key is locked (appears permanently pressed) */
    open var isLocked: Boolean = false

    /**
     * Constructor for a key on `PopupKeyKeyboard` and on `MoreSuggestions`.
     */
    constructor(
        label: String?,
        iconName: String?,
        code: Int,
        outputText: String?,
        hintLabel: String?,
        labelFlags: Int,
        backgroundType: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        horizontalGap: Int,
        verticalGap: Int
    ) {
        mWidth = width - horizontalGap
        mHeight = height - verticalGap
        mHorizontalGap = horizontalGap
        mVerticalGap = verticalGap
        mHintLabel = hintLabel
        mLabelFlags = labelFlags
        mBackgroundType = backgroundType
        mActionFlags = ACTION_FLAGS_NO_KEY_PREVIEW
        mPopupKeys = null
        mPopupKeysColumnAndFlags = 0
        mLabel = label
        mCode = code
        mEnabled = code != KeyCode.NOT_SPECIFIED
        mIconName = iconName
        mOptionalAttributes = OptionalAttributes.newInstance(
            outputText,
            KeyCode.NOT_SPECIFIED,
            if (iconName == null) null else getDisabledIconName(iconName),
            0,
            0
        )
        mX = x + horizontalGap / 2
        mY = y
        hitBox.set(x, y, x + width + 1, y + height)
        mKeyVisualAttributes = null
        mHashCode = computeHashCode(this)
    }

    /**
     * Copy constructor for DynamicGridKeyboard.GridKey.
     */
    protected constructor(
        key: Key,
        popupKeys: Array<PopupKeySpec>?,
        labelHint: String?,
        backgroundType: Int
    ) {
        mCode = key.code
        mLabel = key.label
        mHintLabel = labelHint
        mLabelFlags = key.mLabelFlags
        mIconName = key.iconName
        mWidth = key.width
        mHeight = key.height
        mHorizontalGap = key.horizontalGap
        mVerticalGap = key.verticalGap
        mX = key.x
        mY = key.y
        hitBox.set(key.hitBox)
        mPopupKeys = popupKeys
        mPopupKeysColumnAndFlags = key.mPopupKeysColumnAndFlags
        mBackgroundType = backgroundType
        mActionFlags = key.mActionFlags
        mKeyVisualAttributes = key.visualAttributes
        mOptionalAttributes = key.mOptionalAttributes
        mHashCode = key.mHashCode
        mPressed = key.isPressed
        mEnabled = key.isEnabled
    }

    /**
     * Constructor for creating emoji recent keys when there is no keyboard to take keys from.
     */
    constructor(
        key: Key,
        popupKeys: Array<PopupKeySpec>?,
        labelHint: String?,
        backgroundType: Int,
        code: Int,
        outputText: String?
    ) {
        mCode = if (outputText == null) code else KeyCode.MULTIPLE_CODE_POINTS
        mLabel = if (outputText == null) StringUtils.newSingleCodePointString(code) else outputText
        mHintLabel = labelHint
        mLabelFlags = if (mLabel != null && isEmoticon(mLabel)) {
            LABEL_FLAGS_FONT_NORMAL or LABEL_FLAGS_AUTO_SCALE
        } else {
            key.mLabelFlags
        }
        mIconName = key.iconName
        mWidth = key.width
        mHeight = key.height
        mHorizontalGap = key.horizontalGap
        mVerticalGap = key.verticalGap
        mX = key.x
        mY = key.y
        hitBox.set(key.hitBox)
        mPopupKeys = popupKeys
        mPopupKeysColumnAndFlags = key.mPopupKeysColumnAndFlags
        mBackgroundType = backgroundType
        mActionFlags = key.mActionFlags
        mKeyVisualAttributes = key.visualAttributes
        mOptionalAttributes = if (outputText == null) null
        else OptionalAttributes.newInstance(outputText, KeyCode.NOT_SPECIFIED, null, 0, 0)
        mHashCode = key.mHashCode
        mPressed = key.isPressed
        mEnabled = key.isEnabled
    }

    /** Constructor from KeyParams */
    internal constructor(keyParams: KeyParams) {
        mCode = keyParams.mCode
        mLabel = keyParams.mLabel
        mHintLabel = keyParams.mHintLabel
        mLabelFlags = keyParams.mLabelFlags
        mIconName = keyParams.mIconName
        mPopupKeys = keyParams.mPopupKeys
        mPopupKeysColumnAndFlags = keyParams.mPopupKeysColumnAndFlags
        mBackgroundType = keyParams.mBackgroundType
        mActionFlags = keyParams.mActionFlags
        mKeyVisualAttributes = keyParams.mKeyVisualAttributes
        mOptionalAttributes = keyParams.mOptionalAttributes
        mEnabled = keyParams.mEnabled

        val horizontalGapFloat = if (isSpacer) 0f
        else (keyParams.mKeyboardParams.mRelativeHorizontalGap * keyParams.mKeyboardParams.mOccupiedWidth)
        mHorizontalGap = Math.round(horizontalGapFloat)
        mVerticalGap = Math.round(keyParams.mKeyboardParams.mRelativeVerticalGap * keyParams.mKeyboardParams.mOccupiedHeight)
        mWidth = Math.round(keyParams.mAbsoluteWidth - horizontalGapFloat)
        mHeight = (keyParams.mAbsoluteHeight - keyParams.mKeyboardParams.mVerticalGap).toInt()
        if (!isSpacer && (mWidth == 0 || mHeight == 0)) {
            throw IllegalStateException("key needs positive width and height")
        }
        mX = Math.round(keyParams.xPos + horizontalGapFloat / 2)
        mY = Math.round(keyParams.yPos)
        hitBox.set(
            Math.round(keyParams.xPos),
            Math.round(keyParams.yPos),
            Math.round(keyParams.xPos + keyParams.mAbsoluteWidth) + 1,
            Math.round(keyParams.yPos + keyParams.mAbsoluteHeight)
        )
        mHashCode = computeHashCode(this)
    }

    private constructor(key: Key, popupKeys: Array<PopupKeySpec>?) {
        mCode = key.code
        mLabel = key.label
        mHintLabel = findPopupHintLabel(popupKeys, key.hintLabel)
        mLabelFlags = key.mLabelFlags
        mIconName = key.iconName
        mWidth = key.width
        mHeight = key.height
        mHorizontalGap = key.horizontalGap
        mVerticalGap = key.verticalGap
        mX = key.x
        mY = key.y
        hitBox.set(key.hitBox)
        mPopupKeys = popupKeys
        mPopupKeysColumnAndFlags = key.mPopupKeysColumnAndFlags
        mBackgroundType = key.backgroundType
        mActionFlags = if (popupKeys == null && code > Constants.CODE_SPACE &&
            (key.mActionFlags and ACTION_FLAGS_ENABLE_LONG_PRESS) != 0
        ) {
            key.mActionFlags - ACTION_FLAGS_ENABLE_LONG_PRESS
        } else {
            key.mActionFlags
        }
        mKeyVisualAttributes = key.visualAttributes
        mOptionalAttributes = key.mOptionalAttributes
        mHashCode = key.mHashCode
        mPressed = key.isPressed
        mEnabled = key.isEnabled
    }

    private fun equalsInternal(o: Key): Boolean {
        if (this === o) return true
        return o.x == x &&
                o.y == y &&
                o.width == width &&
                o.height == height &&
                o.code == code &&
                TextUtils.equals(o.label, label) &&
                TextUtils.equals(o.hintLabel, hintLabel) &&
                TextUtils.equals(o.iconName, iconName) &&
                o.backgroundType == backgroundType &&
                o.popupKeys.contentEquals(popupKeys) &&
                TextUtils.equals(o.outputText, outputText) &&
                o.mActionFlags == mActionFlags &&
                o.mLabelFlags == mLabelFlags
    }

    override fun compareTo(other: Key): Int {
        if (equalsInternal(other)) return 0
        return if (mHashCode > other.mHashCode) 1 else -1
    }

    override fun hashCode(): Int = mHashCode

    override fun equals(other: Any?): Boolean =
        other is Key && equalsInternal(other)

    override fun toString(): String =
        "${toShortString()} $x,$y ${width}x$height"

    fun toShortString(): String {
        val code = this.code
        if (code == KeyCode.MULTIPLE_CODE_POINTS) {
            return outputText.orEmpty()
        }
        return Constants.printableCode(code)
    }

    fun toLongString(): String {
        val icon = iconName
        val topVisual = if (icon != null) KeyboardIconsSet.PREFIX_ICON + icon else label
        val hint = hintLabel
        val visual = if (hint == null) topVisual else "$topVisual^$hint"
        return "$this $visual/${backgroundName(backgroundType)}"
    }

    fun markAsLeftEdge(params: KeyboardParams) {
        hitBox.left = params.mLeftPadding
    }

    fun markAsRightEdge(params: KeyboardParams) {
        hitBox.right = params.mOccupiedWidth - params.mRightPadding
    }

    fun markAsTopEdge(params: KeyboardParams) {
        hitBox.top = params.mTopPadding
    }

    fun markAsBottomEdge(params: KeyboardParams) {
        hitBox.bottom = params.mOccupiedHeight + params.mBottomPadding
    }

    val isSpacer: Boolean
        get() = this is Spacer

    fun hasActionKeyBackground(): Boolean =
        backgroundType == BACKGROUND_TYPE_ACTION

    fun hasFunctionalBackground(): Boolean =
        backgroundType == BACKGROUND_TYPE_FUNCTIONAL

    fun isShift(): Boolean = code == KeyCode.SHIFT

    fun isModifier(): Boolean = with(KeyCode) { code.isModifier() }

    fun isRepeatable(): Boolean =
        (mActionFlags and ACTION_FLAGS_IS_REPEATABLE) != 0

    fun hasPreview(): Boolean =
        (mActionFlags and ACTION_FLAGS_NO_KEY_PREVIEW) == 0

    fun altCodeWhileTyping(): Boolean =
        (mActionFlags and ACTION_FLAGS_ALT_CODE_WHILE_TYPING) != 0

    val isLongPressEnabled: Boolean
        get() = (mActionFlags and ACTION_FLAGS_ENABLE_LONG_PRESS) != 0 &&
                (mLabelFlags and LABEL_FLAGS_SHIFTED_LETTER_ACTIVATED) == 0

    fun selectTypeface(params: KeyDrawParams): Typeface {
        return when (mLabelFlags and LABEL_FLAGS_FONT_MASK) {
            LABEL_FLAGS_FONT_NORMAL -> Typeface.DEFAULT
            LABEL_FLAGS_FONT_MONO_SPACE -> Typeface.MONOSPACE
            else -> params.mTypeface
        }
    }

    fun selectTextSize(params: KeyDrawParams): Int {
        return when (mLabelFlags and LABEL_FLAGS_FOLLOW_KEY_TEXT_RATIO_MASK) {
            LABEL_FLAGS_FOLLOW_KEY_LETTER_RATIO -> params.mLetterSize
            LABEL_FLAGS_FOLLOW_KEY_LARGE_LETTER_RATIO -> params.mLargeLetterSize
            LABEL_FLAGS_FOLLOW_KEY_LABEL_RATIO -> params.mLabelSize
            LABEL_FLAGS_FOLLOW_KEY_HINT_LABEL_RATIO -> params.mHintLabelSize
            else -> if (StringUtils.codePointCount(label) == 1) params.mLetterSize else params.mLabelSize
        }
    }

    fun selectTextColor(params: KeyDrawParams): Int {
        if ((mLabelFlags and LABEL_FLAGS_FOLLOW_FUNCTIONAL_TEXT_COLOR) != 0) {
            return params.mFunctionalTextColor
        }
        return if (isShiftedLetterActivated()) params.mTextInactivatedColor else params.mTextColor
    }

    fun selectHintTextSize(params: KeyDrawParams): Int {
        if (hasHintLabel()) {
            return params.mHintLabelSize
        }
        if (hasShiftedLetterHint()) {
            return params.mShiftedLetterHintSize
        }
        return params.mHintLetterSize
    }

    fun selectHintTextColor(params: KeyDrawParams): Int {
        if (hasHintLabel()) {
            return params.mHintLabelColor
        }
        if (hasShiftedLetterHint()) {
            return if (isShiftedLetterActivated()) params.mShiftedLetterHintActivatedColor
            else params.mShiftedLetterHintInactivatedColor
        }
        return params.mHintLetterColor
    }

    fun selectPopupKeyTextSize(params: KeyDrawParams): Int {
        return if (hasLabelsInPopupKeys()) params.mLabelSize else params.mLetterSize
    }

    val previewLabel: String?
        get() = if (isShiftedLetterActivated()) hintLabel else label

    private fun previewHasLetterSize(): Boolean {
        return (mLabelFlags and LABEL_FLAGS_FOLLOW_KEY_LETTER_RATIO) != 0
                || StringUtils.codePointCount(previewLabel) == 1
    }

    fun selectPreviewTextSize(params: KeyDrawParams): Int {
        if (previewHasLetterSize()) {
            return params.mPreviewTextSize
        }
        return params.mLetterSize
    }

    fun selectPreviewTypeface(params: KeyDrawParams): Typeface {
        if (previewHasLetterSize()) {
            return selectTypeface(params)
        }
        return Typeface.DEFAULT_BOLD
    }

    fun isAlignHintLabelToBottom(defaultFlags: Int): Boolean =
        ((mLabelFlags or defaultFlags) and LABEL_FLAGS_ALIGN_HINT_LABEL_TO_BOTTOM) != 0

    fun isAlignIconToBottom(): Boolean =
        (mLabelFlags and LABEL_FLAGS_ALIGN_ICON_TO_BOTTOM) != 0

    fun isAlignLabelOffCenter(): Boolean =
        (mLabelFlags and LABEL_FLAGS_ALIGN_LABEL_OFF_CENTER) != 0

    fun hasPopupHint(): Boolean =
        (mLabelFlags and LABEL_FLAGS_HAS_POPUP_HINT) != 0

    fun hasShiftedLetterHint(): Boolean =
        (mLabelFlags and LABEL_FLAGS_HAS_SHIFTED_LETTER_HINT) != 0 && !hintLabel.isNullOrEmpty()

    fun hasHintLabel(): Boolean =
        (mLabelFlags and LABEL_FLAGS_HAS_HINT_LABEL) != 0

    fun needsAutoXScale(): Boolean =
        (mLabelFlags and LABEL_FLAGS_AUTO_X_SCALE) != 0

    fun needsAutoScale(): Boolean =
        (mLabelFlags and LABEL_FLAGS_AUTO_SCALE) == LABEL_FLAGS_AUTO_SCALE

    fun needsToKeepBackgroundAspectRatio(defaultFlags: Int): Boolean =
        ((mLabelFlags or defaultFlags) and LABEL_FLAGS_KEEP_BACKGROUND_ASPECT_RATIO) != 0

    fun hasCustomActionLabel(): Boolean =
        (mLabelFlags and LABEL_FLAGS_FROM_CUSTOM_ACTION_LABEL) != 0

    private fun isShiftedLetterActivated(): Boolean =
        (mLabelFlags and LABEL_FLAGS_SHIFTED_LETTER_ACTIVATED) != 0 && !hintLabel.isNullOrEmpty()

    val popupKeysColumnNumber: Int
        get() = mPopupKeysColumnAndFlags and POPUP_KEYS_COLUMN_NUMBER_MASK

    val isPopupKeysFixedColumn: Boolean
        get() = (mPopupKeysColumnAndFlags and POPUP_KEYS_FLAGS_FIXED_COLUMN) != 0

    val isPopupKeysFixedOrder: Boolean
        get() = (mPopupKeysColumnAndFlags and POPUP_KEYS_FLAGS_FIXED_ORDER) != 0

    fun hasLabelsInPopupKeys(): Boolean =
        (mPopupKeysColumnAndFlags and POPUP_KEYS_FLAGS_HAS_LABELS) != 0

    val popupKeyLabelFlags: Int
        get() {
            val labelSizeFlag = if (hasLabelsInPopupKeys()) {
                LABEL_FLAGS_FOLLOW_KEY_LABEL_RATIO
            } else {
                LABEL_FLAGS_FOLLOW_KEY_LETTER_RATIO
            }
            return labelSizeFlag or LABEL_FLAGS_AUTO_X_SCALE
        }

    fun needsDividersInPopupKeys(): Boolean =
        (mPopupKeysColumnAndFlags and POPUP_KEYS_FLAGS_NEEDS_DIVIDERS) != 0

    fun hasNoPanelAutoPopupKey(): Boolean =
        (mPopupKeysColumnAndFlags and POPUP_KEYS_FLAGS_NO_PANEL_AUTO_POPUP_KEY) != 0

    val outputText: String?
        get() = mOptionalAttributes?.mOutputText

    val altCode: Int
        get() = mOptionalAttributes?.mAltCode ?: KeyCode.NOT_SPECIFIED

    open fun getIcon(iconSet: KeyboardIconsSet?, alpha: Int): Drawable? {
        val attrs = mOptionalAttributes
        val name = if (isEnabled) iconName else (attrs?.mDisabledIconName)
        val icon = iconSet?.getIconDrawable(name)
        icon?.alpha = alpha
        return icon
    }

    fun getPreviewIcon(iconSet: KeyboardIconsSet?): Drawable? =
        iconSet?.getIconDrawable(iconName)

    open val drawX: Int
        get() = mOptionalAttributes?.let { x + it.mVisualInsetsLeft } ?: x

    open val drawWidth: Int
        get() = mOptionalAttributes?.let { width - it.mVisualInsetsLeft - it.mVisualInsetsRight } ?: width

    fun onPressed() {
        mPressed = true
    }

    fun onReleased() {
        mPressed = false
    }

    val isPressed: Boolean
        get() = mPressed

    fun isOnKey(x: Int, y: Int): Boolean = hitBox.contains(x, y)

    fun squaredDistanceToEdge(x: Int, y: Int): Int {
        val left = this.x
        val right = left + width
        val top = this.y
        val bottom = top + height
        val edgeX = if (x < left) left else minOf(x, right)
        val edgeY = if (y < top) top else minOf(y, bottom)
        val dx = x - edgeX
        val dy = y - edgeY
        return dx * dx + dy * dy
    }

    private class KeyBackgroundState(vararg attrs: Int) {
        private val mReleasedState: IntArray = attrs
        private val mPressedState: IntArray = attrs.copyOf(attrs.size + 1).apply {
            this[attrs.size] = android.R.attr.state_pressed
        }

        fun getState(pressed: Boolean): IntArray = if (pressed) mPressedState else mReleasedState

        companion object {
            val STATES = arrayOf(
                // 0: BACKGROUND_TYPE_EMPTY
                KeyBackgroundState(android.R.attr.state_empty),
                // 1: BACKGROUND_TYPE_NORMAL
                KeyBackgroundState(),
                // 2: BACKGROUND_TYPE_FUNCTIONAL
                KeyBackgroundState(),
                // 3: BACKGROUND_TYPE_ACTION
                KeyBackgroundState(android.R.attr.state_active),
                // 4: BACKGROUND_TYPE_SPACEBAR
                KeyBackgroundState()
            )
        }
    }

    fun selectBackgroundDrawable(
        keyBackground: Drawable,
        functionalKeyBackground: Drawable,
        spacebarBackground: Drawable,
        actionKeyBackground: Drawable
    ): Drawable {
        val background = when {
            hasActionKeyBackground() -> actionKeyBackground
            hasFunctionalBackground() -> functionalKeyBackground
            backgroundType == BACKGROUND_TYPE_SPACEBAR -> spacebarBackground
            else -> keyBackground
        }
        val state = KeyBackgroundState.STATES[backgroundType].getState(mPressed || isLocked)
        background.state = state
        return background
    }

    fun hasActionKeyPopups(): Boolean {
        if (!hasActionKeyBackground()) return false
        val popups = popupKeys ?: return false
        return popups.none { it.mIconName == null }
    }

    open class Spacer : Key {
        internal constructor(keyParams: KeyParams) : super(keyParams)

        constructor(
            params: KeyboardParams,
            x: Int,
            y: Int,
            width: Int,
            height: Int
        ) : super(
            null, null, KeyCode.NOT_SPECIFIED, null,
            null, 0, BACKGROUND_TYPE_EMPTY, x, y, width,
            height, params.mHorizontalGap, params.mVerticalGap
        )
    }

    open class KeyParams {
        var isSpacer: Boolean = false
        internal val mKeyboardParams: KeyboardParams
        var mWidth: Float = 0f
        var mHeight: Float = 0f

        var mAbsoluteWidth: Float = 0f
        var mAbsoluteHeight: Float = 0f
        var xPos: Float = 0f
        var yPos: Float = 0f

        val mCode: Int
        val mLabel: String?
        val mHintLabel: String?
        val mLabelFlags: Int
        val mIconName: String?
        val mPopupKeys: Array<PopupKeySpec>?
        val mPopupKeysColumnAndFlags: Int
        val mBackgroundType: Int
        val mActionFlags: Int
        val mKeyVisualAttributes: KeyVisualAttributes?
        internal val mOptionalAttributes: OptionalAttributes?
        val mEnabled: Boolean

        fun createKey(): Key {
            if (isSpacer) return Spacer(this)
            return Key(this)
        }

        fun setAbsoluteDimensions(newX: Float, newY: Float) {
            if (mHeight == 0f) {
                mHeight = mKeyboardParams.mDefaultRowHeight.toFloat()
            }
            if (!isSpacer && mWidth == 0f) {
                throw IllegalStateException("width = 0 should have been evaluated already")
            }
            if (mHeight < 0f) {
                throw IllegalStateException("can't (yet) deal with absolute height")
            }
            xPos = newX
            yPos = newY
            mAbsoluteWidth = mWidth * mKeyboardParams.mBaseWidth
            mAbsoluteHeight = mHeight * mKeyboardParams.mBaseHeight
        }

        val outputText: String?
            get() = mOptionalAttributes?.mOutputText

        constructor(
            keySpec: String,
            params: KeyboardParams,
            relativeWidth: Float,
            labelFlags: Int,
            backgroundType: Int,
            popupSet: PopupSet<*>?
        ) : this(keySpec, KeySpecParser.getCode(keySpec), params, relativeWidth, labelFlags, backgroundType, popupSet)

        constructor(
            keySpec: String,
            code: Int,
            params: KeyboardParams,
            width: Float,
            labelFlags: Int,
            backgroundType: Int,
            popupSet: PopupSet<*>?
        ) {
            mKeyboardParams = params
            mBackgroundType = backgroundType
            mLabelFlags = labelFlags
            mWidth = width
            mHeight = params.mDefaultRowHeight.toFloat()
            mIconName = KeySpecParser.getIconName(keySpec)

            val needsToUpcase = needsToUpcase(mLabelFlags, params.mId.mElementId)
            val localeForUpcasing = params.mId.locale
            var actionFlags = 0
            if (params.mId.isNumberLayout) {
                actionFlags = ACTION_FLAGS_NO_KEY_PREVIEW
            }

            // label
            val label: String?
            if ((mLabelFlags and LABEL_FLAGS_FROM_CUSTOM_ACTION_LABEL) != 0) {
                mLabel = params.mId.mCustomActionLabel
                label = null
            } else if (code >= Character.MIN_SUPPLEMENTARY_CODE_POINT) {
                mLabel = StringBuilder().appendCodePoint(code).toString()
                label = null
            } else {
                label = KeySpecParser.getLabel(keySpec)
                mLabel = if (needsToUpcase) StringUtils.toTitleCaseOfKeyLabel(label, localeForUpcasing) else label
            }

            // popupKeys
            val popupKeys = helium314.keyboard.latin.utils.createPopupKeysArray(
                popupSet, mKeyboardParams,
                label ?: keySpec
            )
            mPopupKeysColumnAndFlags = getPopupKeysColumnAndFlagsAndSetNullInArray(params, popupKeys)
            val finalPopupKeys = if (popupKeys == null) null else PopupKeySpec.filterOutEmptyString(popupKeys)
            if (finalPopupKeys != null) {
                actionFlags = actionFlags or ACTION_FLAGS_ENABLE_LONG_PRESS
                mPopupKeys = Array(finalPopupKeys.size) { i ->
                    PopupKeySpec(finalPopupKeys[i], needsToUpcase, localeForUpcasing)
                }
            } else {
                mPopupKeys = null
            }

            // hint label
            if ((mLabelFlags and LABEL_FLAGS_DISABLE_HINT_LABEL) != 0) {
                mHintLabel = null
            } else {
                val hintLabel = helium314.keyboard.latin.utils.getHintLabel(popupSet, params, keySpec)
                mHintLabel = if (needsToUpcase) StringUtils.toTitleCaseOfKeyLabel(hintLabel, localeForUpcasing) else hintLabel
            }

            var outputText = KeySpecParser.getOutputText(keySpec, code)
            if (needsToUpcase) {
                outputText = StringUtils.toTitleCaseOfKeyLabel(outputText, localeForUpcasing)
            }

            var resolvedCode = code
            if (resolvedCode == KeyCode.NOT_SPECIFIED && TextUtils.isEmpty(outputText) && !TextUtils.isEmpty(mLabel)) {
                if (StringUtils.codePointCount(mLabel) == 1) {
                    if ((mLabelFlags and LABEL_FLAGS_HAS_SHIFTED_LETTER_HINT) != 0 &&
                        (mLabelFlags and LABEL_FLAGS_SHIFTED_LETTER_ACTIVATED) != 0 &&
                        !TextUtils.isEmpty(mHintLabel)
                    ) {
                        resolvedCode = mHintLabel!!.codePointAt(0)
                    } else {
                        resolvedCode = mLabel!!.codePointAt(0)
                    }
                } else {
                    outputText = mLabel
                    resolvedCode = KeyCode.MULTIPLE_CODE_POINTS
                }
            } else if (resolvedCode == KeyCode.NOT_SPECIFIED && outputText != null) {
                if (StringUtils.codePointCount(outputText) == 1) {
                    resolvedCode = outputText.codePointAt(0)
                    outputText = null
                } else {
                    resolvedCode = KeyCode.MULTIPLE_CODE_POINTS
                }
            } else {
                resolvedCode = if (needsToUpcase) StringUtils.toTitleCaseOfKeyCode(resolvedCode, localeForUpcasing) else resolvedCode
            }
            mCode = resolvedCode

            if (mCode == Constants.CODE_SPACE ||
                mCode == KeyCode.LANGUAGE_SWITCH ||
                mCode == KeyCode.CLEAR_HANDWRITING ||
                (mCode == KeyCode.SYMBOL_ALPHA && !params.mId.isAlphabetKeyboard)
            ) {
                actionFlags = actionFlags or ACTION_FLAGS_ENABLE_LONG_PRESS
            }
            if (mCode <= Constants.CODE_SPACE && mCode != KeyCode.MULTIPLE_CODE_POINTS && mIconName == null) {
                actionFlags = actionFlags or ACTION_FLAGS_NO_KEY_PREVIEW
            }
            when (mCode) {
                KeyCode.DELETE, KeyCode.ARROW_LEFT, KeyCode.ARROW_RIGHT, KeyCode.ARROW_UP, KeyCode.ARROW_DOWN,
                KeyCode.WORD_LEFT, KeyCode.WORD_RIGHT, KeyCode.PAGE_UP, KeyCode.PAGE_DOWN -> {
                    if (mPopupKeys == null) {
                        actionFlags = actionFlags or ACTION_FLAGS_IS_REPEATABLE
                    }
                    actionFlags = actionFlags or ACTION_FLAGS_NO_KEY_PREVIEW
                }
                KeyCode.SHIFT, Constants.CODE_ENTER, KeyCode.SHIFT_ENTER, KeyCode.ALPHA, Constants.CODE_SPACE,
                KeyCode.NUMPAD, KeyCode.SYMBOL, KeyCode.SYMBOL_ALPHA, KeyCode.LANGUAGE_SWITCH, KeyCode.EMOJI,
                KeyCode.CLIPBOARD, KeyCode.MOVE_START_OF_LINE, KeyCode.MOVE_END_OF_LINE,
                KeyCode.MOVE_START_OF_PAGE, KeyCode.MOVE_END_OF_PAGE -> {
                    actionFlags = actionFlags or ACTION_FLAGS_NO_KEY_PREVIEW
                }
            }
            if (mCode == KeyCode.SETTINGS || mCode == KeyCode.LANGUAGE_SWITCH) {
                actionFlags = actionFlags or ACTION_FLAGS_ALT_CODE_WHILE_TYPING
            }
            mActionFlags = actionFlags

            val altCodeInAttr = if (mCode == KeyCode.SETTINGS || mCode == KeyCode.LANGUAGE_SWITCH ||
                mCode == KeyCode.EMOJI || mCode == KeyCode.CLIPBOARD
            ) {
                Constants.CODE_SPACE
            } else {
                KeyCode.NOT_SPECIFIED
            }
            val altCode = if (needsToUpcase) StringUtils.toTitleCaseOfKeyCode(altCodeInAttr, localeForUpcasing) else altCodeInAttr
            mOptionalAttributes = OptionalAttributes.newInstance(
                outputText, altCode,
                if (mIconName == null) null else getDisabledIconName(mIconName),
                0, 0
            )
            mKeyVisualAttributes = null
            mEnabled = true
        }

        constructor(
            label: String?,
            code: Int,
            hintLabel: String?,
            popupKeySpecs: String?,
            labelFlags: Int,
            params: KeyboardParams
        ) {
            mKeyboardParams = params
            mHintLabel = hintLabel
            mLabelFlags = labelFlags
            mBackgroundType = BACKGROUND_TYPE_EMPTY

            if (popupKeySpecs != null) {
                var popupKeys = PopupKeySpec.splitKeySpecs(popupKeySpecs)
                mPopupKeysColumnAndFlags = getPopupKeysColumnAndFlagsAndSetNullInArray(params, popupKeys)

                popupKeys = PopupKeySpec.insertAdditionalPopupKeys(popupKeys, null)
                var actionFlags = 0
                if (popupKeys != null) {
                    actionFlags = actionFlags or ACTION_FLAGS_ENABLE_LONG_PRESS
                    mPopupKeys = Array(popupKeys.size) { i ->
                        PopupKeySpec(popupKeys[i], false, Locale.getDefault())
                    }
                } else {
                    mPopupKeys = null
                }
                mActionFlags = actionFlags
            } else {
                mActionFlags = ACTION_FLAGS_NO_KEY_PREVIEW
                mPopupKeys = null
                mPopupKeysColumnAndFlags = 0
            }

            mLabel = label
            mOptionalAttributes = if (code == KeyCode.MULTIPLE_CODE_POINTS) {
                OptionalAttributes.newInstance(label, KeyCode.NOT_SPECIFIED, null, 0, 0)
            } else {
                null
            }
            mCode = code
            mEnabled = code != KeyCode.NOT_SPECIFIED
            mIconName = null
            mKeyVisualAttributes = null
        }

        private constructor(params: KeyboardParams) {
            isSpacer = true
            mKeyboardParams = params

            mCode = KeyCode.NOT_SPECIFIED
            mLabel = null
            mHintLabel = null
            mKeyVisualAttributes = null
            mOptionalAttributes = null
            mIconName = null
            mBackgroundType = BACKGROUND_TYPE_NORMAL
            mActionFlags = ACTION_FLAGS_NO_KEY_PREVIEW
            mPopupKeys = null
            mPopupKeysColumnAndFlags = 0
            mLabelFlags = LABEL_FLAGS_FONT_NORMAL
            mEnabled = true
        }

        constructor(keyParams: KeyParams) {
            xPos = keyParams.xPos
            yPos = keyParams.yPos
            mWidth = keyParams.mWidth
            mHeight = keyParams.mHeight
            isSpacer = keyParams.isSpacer
            mKeyboardParams = keyParams.mKeyboardParams
            mEnabled = keyParams.mEnabled

            mCode = keyParams.mCode
            mLabel = keyParams.mLabel
            mHintLabel = keyParams.mHintLabel
            mLabelFlags = keyParams.mLabelFlags
            mIconName = keyParams.mIconName
            mAbsoluteWidth = keyParams.mAbsoluteWidth
            mAbsoluteHeight = keyParams.mAbsoluteHeight
            mPopupKeys = keyParams.mPopupKeys
            mPopupKeysColumnAndFlags = keyParams.mPopupKeysColumnAndFlags
            mBackgroundType = keyParams.mBackgroundType
            mActionFlags = keyParams.mActionFlags
            mKeyVisualAttributes = keyParams.mKeyVisualAttributes
            mOptionalAttributes = keyParams.mOptionalAttributes
        }

        companion object {
            @JvmStatic
            fun newSpacer(params: KeyboardParams, width: Float): KeyParams {
                val spacer = KeyParams(params)
                spacer.mWidth = width
                spacer.mHeight = params.mDefaultRowHeight.toFloat()
                return spacer
            }

            private fun getPopupKeysColumnAndFlagsAndSetNullInArray(
                params: KeyboardParams,
                popupKeys: Array<String>?
            ): Int {
                if (popupKeys == null) {
                    return POPUP_KEYS_MODE_MAX_COLUMN_WITH_AUTO_ORDER or params.mMaxPopupKeysKeyboardColumn
                }
                @Suppress("UNCHECKED_CAST")
                val nullableArray = popupKeys as Array<String?>
                var popupKeysColumnAndFlags = POPUP_KEYS_MODE_MAX_COLUMN_WITH_AUTO_ORDER or params.mMaxPopupKeysKeyboardColumn
                var value = PopupKeySpec.getIntValue(nullableArray, POPUP_KEYS_AUTO_ORDER, -1)
                if (value > 0) {
                    popupKeysColumnAndFlags = POPUP_KEYS_MODE_FIXED_COLUMN_WITH_AUTO_ORDER or
                            (value and POPUP_KEYS_COLUMN_NUMBER_MASK)
                }
                value = PopupKeySpec.getIntValue(nullableArray, POPUP_KEYS_FIXED_ORDER, -1)
                if (value > 0) {
                    popupKeysColumnAndFlags = POPUP_KEYS_MODE_FIXED_COLUMN_WITH_FIXED_ORDER or
                            (value and POPUP_KEYS_COLUMN_NUMBER_MASK)
                }
                if (PopupKeySpec.getBooleanValue(nullableArray, POPUP_KEYS_HAS_LABELS)) {
                    popupKeysColumnAndFlags = popupKeysColumnAndFlags or POPUP_KEYS_FLAGS_HAS_LABELS
                }
                if (PopupKeySpec.getBooleanValue(nullableArray, POPUP_KEYS_NEEDS_DIVIDERS)) {
                    popupKeysColumnAndFlags = popupKeysColumnAndFlags or POPUP_KEYS_FLAGS_NEEDS_DIVIDERS
                }
                if (PopupKeySpec.getBooleanValue(nullableArray, POPUP_KEYS_NO_PANEL_AUTO_POPUP_KEY)) {
                    popupKeysColumnAndFlags = popupKeysColumnAndFlags or POPUP_KEYS_FLAGS_NO_PANEL_AUTO_POPUP_KEY
                }
                return popupKeysColumnAndFlags
            }
        }
    }

    companion object {
        const val LABEL_FLAGS_ALIGN_HINT_LABEL_TO_BOTTOM = 0x02
        const val LABEL_FLAGS_ALIGN_ICON_TO_BOTTOM = 0x04
        const val LABEL_FLAGS_ALIGN_LABEL_OFF_CENTER = 0x08
        // Font typeface specification.
        private const val LABEL_FLAGS_FONT_MASK = 0x30
        const val LABEL_FLAGS_FONT_NORMAL = 0x10
        const val LABEL_FLAGS_FONT_MONO_SPACE = 0x20
        const val LABEL_FLAGS_FONT_DEFAULT = 0x30
        // Start of key text ratio enum values
        private const val LABEL_FLAGS_FOLLOW_KEY_TEXT_RATIO_MASK = 0x1C0
        const val LABEL_FLAGS_FOLLOW_KEY_LARGE_LETTER_RATIO = 0x40
        const val LABEL_FLAGS_FOLLOW_KEY_LETTER_RATIO = 0x80
        const val LABEL_FLAGS_FOLLOW_KEY_LABEL_RATIO = 0xC0
        const val LABEL_FLAGS_FOLLOW_KEY_HINT_LABEL_RATIO = 0x140
        // End of key text ratio mask enum values
        const val LABEL_FLAGS_HAS_POPUP_HINT = 0x200
        const val LABEL_FLAGS_HAS_SHIFTED_LETTER_HINT = 0x400
        const val LABEL_FLAGS_HAS_HINT_LABEL = 0x800
        const val LABEL_FLAGS_AUTO_X_SCALE = 0x4000
        const val LABEL_FLAGS_AUTO_Y_SCALE = 0x8000
        const val LABEL_FLAGS_AUTO_SCALE = LABEL_FLAGS_AUTO_X_SCALE or LABEL_FLAGS_AUTO_Y_SCALE
        const val LABEL_FLAGS_PRESERVE_CASE = 0x10000
        const val LABEL_FLAGS_SHIFTED_LETTER_ACTIVATED = 0x20000
        const val LABEL_FLAGS_FROM_CUSTOM_ACTION_LABEL = 0x40000
        const val LABEL_FLAGS_FOLLOW_FUNCTIONAL_TEXT_COLOR = 0x80000
        const val LABEL_FLAGS_KEEP_BACKGROUND_ASPECT_RATIO = 0x100000
        const val LABEL_FLAGS_DISABLE_HINT_LABEL = 0x40000000
        const val LABEL_FLAGS_DISABLE_ADDITIONAL_POPUP_KEYS = 0x80000000.toInt()

        private const val POPUP_KEYS_COLUMN_NUMBER_MASK = 0x000000ff
        private const val POPUP_KEYS_FLAGS_FIXED_COLUMN = 0x00000100
        private const val POPUP_KEYS_FLAGS_FIXED_ORDER = 0x00000200
        private const val POPUP_KEYS_MODE_MAX_COLUMN_WITH_AUTO_ORDER = 0
        private const val POPUP_KEYS_MODE_FIXED_COLUMN_WITH_AUTO_ORDER = POPUP_KEYS_FLAGS_FIXED_COLUMN
        private const val POPUP_KEYS_MODE_FIXED_COLUMN_WITH_FIXED_ORDER = (POPUP_KEYS_FLAGS_FIXED_COLUMN
                or POPUP_KEYS_FLAGS_FIXED_ORDER)
        private const val POPUP_KEYS_FLAGS_HAS_LABELS = 0x40000000
        private const val POPUP_KEYS_FLAGS_NEEDS_DIVIDERS = 0x20000000
        private const val POPUP_KEYS_FLAGS_NO_PANEL_AUTO_POPUP_KEY = 0x10000000
        const val POPUP_KEYS_AUTO_ORDER = "!autoOrder!"
        const val POPUP_KEYS_FIXED_ORDER = "!fixedOrder!"
        const val POPUP_KEYS_HAS_LABELS = "!hasLabels!"
        private const val POPUP_KEYS_NEEDS_DIVIDERS = "!needsDividers!"
        private const val POPUP_KEYS_NO_PANEL_AUTO_POPUP_KEY = "!noPanelAutoPopupKey!"

        const val BACKGROUND_TYPE_EMPTY = 0
        const val BACKGROUND_TYPE_NORMAL = 1
        const val BACKGROUND_TYPE_FUNCTIONAL = 2
        const val BACKGROUND_TYPE_ACTION = 3
        const val BACKGROUND_TYPE_SPACEBAR = 4

        private const val ACTION_FLAGS_IS_REPEATABLE = 0x01
        private const val ACTION_FLAGS_NO_KEY_PREVIEW = 0x02
        private const val ACTION_FLAGS_ALT_CODE_WHILE_TYPING = 0x04
        private const val ACTION_FLAGS_ENABLE_LONG_PRESS = 0x08

        @JvmStatic
        fun removeRedundantPopupKeys(key: Key, lettersOnBaseLayout: PopupKeySpec.LettersOnBaseLayout): Key {
            if (key.isPopupKeysFixedColumn) {
                return key
            }
            val popupKeys = key.popupKeys
            val filteredPopupKeys = PopupKeySpec.removeRedundantPopupKeys(popupKeys, lettersOnBaseLayout)
            return if (filteredPopupKeys === popupKeys) key else Key(key, filteredPopupKeys)
        }

        private fun needsToUpcase(labelFlags: Int, keyboardElementId: Int): Boolean {
            if ((labelFlags and LABEL_FLAGS_PRESERVE_CASE) != 0) return false
            return when (keyboardElementId) {
                KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED,
                KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED,
                KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED,
                KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED -> true
                else -> false
            }
        }

        private fun computeHashCode(key: Key): Int {
            return Arrays.hashCode(
                arrayOf(
                    key.x,
                    key.y,
                    key.width,
                    key.height,
                    key.code,
                    key.label,
                    key.hintLabel,
                    key.iconName,
                    key.backgroundType,
                    key.popupKeys.contentHashCode(),
                    key.outputText,
                    key.mActionFlags,
                    key.mLabelFlags
                )
            )
        }

        private fun backgroundName(backgroundType: Int): String? = when (backgroundType) {
            BACKGROUND_TYPE_EMPTY -> "empty"
            BACKGROUND_TYPE_NORMAL -> "normal"
            BACKGROUND_TYPE_FUNCTIONAL -> "functional"
            BACKGROUND_TYPE_ACTION -> "action"
            BACKGROUND_TYPE_SPACEBAR -> "spacebar"
            else -> null
        }

        private fun isEmoticon(text: String?): Boolean {
            if (text == null || text.length < 2) return false
            for (i in 0 until text.length) {
                val c = text[i]
                if (c in '!'..'~') return true
            }
            return false
        }

        private fun getDisabledIconName(iconName: String): String? {
            if (iconName == toolbarKeyStrings[ToolbarKey.VOICE]) {
                return KeyboardIconsSet.NAME_SHORTCUT_KEY_DISABLED
            }
            return null
        }
    }
}
