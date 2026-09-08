/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.suggestions

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.CharacterStyle
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import helium314.keyboard.accessibility.AccessibilityUtils
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.PunctuationSuggestions
import helium314.keyboard.latin.R
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.isEmoji
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.ViewLayoutUtils

internal class SuggestionStripLayoutHelper(
    context: Context,
    attrs: AttributeSet?,
    defStyle: Int,
    wordViews: ArrayList<TextView>,
    dividerViews: ArrayList<View>,
    debugInfoViews: ArrayList<TextView>
) {
    val mPadding: Int
    val mDividerWidth: Int
    val mSuggestionsStripHeight: Int
    private var mSuggestionsCountInStrip: Int
    val mMoreSuggestionsRowHeight: Int
    private var mMaxMoreSuggestionsRow: Int
    val mMinMoreSuggestionsWidth: Float
    val mMoreSuggestionsBottomGap: Int
    private var mMoreSuggestionsAvailable: Boolean = false

    private val mWordViews: ArrayList<TextView> = wordViews
    private val mDividerViews: ArrayList<View> = dividerViews
    private val mDebugInfoViews: ArrayList<TextView> = debugInfoViews

    private val mColorValidTypedWord: Int
    private val mColorTypedWord: Int
    private val mColorAutoCorrect: Int
    private val mColorSuggested: Int
    private val mAlphaObsoleted: Float
    private var mCenterSuggestionWeight: Float
    private val mOriginalCenterSuggestionWeight: Float
    private var mCenterPositionInStrip: Int
    private var mTypedWordPositionWhenAutocorrect: Int
    private val mMoreSuggestionsHint: Drawable

    private val mSuggestionStripOptions: Int

    init {
        val wordView = wordViews[0]
        val dividerView = dividerViews[0]
        mPadding = wordView.compoundPaddingLeft + wordView.compoundPaddingRight
        dividerView.measure(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        mDividerWidth = dividerView.measuredWidth

        val res = wordView.resources
        mSuggestionsStripHeight = ResourceUtils.getSuggestionsStripHeight(res)

        val a = context.obtainStyledAttributes(
            attrs,
            R.styleable.SuggestionStripView, defStyle, R.style.SuggestionStripView
        )
        mSuggestionStripOptions = a.getInt(R.styleable.SuggestionStripView_suggestionStripOptions, 0)
        mAlphaObsoleted = ResourceUtils.getFraction(a, R.styleable.SuggestionStripView_alphaObsoleted, 1.0f)

        val colors = Settings.getValues().mColors
        mColorValidTypedWord = colors.get(ColorType.SUGGESTION_VALID_WORD)
        mColorTypedWord = colors.get(ColorType.SUGGESTION_TYPED_WORD)
        mColorAutoCorrect = colors.get(ColorType.SUGGESTION_AUTO_CORRECT)
        mColorSuggested = colors.get(ColorType.SUGGESTED_WORD)
        val colorMoreSuggestionsHint = colors.get(ColorType.MORE_SUGGESTIONS_HINT)

        mSuggestionsCountInStrip = a.getInt(
            R.styleable.SuggestionStripView_suggestionsCountInStrip,
            DEFAULT_SUGGESTIONS_COUNT_IN_STRIP
        )
        mCenterSuggestionWeight = ResourceUtils.getFraction(
            a,
            R.styleable.SuggestionStripView_centerSuggestionPercentile,
            DEFAULT_CENTER_SUGGESTION_PERCENTILE
        )
        mMaxMoreSuggestionsRow = a.getInt(
            R.styleable.SuggestionStripView_maxMoreSuggestionsRow,
            DEFAULT_MAX_MORE_SUGGESTIONS_ROW
        )
        mMinMoreSuggestionsWidth = ResourceUtils.getFraction(
            a,
            R.styleable.SuggestionStripView_minMoreSuggestionsWidth, 1.0f
        )
        a.recycle()

        mMoreSuggestionsHint = getMoreSuggestionsHint(
            res,
            res.getDimension(R.dimen.config_more_suggestions_hint_text_size),
            colorMoreSuggestionsHint
        )
        mOriginalCenterSuggestionWeight = mCenterSuggestionWeight
        mCenterPositionInStrip = mSuggestionsCountInStrip / 2
        mTypedWordPositionWhenAutocorrect = mCenterPositionInStrip - 1
        mMoreSuggestionsBottomGap = res.getDimensionPixelOffset(
            R.dimen.config_more_suggestions_bottom_gap
        )
        mMoreSuggestionsRowHeight = res.getDimensionPixelSize(
            R.dimen.config_more_suggestions_row_height
        )
    }

    fun setSuggestionsCountInStrip(count: Int) {
        mSuggestionsCountInStrip = count
        if (count > 3) {
            mCenterSuggestionWeight = 0.20f
        } else {
            mCenterSuggestionWeight = mOriginalCenterSuggestionWeight
        }
        mCenterPositionInStrip = mSuggestionsCountInStrip / 2
        mTypedWordPositionWhenAutocorrect = mCenterPositionInStrip - 1
    }

    val maxMoreSuggestionsRow: Int
        get() = mMaxMoreSuggestionsRow

    private fun getMoreSuggestionsHeight(): Int {
        return mMaxMoreSuggestionsRow * mMoreSuggestionsRowHeight + mMoreSuggestionsBottomGap
    }

    fun setMoreSuggestionsHeight(remainingHeight: Int) {
        val currentHeight = getMoreSuggestionsHeight()
        if (currentHeight <= remainingHeight) {
            return
        }
        mMaxMoreSuggestionsRow = (remainingHeight - mMoreSuggestionsBottomGap) / mMoreSuggestionsRowHeight
    }

    private fun getStyledSuggestedWord(suggestedWords: SuggestedWords, indexInSuggestedWords: Int): CharSequence? {
        if (indexInSuggestedWords >= suggestedWords.size()) {
            return null
        }
        val word = suggestedWords.getLabel(indexInSuggestedWords)
        val isAutoCorrection = suggestedWords.mWillAutoCorrect &&
                indexInSuggestedWords == SuggestedWords.INDEX_OF_AUTO_CORRECTION
        val isTypedWordValid = suggestedWords.mTypedWordValid &&
                indexInSuggestedWords == SuggestedWords.INDEX_OF_TYPED_WORD
        if (!isAutoCorrection && !isTypedWordValid) {
            return word
        }

        val spannedWord: Spannable = SpannableString(word)
        val options = mSuggestionStripOptions
        if ((isAutoCorrection && (options and AUTO_CORRECT_BOLD) != 0) ||
            (isTypedWordValid && (options and VALID_TYPED_WORD_BOLD) != 0)
        ) {
            addStyleSpan(spannedWord, BOLD_SPAN)
        }
        if (isAutoCorrection && (options and AUTO_CORRECT_UNDERLINE) != 0) {
            addStyleSpan(spannedWord, UNDERLINE_SPAN)
        }
        return spannedWord
    }

    private fun getPositionInSuggestionStrip(indexInSuggestedWords: Int, suggestedWords: SuggestedWords): Int {
        val settingsValues = Settings.getValues()
        val shouldOmitTypedWord = shouldOmitTypedWord(
            suggestedWords.mInputStyle,
            settingsValues.mGestureFloatingPreviewTextEnabled, true
        )
        return getPositionInSuggestionStrip(
            indexInSuggestedWords, suggestedWords.mWillAutoCorrect,
            shouldOmitTypedWord, mCenterPositionInStrip, mTypedWordPositionWhenAutocorrect
        )
    }

    private fun getSuggestionTextColor(suggestedWords: SuggestedWords, indexInSuggestedWords: Int): Int {
        val isTypedWord = suggestedWords.getInfo(indexInSuggestedWords)
            .isKindOf(SuggestedWordInfo.KIND_TYPED)

        val color: Int = when {
            indexInSuggestedWords == SuggestedWords.INDEX_OF_AUTO_CORRECTION && suggestedWords.mWillAutoCorrect -> mColorAutoCorrect
            suggestedWords.isPrediction && indexInSuggestedWords == 0 -> mColorAutoCorrect
            isTypedWord && suggestedWords.mTypedWordValid -> mColorValidTypedWord
            isTypedWord -> mColorTypedWord
            else -> mColorSuggested
        }
        if (suggestedWords.mIsObsoleteSuggestions && !isTypedWord) {
            return applyAlpha(color, mAlphaObsoleted)
        }
        return color
    }

    private fun layoutDebugInfo(positionInStrip: Int, placerView: ViewGroup, x: Int) {
        val debugInfoView = mDebugInfoViews[positionInStrip]
        val debugInfo = debugInfoView.text
        if (debugInfo == null) {
            return
        }
        placerView.addView(debugInfoView)
        debugInfoView.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val infoWidth = debugInfoView.measuredWidth
        ViewLayoutUtils.placeViewAt(debugInfoView, x - infoWidth, 0, infoWidth, debugInfoView.measuredHeight)
    }

    private fun getSuggestionWidth(positionInStrip: Int, maxWidth: Int): Int {
        val paddings = mPadding * mSuggestionsCountInStrip
        val dividers = mDividerWidth * (mSuggestionsCountInStrip - 1)
        val availableWidth = maxWidth - paddings - dividers
        return (availableWidth * getSuggestionWeight(positionInStrip)).toInt()
    }

    private fun getSuggestionWeight(positionInStrip: Int): Float {
        if (positionInStrip == mCenterPositionInStrip) {
            return mCenterSuggestionWeight
        }
        return (1.0f - mCenterSuggestionWeight) / (mSuggestionsCountInStrip - 1)
    }

    private fun setupWordViewsAndReturnStartIndexOfMoreSuggestions(
        suggestedWords: SuggestedWords, maxSuggestionInStrip: Int
    ): Int {
        for (positionInStrip in 0 until maxSuggestionInStrip) {
            val wordView = mWordViews[positionInStrip]
            wordView.text = null
            wordView.tag = null
            if (SuggestionStripView.DEBUG_SUGGESTIONS) {
                mDebugInfoViews[positionInStrip].text = null
            }
        }
        var count = 0
        var indexInSuggestedWords = 0
        val emojiTypeface = Settings.getInstance().customEmojiTypeface
        while (indexInSuggestedWords < suggestedWords.size() && count < maxSuggestionInStrip) {
            val positionInStrip = getPositionInSuggestionStrip(indexInSuggestedWords, suggestedWords)
            if (positionInStrip >= 0) {
                val wordView = mWordViews[positionInStrip]
                wordView.tag = indexInSuggestedWords
                var label: CharSequence? = getStyledSuggestedWord(suggestedWords, indexInSuggestedWords)
                val switcher = KeyboardSwitcher.getInstance()
                val isPhysicalKeyboardInUse = switcher.isImeSuppressedByHardwareKeyboard(
                    Settings.getValues(), switcher.keyboardSwitchState
                )
                val showShortcuts = isPhysicalKeyboardInUse &&
                        Settings.getValues().mPhysicalKeyboardSuggestionShortcuts != "disabled"
                if (showShortcuts && positionInStrip >= 0 && positionInStrip < SUPERSCRIPT_DIGITS.size && !TextUtils.isEmpty(label)) {
                    label = label.toString() + " " + SUPERSCRIPT_DIGITS[positionInStrip]
                }
                wordView.text = label
                wordView.setTextColor(getSuggestionTextColor(suggestedWords, indexInSuggestedWords))

                if (emojiTypeface != null && isEmoji(wordView.text)) {
                    wordView.typeface = emojiTypeface
                } else {
                    wordView.typeface = getTextTypeface(wordView.text)
                }
                if (SuggestionStripView.DEBUG_SUGGESTIONS) {
                    mDebugInfoViews[positionInStrip].text = suggestedWords.getDebugString(indexInSuggestedWords)
                }
                count++
            }
            indexInSuggestedWords++
        }
        return indexInSuggestedWords
    }

    private fun layoutPunctuationsAndReturnStartIndexOfMoreSuggestions(
        punctuationSuggestions: PunctuationSuggestions, stripView: ViewGroup
    ): Int {
        val countInStrip = minOf(punctuationSuggestions.size(), PUNCTUATIONS_IN_STRIP)
        for (positionInStrip in 0 until countInStrip) {
            if (positionInStrip != 0) {
                addDivider(stripView, mDividerViews[positionInStrip])
            }

            val wordView = mWordViews[positionInStrip]
            val punctuation = punctuationSuggestions.getLabel(positionInStrip)
            wordView.tag = positionInStrip
            wordView.text = punctuation
            wordView.contentDescription = punctuation
            wordView.textScaleX = 1.0f
            wordView.setCompoundDrawables(null, null, null, null)
            wordView.setTextColor(mColorAutoCorrect)
            stripView.addView(wordView)
            setLayoutWeight(wordView, 1.0f, mSuggestionsStripHeight)
        }
        mMoreSuggestionsAvailable = punctuationSuggestions.size() > countInStrip
        return countInStrip
    }

    fun layoutAndReturnStartIndexOfMoreSuggestions(
        context: Context,
        suggestedWords: SuggestedWords,
        stripView: ViewGroup,
        placerView: ViewGroup
    ): Int {
        if (suggestedWords.isPunctuationSuggestions) {
            return layoutPunctuationsAndReturnStartIndexOfMoreSuggestions(
                suggestedWords as PunctuationSuggestions, stripView
            )
        }

        val wordCountToShow = suggestedWords.getWordCountToShow()
        val startIndexOfMoreSuggestions = setupWordViewsAndReturnStartIndexOfMoreSuggestions(
            suggestedWords, mSuggestionsCountInStrip
        )
        val centerWordView = mWordViews[mCenterPositionInStrip]
        val stripWidth = stripView.width

        val centerWidth = getSuggestionWidth(mCenterPositionInStrip, stripWidth)
        if (wordCountToShow == 1 || getTextScaleX(centerWordView.text, centerWidth, centerWordView.paint) < MIN_TEXT_XSCALE) {
            val countInStrip = 1
            mMoreSuggestionsAvailable = wordCountToShow > countInStrip
            layoutWord(context, mCenterPositionInStrip, stripWidth - mPadding)
            stripView.addView(centerWordView)
            setLayoutWeight(centerWordView, 1.0f, ViewGroup.LayoutParams.MATCH_PARENT)
            if (SuggestionStripView.DEBUG_SUGGESTIONS) {
                layoutDebugInfo(mCenterPositionInStrip, placerView, stripWidth)
            }
            val lastIndex = centerWordView.tag as? Int
            return (lastIndex ?: 0) + 1
        }

        val countInStrip = mSuggestionsCountInStrip
        mMoreSuggestionsAvailable = wordCountToShow > countInStrip
        var x = 0
        for (positionInStrip in 0 until countInStrip) {
            if (positionInStrip != 0) {
                val divider = mDividerViews[positionInStrip]
                addDivider(stripView, divider)
                x += divider.measuredWidth
            }

            val width = getSuggestionWidth(positionInStrip, stripWidth)
            val wordView = layoutWord(context, positionInStrip, width)
            stripView.addView(wordView)
            setLayoutWeight(wordView, getSuggestionWeight(positionInStrip), ViewGroup.LayoutParams.MATCH_PARENT)
            x += wordView.measuredWidth

            if (SuggestionStripView.DEBUG_SUGGESTIONS) {
                layoutDebugInfo(positionInStrip, placerView, stripView.x.toInt() + x)
            }
        }
        return startIndexOfMoreSuggestions
    }

    private fun layoutWord(context: Context, positionInStrip: Int, width: Int): TextView {
        val wordView = mWordViews[positionInStrip]
        val word = wordView.text
        if (positionInStrip == mCenterPositionInStrip && mMoreSuggestionsAvailable) {
            wordView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, mMoreSuggestionsHint)
            wordView.compoundDrawablePadding = -mMoreSuggestionsHint.intrinsicHeight
        } else {
            wordView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
        }
        wordView.contentDescription = if (TextUtils.isEmpty(word))
            context.resources.getString(R.string.spoken_empty_suggestion)
        else
            word.toString()
        val text = getEllipsizedTextWithSettingScaleX(word, width, wordView.paint)
        val scaleX = wordView.textScaleX
        wordView.text = text
        wordView.textScaleX = scaleX
        wordView.isEnabled = !TextUtils.isEmpty(word) ||
                AccessibilityUtils.instance.isTouchExplorationEnabled
        return wordView
    }

    companion object {
        private const val DEFAULT_SUGGESTIONS_COUNT_IN_STRIP = 3
        private const val DEFAULT_CENTER_SUGGESTION_PERCENTILE = 0.40f
        private const val DEFAULT_MAX_MORE_SUGGESTIONS_ROW = 2
        private const val PUNCTUATIONS_IN_STRIP = 5
        private const val MIN_TEXT_XSCALE = 0.70f

        private const val MORE_SUGGESTIONS_HINT = "…"

        private val BOLD_SPAN: CharacterStyle = StyleSpan(Typeface.BOLD)
        private val UNDERLINE_SPAN: CharacterStyle = UnderlineSpan()

        private const val AUTO_CORRECT_BOLD = 0x01
        private const val AUTO_CORRECT_UNDERLINE = 0x02
        private const val VALID_TYPED_WORD_BOLD = 0x04
        private val SUPERSCRIPT_DIGITS = arrayOf("¹", "²", "³", "⁴", "⁵", "⁶", "⁷", "⁸", "⁹")

        private fun getMoreSuggestionsHint(res: Resources, textSize: Float, color: Int): Drawable {
            val paint = Paint()
            paint.isAntiAlias = true
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = textSize
            paint.color = color
            val bounds = Rect()
            paint.getTextBounds(MORE_SUGGESTIONS_HINT, 0, MORE_SUGGESTIONS_HINT.length, bounds)
            val width = Math.round(bounds.width() + 0.5f)
            val height = Math.round(bounds.height() + 0.5f)
            val buffer = Bitmap.createBitmap(width, height * 3 / 2, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(buffer)
            canvas.drawText(MORE_SUGGESTIONS_HINT, (width / 2).toFloat(), height.toFloat(), paint)
            val bitmapDrawable = BitmapDrawable(res, buffer)
            bitmapDrawable.setTargetDensity(canvas)
            return bitmapDrawable
        }

        fun shouldOmitTypedWord(
            inputStyle: Int,
            gestureFloatingPreviewTextEnabled: Boolean,
            shouldShowUiToAcceptTypedWord: Boolean
        ): Boolean {
            val omitTypedWord = (inputStyle == SuggestedWords.INPUT_STYLE_TYPING) ||
                    (inputStyle == SuggestedWords.INPUT_STYLE_TAIL_BATCH) ||
                    (inputStyle == SuggestedWords.INPUT_STYLE_UPDATE_BATCH && gestureFloatingPreviewTextEnabled)
            return shouldShowUiToAcceptTypedWord && omitTypedWord
        }

        fun getPositionInSuggestionStrip(
            indexInSuggestedWords: Int,
            willAutoCorrect: Boolean, omitTypedWord: Boolean,
            centerPositionInStrip: Int, typedWordPositionWhenAutoCorrect: Int
        ): Int {
            if (omitTypedWord) {
                if (indexInSuggestedWords == SuggestedWords.INDEX_OF_TYPED_WORD) {
                    return -1
                }
                if (indexInSuggestedWords == SuggestedWords.INDEX_OF_AUTO_CORRECTION) {
                    return centerPositionInStrip
                }
                val offsetFromCenter = if ((indexInSuggestedWords % 2) == 0) -(indexInSuggestedWords / 2)
                else (indexInSuggestedWords / 2)
                return centerPositionInStrip + offsetFromCenter
            }
            val indexToDisplayMostImportantSuggestion: Int
            val indexToDisplaySecondMostImportantSuggestion: Int
            if (willAutoCorrect) {
                indexToDisplayMostImportantSuggestion = SuggestedWords.INDEX_OF_AUTO_CORRECTION
                indexToDisplaySecondMostImportantSuggestion = SuggestedWords.INDEX_OF_TYPED_WORD
            } else {
                indexToDisplayMostImportantSuggestion = SuggestedWords.INDEX_OF_TYPED_WORD
                indexToDisplaySecondMostImportantSuggestion = SuggestedWords.INDEX_OF_AUTO_CORRECTION
            }
            if (indexInSuggestedWords == indexToDisplayMostImportantSuggestion) {
                return centerPositionInStrip
            }
            if (indexInSuggestedWords == indexToDisplaySecondMostImportantSuggestion) {
                return typedWordPositionWhenAutoCorrect
            }
            val n = indexInSuggestedWords + 1
            val offsetFromCenter = if ((n % 2) == 0) -(n / 2) else (n / 2)
            return centerPositionInStrip + offsetFromCenter
        }

        private fun applyAlpha(color: Int, alpha: Float): Int {
            val newAlpha = (Color.alpha(color) * alpha).toInt()
            return Color.argb(newAlpha, Color.red(color), Color.green(color), Color.blue(color))
        }

        private fun addDivider(stripView: ViewGroup, dividerView: View) {
            stripView.addView(dividerView)
            val params = dividerView.layoutParams as LinearLayout.LayoutParams
            params.gravity = Gravity.CENTER
        }

        fun setLayoutWeight(v: View, weight: Float, height: Int) {
            val lp = v.layoutParams
            if (lp is LinearLayout.LayoutParams) {
                lp.weight = weight
                lp.width = 0
                lp.height = height
            }
        }

        private fun getTextScaleX(text: CharSequence?, maxWidth: Int, paint: TextPaint): Float {
            paint.textScaleX = 1.0f
            val width = getTextWidth(text, paint)
            if (width <= maxWidth || maxWidth <= 0) {
                return 1.0f
            }
            return maxWidth / width.toFloat()
        }

        private fun getEllipsizedTextWithSettingScaleX(
            text: CharSequence?, maxWidth: Int, paint: TextPaint
        ): CharSequence? {
            if (text == null) {
                return null
            }
            val scaleX = getTextScaleX(text, maxWidth, paint)
            if (scaleX >= MIN_TEXT_XSCALE) {
                paint.textScaleX = scaleX
                return text
            }

            paint.textScaleX = MIN_TEXT_XSCALE
            val hasBoldStyle = hasStyleSpan(text, BOLD_SPAN)
            val hasUnderlineStyle = hasStyleSpan(text, UNDERLINE_SPAN)
            val ellipsizedText = TextUtils.ellipsize(text, paint, maxWidth.toFloat(), TextUtils.TruncateAt.MIDDLE)
            if (!hasBoldStyle && !hasUnderlineStyle) {
                return ellipsizedText
            }
            val spannableText = if (ellipsizedText is Spannable)
                ellipsizedText
            else
                SpannableString(ellipsizedText)
            if (hasBoldStyle) {
                addStyleSpan(spannableText, BOLD_SPAN)
            }
            if (hasUnderlineStyle) {
                addStyleSpan(spannableText, UNDERLINE_SPAN)
            }
            return spannableText
        }

        private fun hasStyleSpan(text: CharSequence?, style: CharacterStyle): Boolean {
            if (text is Spanned) {
                return text.getSpanStart(style) >= 0
            }
            return false
        }

        private fun addStyleSpan(text: Spannable, style: CharacterStyle) {
            text.removeSpan(style)
            text.setSpan(style, 0, text.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        }

        private fun getTextWidth(text: CharSequence?, paint: TextPaint): Int {
            if (text.isNullOrEmpty()) {
                return 0
            }
            val length = text.length
            val widths = FloatArray(length)
            val count: Int
            val savedTypeface = paint.typeface
            try {
                paint.typeface = getTextTypeface(text)
                count = paint.getTextWidths(text, 0, length, widths)
            } finally {
                paint.typeface = savedTypeface
            }
            var width = 0
            for (i in 0 until count) {
                width += Math.round(widths[i] + 0.5f)
            }
            return width
        }

        private fun getTextTypeface(text: CharSequence?): Typeface {
            val customTypeface = Settings.getInstance().customTypeface
            val isBold = hasStyleSpan(text, BOLD_SPAN)
            return if (customTypeface != null) {
                if (isBold) Typeface.create(customTypeface, Typeface.BOLD) else customTypeface
            } else {
                if (isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
        }
    }
}
