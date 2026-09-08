package helium314.keyboard.keyboard

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.NinePatchDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import helium314.keyboard.keyboard.emoji.EmojiPageKeyboardView
import helium314.keyboard.keyboard.internal.KeyDrawParams
import helium314.keyboard.keyboard.internal.KeyVisualAttributes
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.isEmoji
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.suggestions.MoreSuggestions
import helium314.keyboard.latin.suggestions.MoreSuggestionsView
import helium314.keyboard.latin.utils.TypefaceUtils
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

open class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = R.attr.keyboardViewStyle
) : View(context, attrs, defStyle) {

    private val mKeyVisualAttributes: KeyVisualAttributes?
    private val mDefaultKeyLabelFlags: Int
    private val mKeyHintLetterPadding: Float
    private val mKeyPopupHintLetter: String
    private val mKeyPopupHintLetterPadding: Float
    private val mKeyShiftedLetterHintPadding: Float
    private val mKeyTextShadowRadius: Float
    val verticalCorrection: Float
    private val mKeyBackground: Drawable
    private val mFunctionalKeyBackground: Drawable
    private val mActionKeyBackground: Drawable
    private val mSpacebarBackground: Drawable
    private val mEditModeKeyBackground: Drawable?
    private val mSpacebarIconWidthRatio: Float
    private val mKeyBackgroundPadding = Rect()
    private val mEditModeKeyBackgroundPadding = Rect()
    private val mColors = Settings.getValues().mColors
    private var mKeyScaleForText = 0f
    protected var mFontSizeMultiplier = 0f

    @Volatile private var mKeyboard: Keyboard? = null
    open val keyboard: Keyboard? get() = mKeyboard
    open val keyDrawParams: KeyDrawParams = KeyDrawParams()

    private val mKeyCustomBgColors = HashMap<Key, Int>()
    private var mInvalidateAllKeys = false
    private val mInvalidatedKeys = HashSet<Key>()
    private val mClipRect = Rect()
    private var mOffscreenBuffer: Bitmap? = null
    private var mShowsHints = false
    private var mIconScaleFactor = 1f
    private val mOffscreenCanvas = Canvas()
    private val mPaint = Paint()
    private val mFontMetrics = Paint.FontMetrics()
    protected var mTypeface: Typeface?
    protected val mEmojiTypeface: Typeface?

    init {
        val keyboardViewAttr = context.obtainStyledAttributes(attrs, R.styleable.KeyboardView, defStyle, R.style.KeyboardView)
        mKeyBackground = when {
            this is MoreSuggestionsView -> mColors.selectAndColorDrawable(keyboardViewAttr, ColorType.MORE_SUGGESTIONS_WORD_BACKGROUND)
            this is PopupKeysKeyboardView -> mColors.selectAndColorDrawable(keyboardViewAttr, ColorType.POPUP_KEYS_BACKGROUND)
            else -> mColors.selectAndColorDrawable(keyboardViewAttr, ColorType.KEY_BACKGROUND)
        }
        mKeyBackground.getPadding(mKeyBackgroundPadding)
        mFunctionalKeyBackground = mColors.selectAndColorDrawable(keyboardViewAttr, ColorType.FUNCTIONAL_KEY_BACKGROUND)
        mSpacebarBackground = mColors.selectAndColorDrawable(keyboardViewAttr, ColorType.SPACE_BAR_BACKGROUND)
        mActionKeyBackground = if (this is PopupKeysKeyboardView) {
            mColors.selectAndColorDrawable(keyboardViewAttr, ColorType.ACTION_KEY_POPUP_KEYS_BACKGROUND)
        } else {
            mColors.selectAndColorDrawable(keyboardViewAttr, ColorType.ACTION_KEY_BACKGROUND)
        }

        mSpacebarIconWidthRatio = keyboardViewAttr.getFloat(R.styleable.KeyboardView_spacebarIconWidthRatio, 1.0f)
        mKeyHintLetterPadding = keyboardViewAttr.getDimension(R.styleable.KeyboardView_keyHintLetterPadding, 0.0f)
        mKeyPopupHintLetter = if (Settings.getValues().mShowsPopupHints) keyboardViewAttr.getString(R.styleable.KeyboardView_keyPopupHintLetter) ?: "" else ""
        mKeyPopupHintLetterPadding = keyboardViewAttr.getDimension(R.styleable.KeyboardView_keyPopupHintLetterPadding, 0.0f)
        mKeyShiftedLetterHintPadding = keyboardViewAttr.getDimension(R.styleable.KeyboardView_keyShiftedLetterHintPadding, 0.0f)
        mKeyTextShadowRadius = keyboardViewAttr.getFloat(R.styleable.KeyboardView_keyTextShadowRadius, -1.0f)
        verticalCorrection = keyboardViewAttr.getDimension(R.styleable.KeyboardView_verticalCorrection, 0.0f)
        keyboardViewAttr.recycle()

        val popupAttrs = context.obtainStyledAttributes(attrs, R.styleable.KeyboardView, R.attr.popupKeysKeyboardViewStyle, R.style.PopupKeysKeyboardView)
        val editBg = popupAttrs.getDrawable(R.styleable.KeyboardView_keyBackground)
        mEditModeKeyBackground = editBg?.mutate()
        mEditModeKeyBackground?.getPadding(mEditModeKeyBackgroundPadding)
        popupAttrs.recycle()

        val keyAttr = context.obtainStyledAttributes(attrs, R.styleable.Keyboard_Key, defStyle, R.style.KeyboardView)
        mDefaultKeyLabelFlags = keyAttr.getInt(R.styleable.Keyboard_Key_keyLabelFlags, 0)
        mKeyVisualAttributes = KeyVisualAttributes.newInstance(keyAttr)
        keyAttr.recycle()

        mPaint.isAntiAlias = true
        mTypeface = Settings.getInstance().customTypeface
        mEmojiTypeface = Settings.getInstance().customEmojiTypeface
        fitsSystemWindows = true
    }

    
    open fun setHardwareAcceleratedDrawingEnabled(enabled: Boolean) {
        if (!enabled) return
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    val keyVisualAttribute: KeyVisualAttributes? get() = mKeyVisualAttributes
    protected fun updateKeyDrawParams(keyHeight: Int) { keyDrawParams.updateParams(keyHeight, mKeyVisualAttributes) }

    open fun setKeyboard(keyboard: Keyboard) {
        mKeyCustomBgColors.clear()
        when (keyboard) {
            is MoreSuggestions -> mColors.setBackground(this, ColorType.MORE_SUGGESTIONS_BACKGROUND)
            is PopupKeysKeyboard -> mColors.setBackground(this, ColorType.POPUP_KEYS_BACKGROUND)
            else -> setBackgroundColor(Color.TRANSPARENT)
        }
        mKeyboard = keyboard
        mKeyScaleForText = sqrt(1f / Settings.getValues().mKeyboardHeightScale.toDouble()).toFloat()
        val scaledKeyHeight = ((keyboard.mMostCommonKeyHeight - keyboard.mVerticalGap) * mKeyScaleForText).toInt()
        keyDrawParams.updateParams(scaledKeyHeight, mKeyVisualAttributes)
        keyDrawParams.updateParams(scaledKeyHeight, keyboard.mKeyVisualAttributes)
        invalidateAllKeys()
        if (isInLayout) post { requestLayout() } else requestLayout()
        
        mFontSizeMultiplier = if (keyboard.mId.mElementId == KeyboardId.ELEMENT_EMOJI_CATEGORY10) {
            Settings.getValues().mFontSizeMultiplierEmoji * 0.55f
        } else {
            if (keyboard.mId.isEmojiKeyboard) (if (Settings.getValues().mEmojiKeyFit) 1f else Settings.getValues().mFontSizeMultiplierEmoji) else Settings.getValues().mFontSizeMultiplier
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val kb = keyboard
        if (kb == null) { super.onMeasure(widthMeasureSpec, heightMeasureSpec); return }
        setMeasuredDimension(kb.mOccupiedWidth + paddingLeft + paddingRight, kb.mOccupiedHeight + paddingTop + paddingBottom)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (canvas.isHardwareAccelerated) { onDrawKeyboard(canvas); return }
        if (mInvalidateAllKeys || mInvalidatedKeys.isNotEmpty() || mOffscreenBuffer == null) {
            if (maybeAllocateOffscreenBuffer()) {
                mInvalidateAllKeys = true
                mOffscreenCanvas.setBitmap(mOffscreenBuffer)
            }
            onDrawKeyboard(mOffscreenCanvas)
        }
        mOffscreenBuffer?.let { canvas.drawBitmap(it, 0f, 0f, null) }
    }

    private fun maybeAllocateOffscreenBuffer(): Boolean {
        val w = width; val h = height
        if (w == 0 || h == 0) return false
        val buffer = mOffscreenBuffer
        if (buffer != null && buffer.width == w && buffer.height == h) return false
        freeOffscreenBuffer()
        mOffscreenBuffer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        return true
    }

    private fun freeOffscreenBuffer() {
        mOffscreenCanvas.setBitmap(null)
        mOffscreenCanvas.setMatrix(null)
        mOffscreenBuffer?.recycle()
        mOffscreenBuffer = null
    }

    private fun onDrawKeyboard(canvas: Canvas) {
        val kb = keyboard ?: return
        val sv = Settings.getValues()
        mShowsHints = sv.mShowsHints
        val scale = sv.mKeyboardHeightScale
        val floatingScale = helium314.keyboard.latin.utils.ResourceUtils.getFloatingKeyboardScale()
        val floatingWidth = helium314.keyboard.latin.utils.ResourceUtils.getFloatingKeyboardWidth()
        val defaultWidth = helium314.keyboard.latin.utils.ResourceUtils.getDefaultKeyboardWidth(context)
        
        if (floatingScale > 0.0f || floatingWidth > 0) {
            val heightScale = scale * (if (floatingScale > 0.0f) floatingScale else 1.0f)
            val widthScale = if (floatingWidth > 0 && defaultWidth > 0) (floatingWidth.toFloat() / defaultWidth) else 1.0f
            val effectiveKeyScale = min(heightScale, widthScale)
            mIconScaleFactor = max(0.4f, min(effectiveKeyScale, 1.5f))
        } else {
            mIconScaleFactor = if (scale < 0.8f) scale + 0.2f else 1f
        }

        val paint = mPaint
        val background = background
        val drawAllKeys = mInvalidateAllKeys || mInvalidatedKeys.isEmpty()
        val isHardwareAccelerated = canvas.isHardwareAccelerated

        if (drawAllKeys || isHardwareAccelerated) {
            if (!isHardwareAccelerated && background != null) {
                canvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR)
                background.draw(canvas)
            }
            for (key in kb.sortedKeys) onDrawKey(key, canvas, paint)
        } else {
            for (key in mInvalidatedKeys) {
                if (!kb.hasKey(key)) continue
                if (background != null) {
                    val x = key.x + paddingLeft
                    val y = key.y + paddingTop
                    mClipRect.set(x, y, x + key.width, y + key.height)
                    canvas.save()
                    canvas.clipRect(mClipRect)
                    canvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR)
                    background.draw(canvas)
                    canvas.restore()
                }
                onDrawKey(key, canvas, paint)
            }
        }
        mInvalidatedKeys.clear()
        mInvalidateAllKeys = false
    }

    private fun onDrawKey(key: Key, canvas: Canvas, paint: Paint) {
        val keyDrawX = key.drawX + paddingLeft
        val keyDrawY = key.y + paddingTop
        canvas.translate(keyDrawX.toFloat(), keyDrawY.toFloat())

        val attr = key.visualAttributes
        val params = keyDrawParams.mayCloneAndUpdateParams((key.height * mKeyScaleForText).toInt(), attr)
        params.mAnimAlpha = Constants.Color.ALPHA_OPAQUE

        if (!key.isSpacer) {
            val background = key.selectBackgroundDrawable(mKeyBackground, mFunctionalKeyBackground, mSpacebarBackground, mActionKeyBackground)
            onDrawKeyBackground(key, canvas, background)
        }
        onDrawKeyTopVisuals(key, canvas, paint, params)
        canvas.translate(-keyDrawX.toFloat(), -keyDrawY.toFloat())
    }

    protected open fun onDrawKeyBackground(key: Key, canvas: Canvas, background: Drawable) {
        var customColor = 0
        val isTextEditMode = KeyboardActionListenerImpl.sPersistentTextEditModeActive || (keyboard?.mId?.mElementId == KeyboardId.ELEMENT_TEXT_EDIT)
        if (isTextEditMode) {
            customColor = when (key.code) {
                -131, -132, -7, -9 -> mColors.get(ColorType.EDIT_MODE_DELETE_BACKGROUND)
                -35, -34, -306, -32, -31, -33 -> mColors.get(ColorType.EDIT_MODE_FUNC_BACKGROUND)
                -201 -> mColors.get(ColorType.EDIT_MODE_ALPHA_BACKGROUND)
                -23, -24, -21, -22, 32 -> mColors.get(ColorType.EDIT_MODE_NAV_BACKGROUND)
                -25, -26, -27, -28, -10015, -10016 -> mColors.get(ColorType.EDIT_MODE_JUMP_BACKGROUND)
                else -> if (key.hasActionKeyBackground()) mColors.get(ColorType.ACTION_KEY_BACKGROUND) else 0
            }
        }

        val editBg = mEditModeKeyBackground
        val drawBackground = if (customColor != 0 && editBg != null) editBg else background
        val padding = if (customColor != 0 && editBg != null) mEditModeKeyBackgroundPadding else mKeyBackgroundPadding

        val keyWidth = key.drawWidth
        val keyHeight = key.height
        val bgWidth: Int; val bgHeight: Int; val bgX: Int; val bgY: Int
        
        if (key.needsToKeepBackgroundAspectRatio(mDefaultKeyLabelFlags) && !isTextEditMode && !key.hasCustomActionLabel()) {
            bgWidth = (drawBackground.intrinsicWidth * mIconScaleFactor).toInt()
            bgHeight = (drawBackground.intrinsicHeight * mIconScaleFactor).toInt()
            bgX = (keyWidth - bgWidth) / 2
            bgY = (keyHeight - bgHeight) / 2
        } else if (!mColors.hasKeyBorders && key.backgroundType == Key.BACKGROUND_TYPE_SPACEBAR) {
            val verticalInset = (keyHeight * 0.16f).toInt()
            bgWidth = keyWidth + padding.left + padding.right
            bgHeight = max(1, keyHeight + padding.top + padding.bottom - (verticalInset * 2))
            bgY = -padding.top + verticalInset
            bgX = -padding.left
        } else {
            bgWidth = keyWidth + padding.left + padding.right
            bgHeight = keyHeight + padding.top + padding.bottom
            bgY = -padding.top
            bgX = -padding.left
        }
        
        drawBackground.setBounds(0, 0, bgWidth, bgHeight)
        canvas.translate(bgX.toFloat(), bgY.toFloat())
        
        val isSelected = (key.code == KeyCode.SHIFT && key.isLocked) || (key.code == KeyCode.TOGGLE_SELECTION_MODE && KeyboardActionListenerImpl.sPersistentSelectionModeActive)
        var hasCustomTint = false
        
        if (customColor != 0) {
            DrawableCompat.setTint(drawBackground, customColor)
            mKeyCustomBgColors[key] = customColor
            hasCustomTint = true
        }
        if (isSelected) drawBackground.setColorFilter(Color.argb(0x80, 0, 0, 0), PorterDuff.Mode.SRC_ATOP)
        
        drawBackground.draw(canvas)
        
        if (isSelected) drawBackground.clearColorFilter()
        if (hasCustomTint) {
            if (drawBackground === background) {
                val originalType = if (key.backgroundType == Key.BACKGROUND_TYPE_FUNCTIONAL) ColorType.FUNCTIONAL_KEY_BACKGROUND else ColorType.KEY_BACKGROUND
                mColors.setColor(background, originalType)
            } else {
                DrawableCompat.setTintList(drawBackground, null)
            }
        }
        canvas.translate(-bgX.toFloat(), -bgY.toFloat())
    }

    protected open fun onDrawKeyTopVisuals(key: Key, canvas: Canvas, paint: Paint, params: KeyDrawParams) {
        val keyWidth = key.drawWidth
        val keyHeight = key.height
        val centerX = keyWidth * 0.5f
        val centerY = keyHeight * 0.5f

        val kb = keyboard
        val icon = kb?.let { key.getIcon(it.mIconsSet, params.mAnimAlpha) }
        var labelX = centerX
        var labelBaseline = centerY
        val label = key.label
        
        if (label != null) {
            val typeface = if (mEmojiTypeface != null && isEmoji(label)) mEmojiTypeface else mTypeface
            paint.typeface = typeface ?: key.selectTypeface(params)
            paint.textSize = key.selectTextSize(params) * mFontSizeMultiplier
            val labelCharHeight = TypefaceUtils.getReferenceCharHeight(paint)
            val labelCharWidth = TypefaceUtils.getReferenceCharWidth(paint)
            labelBaseline = centerY + labelCharHeight / 2.0f

            if (key.isAlignLabelOffCenter() && mShowsHints) {
                labelX = max(0f, centerX + params.mLabelOffCenterRatio * labelCharWidth)
                paint.textAlign = Paint.Align.LEFT
            } else {
                labelX = centerX
                paint.textAlign = Paint.Align.CENTER
            }
            
            if (key.needsAutoXScale() || (isEmoji(label) && Settings.getValues().mEmojiKeyFit)) {
                val width = if (key.needsToKeepBackgroundAspectRatio(mDefaultKeyLabelFlags) && !(KeyboardActionListenerImpl.sPersistentTextEditModeActive || (kb != null && kb.mId.mElementId == KeyboardId.ELEMENT_TEXT_EDIT))) {
                    val bg = key.selectBackgroundDrawable(mKeyBackground, mFunctionalKeyBackground, mSpacebarBackground, mActionKeyBackground)
                    min(bg.bounds.bottom, bg.bounds.right)
                } else keyWidth
                val ratio = min(1.0f, (width * 0.90f) / TypefaceUtils.getStringWidth(label, paint))
                if (key.needsAutoScale() || (isEmoji(label) && Settings.getValues().mEmojiKeyFit)) {
                    paint.textSize = paint.textSize * ratio
                } else {
                    paint.textScaleX = ratio
                }
            }

            if (key.isEnabled) {
                val customBgColor = mKeyCustomBgColors[key]
                if (customBgColor != null) {
                    paint.color = getContrastingColor(customBgColor)
                } else if (isEmoji(label)) {
                    paint.color = key.selectTextColor(params) or 0xFF000000.toInt()
                } else if (key.hasActionKeyBackground()) {
                    paint.color = mColors.get(ColorType.ACTION_KEY_ICON)
                } else if (this is EmojiPageKeyboardView) {
                    paint.color = mColors.get(ColorType.EMOJI_KEY_TEXT)
                } else if (this is PopupKeysKeyboardView) {
                    if (key.isPressed) {
                        val pressedBgColor = mColors.getPressedColor(if (key.hasActionKeyBackground()) ColorType.ACTION_KEY_POPUP_KEYS_BACKGROUND else ColorType.POPUP_KEYS_BACKGROUND)
                        paint.color = getContrastingColor(pressedBgColor)
                    } else {
                        paint.color = mColors.get(ColorType.POPUP_KEY_TEXT)
                    }
                } else {
                    paint.color = key.selectTextColor(params)
                }
                if (mKeyTextShadowRadius > 0.0f) paint.setShadowLayer(mKeyTextShadowRadius, 0.0f, 0.0f, params.mTextShadowColor) else paint.clearShadowLayer()
            } else {
                paint.color = Color.TRANSPARENT
                paint.clearShadowLayer()
            }
            blendAlpha(paint, params.mAnimAlpha)
            canvas.drawText(label, 0, label.length, labelX, labelBaseline, paint)
            paint.clearShadowLayer()
            paint.textScaleX = 1.0f
        }

        val hintLabel = key.hintLabel
        if (hintLabel != null && mShowsHints) {
            paint.textSize = key.selectHintTextSize(params) * mFontSizeMultiplier
            paint.color = key.selectHintTextColor(params)
            val typeface = if (mEmojiTypeface != null && isEmoji(hintLabel)) mEmojiTypeface else mTypeface
            paint.typeface = typeface ?: Typeface.DEFAULT_BOLD
            blendAlpha(paint, params.mAnimAlpha)
            val labelCharHeight = TypefaceUtils.getReferenceCharHeight(paint)
            val labelCharWidth = TypefaceUtils.getReferenceCharWidth(paint)
            val isFunctionalKeyAndRoundedStyle = mColors.themeStyle == helium314.keyboard.keyboard.KeyboardTheme.STYLE_ROUNDED && key.hasFunctionalBackground()
            val hintX: Float; val hintBaseline: Float
            
            if (key.hasHintLabel()) {
                hintX = labelX + params.mHintLabelOffCenterRatio * labelCharWidth
                hintBaseline = if (key.isAlignHintLabelToBottom(mDefaultKeyLabelFlags)) labelBaseline else centerY + labelCharHeight / 2.0f
                paint.textAlign = Paint.Align.LEFT
                val ratio = min(1.0f, (keyWidth - hintX) * 0.95f / TypefaceUtils.getStringWidth(hintLabel, paint))
                paint.textSize = paint.textSize * ratio
            } else if (key.hasShiftedLetterHint()) {
                hintX = keyWidth - mKeyShiftedLetterHintPadding - labelCharWidth / 2.0f
                paint.getFontMetrics(mFontMetrics)
                hintBaseline = -mFontMetrics.top
                paint.textAlign = Paint.Align.CENTER
            } else {
                val hintDigitWidth = TypefaceUtils.getReferenceDigitWidth(paint)
                val hintLabelWidth = TypefaceUtils.getStringWidth(hintLabel, paint)
                hintBaseline = -paint.ascent()
                hintX = if (isFunctionalKeyAndRoundedStyle) keyWidth - hintBaseline else keyWidth - mKeyHintLetterPadding - max(hintDigitWidth, hintLabelWidth) / 2.0f
                paint.textAlign = Paint.Align.CENTER
            }
            val adjustmentY = if (isFunctionalKeyAndRoundedStyle) hintBaseline * 0.5f else params.mHintLabelVerticalAdjustment * labelCharHeight
            canvas.drawText(hintLabel, 0, hintLabel.length, hintX, hintBaseline + adjustmentY, paint)
        }

        if (label == null && icon != null) {
            val iconWidth = if (key.code == Constants.CODE_SPACE && icon is NinePatchDrawable) (keyWidth * mSpacebarIconWidthRatio * mIconScaleFactor).toInt() else (min(icon.intrinsicWidth, keyWidth) * mIconScaleFactor).toInt()
            val iconHeight = (icon.intrinsicHeight * mIconScaleFactor).toInt()
            val iconY = if (key.isAlignIconToBottom()) keyHeight - iconHeight else (keyHeight - iconHeight) / 2
            val iconX = (keyWidth - iconWidth) / 2
            setKeyIconColor(key, icon, kb)
            drawIcon(canvas, icon, iconX, iconY, iconWidth, iconHeight)
        }

        if (key.hasPopupHint() && key.popupKeys != null) drawKeyPopupHint(key, canvas, paint, params)
    }

    protected open fun drawKeyPopupHint(key: Key, canvas: Canvas, paint: Paint, params: KeyDrawParams) {
        if (TextUtils.isEmpty(mKeyPopupHintLetter)) return
        val keyWidth = key.drawWidth
        val keyHeight = key.height
        val labelCharWidth = TypefaceUtils.getReferenceCharWidth(paint)
        val hintBaseline = paint.ascent()
        paint.typeface = params.mTypeface
        paint.textSize = params.mHintLetterSize.toFloat()
        paint.color = params.mHintLabelColor
        paint.textAlign = Paint.Align.CENTER
        val hintX = if (mColors.themeStyle == helium314.keyboard.keyboard.KeyboardTheme.STYLE_ROUNDED) {
            if (key.backgroundType == Key.BACKGROUND_TYPE_SPACEBAR) keyWidth + hintBaseline + labelCharWidth * 0.1f
            else if (key.hasFunctionalBackground() || key.hasActionKeyBackground()) keyWidth / 2.0f
            else keyWidth - mKeyHintLetterPadding - labelCharWidth / 2.0f
        } else {
            keyWidth - mKeyHintLetterPadding - TypefaceUtils.getReferenceCharWidth(paint) / 2.0f
        }
        val hintY = keyHeight - mKeyPopupHintLetterPadding
        canvas.drawText(mKeyPopupHintLetter, hintX, hintY, paint)
    }

    protected fun drawIcon(canvas: Canvas, icon: Drawable, x: Int, y: Int, width: Int, height: Int) {
        canvas.translate(x.toFloat(), y.toFloat())
        icon.setBounds(0, 0, width, height)
        icon.draw(canvas)
        canvas.translate(-x.toFloat(), -y.toFloat())
    }

    fun newLabelPaint(key: Key?): Paint {
        val paint = Paint()
        paint.isAntiAlias = true
        if (key == null) {
            paint.typeface = keyDrawParams.mTypeface
            paint.textSize = keyDrawParams.mLetterSize.toFloat()
        } else {
            paint.color = key.selectTextColor(keyDrawParams)
            paint.typeface = key.selectTypeface(keyDrawParams)
            paint.textSize = key.selectTextSize(keyDrawParams) * mFontSizeMultiplier
        }
        return paint
    }

    fun invalidateAllKeys() {
        mInvalidatedKeys.clear()
        mInvalidateAllKeys = true
        invalidate()
    }

    fun invalidateKey(key: Key?) {
        if (mInvalidateAllKeys || key == null) return
        mInvalidatedKeys.add(key)
        val x = key.x + paddingLeft
        val y = key.y + paddingTop
        invalidate(x, y, x + key.width, y + key.height)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        freeOffscreenBuffer()
    }

    open fun deallocateMemory() { freeOffscreenBuffer() }

    private fun getContrastingColor(bgColor: Int): Int {
        val baseBg = mColors.get(ColorType.MAIN_BACKGROUND)
        val compositeBg = getCompositeColor(bgColor, baseBg)
        val Lbg = ColorUtils.calculateLuminance(compositeBg)
        val Lwhite = 0.95; val Lblack = 0.015
        val ratioWhite = if (Lbg > Lwhite) (Lbg + 0.05) / (Lwhite + 0.05) else (Lwhite + 0.05) / (Lbg + 0.05)
        val ratioBlack = if (Lbg > Lblack) (Lbg + 0.05) / (Lblack + 0.05) else (Lblack + 0.05) / (Lbg + 0.05)
        return if (ratioWhite > ratioBlack) 0xFFFAFAFA.toInt() else 0xFF222222.toInt()
    }

    private fun getCompositeColor(srcColor: Int, dstColor: Int): Int {
        val alpha = (srcColor ushr 24) and 0xFF
        if (alpha == 0xFF) return srcColor
        if (alpha == 0x00) return dstColor
        val fAlpha = alpha / 255f
        val srcR = (srcColor ushr 16) and 0xFF; val srcG = (srcColor ushr 8) and 0xFF; val srcB = srcColor and 0xFF
        val dstR = (dstColor ushr 16) and 0xFF; val dstG = (dstColor ushr 8) and 0xFF; val dstB = dstColor and 0xFF
        val r = (srcR * fAlpha + dstR * (1 - fAlpha)).roundToInt()
        val g = (srcG * fAlpha + dstG * (1 - fAlpha)).roundToInt()
        val b = (srcB * fAlpha + dstB * (1 - fAlpha)).roundToInt()
        return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
    }

    private fun setKeyIconColor(key: Key, icon: Drawable, keyboard: Keyboard?) {
        val customBgColor = mKeyCustomBgColors[key]
        if (customBgColor != null) {
            icon.setColorFilter(getContrastingColor(customBgColor), PorterDuff.Mode.SRC_IN)
        } else if (key.hasActionKeyBackground()) {
            mColors.setColor(icon, ColorType.ACTION_KEY_ICON)
        } else if (key.isShift() && keyboard != null) {
            if (keyboard.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED || keyboard.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED || keyboard.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED || keyboard.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED)
                mColors.setColor(icon, ColorType.SHIFT_KEY_ICON)
            else mColors.setColor(icon, ColorType.KEY_ICON)
        } else if (key.backgroundType != Key.BACKGROUND_TYPE_NORMAL) {
            mColors.setColor(icon, ColorType.KEY_ICON)
        } else if (this is PopupKeysKeyboardView) {
            if (key.isPressed) {
                val pressedBgColor = mColors.getPressedColor(if (key.hasActionKeyBackground()) ColorType.ACTION_KEY_POPUP_KEYS_BACKGROUND else ColorType.POPUP_KEYS_BACKGROUND)
                icon.setColorFilter(getContrastingColor(pressedBgColor), PorterDuff.Mode.SRC_IN)
            } else {
                mColors.setColor(icon, ColorType.POPUP_KEY_ICON)
            }
        } else if (key.code == Constants.CODE_SPACE || key.code == KeyCode.ZWNJ) {
            mColors.setColor(icon, ColorType.KEY_ICON)
        } else {
            mColors.setColor(icon, ColorType.KEY_TEXT)
        }
    }

    companion object {
        private fun blendAlpha(paint: Paint, alpha: Int) {
            val color = paint.color
            paint.setARGB((paint.alpha * alpha) / Constants.Color.ALPHA_OPAQUE, Color.red(color), Color.green(color), Color.blue(color))
        }
    }
}
