/*
 * Copyright (C) 2015 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.emoji

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.Paint
import androidx.core.graphics.PaintCompat
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.prefs
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class EmojiCategory(
    ctx: Context,
    private val mLayoutSet: KeyboardLayoutSet,
    emojiPaletteViewAttr: TypedArray
) {
    private val mPrefs: SharedPreferences = ctx.prefs()
    private val mRes: Resources = ctx.resources
    private val mContext: Context = ctx
    private val mMaxRecentsKeyCount: Int = mRes.getInteger(R.integer.config_emoji_keyboard_max_recents_key_count)
    private val mCategoryNameToIdMap = HashMap<String, Int>()
    private val mCategoryTabIconId = IntArray(sCategoryName.size)
    private val mShownCategories = ArrayList<CategoryProperties>()
    private val mCategoryKeyboardMap = ConcurrentHashMap<Long, DynamicGridKeyboard>()

    private var mCurrentCategoryId = ID_UNSPECIFIED
    private var mCurrentCategoryPageId = 0

    init {
        for (i in sCategoryName.indices) {
            mCategoryNameToIdMap[sCategoryName[i]] = i
            mCategoryTabIconId[i] = emojiPaletteViewAttr.getResourceId(sCategoryTabIconAttr[i], 0)
        }
    }

    inner class CategoryProperties(val mCategoryId: Int) {
        var mPageCount = -1
        fun getPageCount(): Int {
            if (mPageCount < 0) mPageCount = computeCategoryPageCount(mCategoryId)
            return mPageCount
        }
    }

    fun initialize() {
        val defaultCategoryId = ID_SMILEYS_EMOTION
        addShownCategoryId(ID_RECENTS)
        addShownCategoryId(ID_SMILEYS_EMOTION)
        addShownCategoryId(ID_PEOPLE_BODY)
        addShownCategoryId(ID_ANIMALS_NATURE)
        addShownCategoryId(ID_FOOD_DRINK)
        addShownCategoryId(ID_TRAVEL_PLACES)
        addShownCategoryId(ID_ACTIVITIES)
        addShownCategoryId(ID_OBJECTS)
        addShownCategoryId(ID_SYMBOLS)
        if (canShowFlagEmoji()) {
            addShownCategoryId(ID_FLAGS)
        }
        addShownCategoryId(ID_EMOTICONS)

        val recentsKbd = getKeyboard(ID_RECENTS, 0)
        mCurrentCategoryId = mPrefs.getInt(Settings.PREF_LAST_SHOWN_EMOJI_CATEGORY_ID, defaultCategoryId)
        mCurrentCategoryPageId = mPrefs.getInt(Settings.PREF_LAST_SHOWN_EMOJI_CATEGORY_PAGE_ID, Defaults.PREF_LAST_SHOWN_EMOJI_CATEGORY_PAGE_ID)
        if (!isShownCategoryId(mCurrentCategoryId)) {
            mCurrentCategoryId = defaultCategoryId
        } else if (mCurrentCategoryId == ID_RECENTS && (recentsKbd == null || recentsKbd.sortedKeys.isEmpty())) {
            mCurrentCategoryId = defaultCategoryId
        }

        if (mCurrentCategoryPageId >= computeCategoryPageCount(mCurrentCategoryId)) {
            mCurrentCategoryPageId = 0
        }
    }

    fun clearKeyboardCache() {
        mCategoryKeyboardMap.clear()
        for (props in mShownCategories) {
            props.mPageCount = -1 // reset page count in case size (number of keys per row) changed
        }
    }

    private fun addShownCategoryId(categoryId: Int) {
        val properties = CategoryProperties(categoryId)
        mShownCategories.add(properties)
    }

    private fun isShownCategoryId(categoryId: Int): Boolean {
        for (prop in mShownCategories) {
            if (prop.mCategoryId == categoryId) return true
        }
        return false
    }

    fun getCategoryId(name: String): Int {
        val strings = name.split("-")
        return mCategoryNameToIdMap[strings[0]]!!
    }

    fun getCategoryTabIcon(categoryId: Int): Int = mCategoryTabIconId[categoryId]

    fun getAccessibilityDescription(categoryId: Int): String = mRes.getString(sAccessibilityDescriptionResourceIdsForCategories[categoryId])

    fun getShownCategories(): ArrayList<CategoryProperties> = mShownCategories

    fun getCurrentCategoryId(): Int = mCurrentCategoryId

    fun getCurrentCategoryPageCount(): Int = getCategoryPageCount(mCurrentCategoryId)

    fun getCategoryPageCount(categoryId: Int): Int {
        for (prop in mShownCategories) {
            if (prop.mCategoryId == categoryId) return prop.getPageCount()
        }
        Log.w(TAG, "Invalid category id: $categoryId")
        return 0
    }

    fun setCurrentCategoryId(categoryId: Int) {
        mCurrentCategoryId = categoryId
        mPrefs.edit().putInt(Settings.PREF_LAST_SHOWN_EMOJI_CATEGORY_ID, categoryId).apply()
    }

    fun setCurrentCategoryPageId(id: Int) {
        mCurrentCategoryPageId = id
        mPrefs.edit().putInt(Settings.PREF_LAST_SHOWN_EMOJI_CATEGORY_PAGE_ID, id).apply()
    }

    fun getCurrentCategoryPageId(): Int = mCurrentCategoryPageId

    fun isInRecentTab(): Boolean = mCurrentCategoryId == ID_RECENTS

    fun getTabIdFromCategoryId(categoryId: Int): Int {
        for (i in mShownCategories.indices) {
            if (mShownCategories[i].mCategoryId == categoryId) return i
        }
        Log.w(TAG, "categoryId not found: $categoryId")
        return 0
    }

    fun getRecentTabId(): Int = getTabIdFromCategoryId(ID_RECENTS)

    private fun computeCategoryPageCount(categoryId: Int): Int {
        val keyboard = mLayoutSet.getKeyboard(sCategoryElementId[categoryId])
        return (keyboard.sortedKeys.size - 1) / computeMaxKeyCountPerPage(categoryId) + 1
    }

    fun getKeyboardFromAdapterPosition(categoryId: Int, position: Int): DynamicGridKeyboard? {
        if (position >= 0 && position < getCategoryPageCount(categoryId)) {
            return getKeyboard(categoryId, position)
        }
        Log.w(TAG, "invalid position for categoryId : $categoryId")
        return null
    }

    fun getKeyboard(categoryId: Int, id: Int): DynamicGridKeyboard? {
        synchronized(mCategoryKeyboardMap) {
            val categoryKeyboardMapKey = getCategoryKeyboardMapKey(categoryId, id)
            mCategoryKeyboardMap[categoryKeyboardMapKey]?.let { return it }

            val currentWidth = ResourceUtils.getKeyboardWidth(mContext, Settings.getValues())
            if (categoryId == ID_RECENTS) {
                val kbd = DynamicGridKeyboard(
                    mPrefs,
                    mLayoutSet.getKeyboard(KeyboardId.ELEMENT_EMOJI_RECENTS),
                    mMaxRecentsKeyCount, categoryId, currentWidth
                )
                mCategoryKeyboardMap[categoryKeyboardMapKey] = kbd
                kbd.loadRecentKeys(mCategoryKeyboardMap.values)
                return kbd
            }

            val keyboard = mLayoutSet.getKeyboard(sCategoryElementId[categoryId])
            val keyCountPerPage = computeMaxKeyCountPerPage(categoryId)
            val sortedKeysPages = sortKeysGrouped(keyboard.sortedKeys, keyCountPerPage)
            for (pageId in sortedKeysPages.indices) {
                val tempKeyboard = DynamicGridKeyboard(
                    mPrefs,
                    mLayoutSet.getKeyboard(KeyboardId.ELEMENT_EMOJI_RECENTS),
                    keyCountPerPage, categoryId, currentWidth
                )
                for (emojiKey in sortedKeysPages[pageId]) {
                    if (emojiKey == null) break
                    tempKeyboard.addKeyLast(emojiKey)
                }
                mCategoryKeyboardMap[getCategoryKeyboardMapKey(categoryId, pageId)] = tempKeyboard
            }
            return mCategoryKeyboardMap[categoryKeyboardMapKey]
        }
    }

    private fun computeMaxKeyCountPerPage(categoryId: Int): Int {
        val tempKeyboard = DynamicGridKeyboard(
            mPrefs,
            mLayoutSet.getKeyboard(KeyboardId.ELEMENT_EMOJI_RECENTS),
            0, categoryId, ResourceUtils.getKeyboardWidth(mContext, Settings.getValues())
        )
        return MAX_LINE_COUNT_PER_PAGE * tempKeyboard.occupiedColumnCount
    }

    fun getAllEmojiKeys(): List<String> {
        val allEmojis = ArrayList<String>()
        for (i in 1 until sCategoryElementId.size) {
            val kbd = mLayoutSet.getKeyboard(sCategoryElementId[i])
            for (key in kbd.sortedKeys) {
                if (key.outputText != null) {
                    allEmojis.add(key.outputText!!)
                }
            }
        }
        return allEmojis
    }

    companion object {
        private const val TAG = "EmojiCategory"

        const val ID_UNSPECIFIED = -1
        const val ID_RECENTS = 0
        const val ID_SMILEYS_EMOTION = 1
        const val ID_PEOPLE_BODY = 2
        const val ID_ANIMALS_NATURE = 3
        const val ID_FOOD_DRINK = 4
        const val ID_TRAVEL_PLACES = 5
        const val ID_ACTIVITIES = 6
        const val ID_OBJECTS = 7
        const val ID_SYMBOLS = 8
        const val ID_FLAGS = 9
        const val ID_EMOTICONS = 10

        private const val MAX_LINE_COUNT_PER_PAGE = 3

        private val sCategoryName = arrayOf(
            "recents", "smileys & emotion", "people & body", "animals & nature",
            "food & drink", "travel & places", "activities", "objects", "symbols", "flags", "emoticons"
        )

        private val sCategoryTabIconAttr = intArrayOf(
            R.styleable.EmojiPalettesView_iconEmojiRecentsTab,
            R.styleable.EmojiPalettesView_iconEmojiCategory1Tab,
            R.styleable.EmojiPalettesView_iconEmojiCategory2Tab,
            R.styleable.EmojiPalettesView_iconEmojiCategory3Tab,
            R.styleable.EmojiPalettesView_iconEmojiCategory4Tab,
            R.styleable.EmojiPalettesView_iconEmojiCategory5Tab,
            R.styleable.EmojiPalettesView_iconEmojiCategory6Tab,
            R.styleable.EmojiPalettesView_iconEmojiCategory7Tab,
            R.styleable.EmojiPalettesView_iconEmojiCategory8Tab,
            R.styleable.EmojiPalettesView_iconEmojiCategory9Tab,
            R.styleable.EmojiPalettesView_iconEmojiCategory10Tab
        )

        private val sAccessibilityDescriptionResourceIdsForCategories = intArrayOf(
            R.string.spoken_description_emoji_category_recents,
            R.string.spoken_description_emoji_category_eight_smiley,
            R.string.spoken_description_emoji_category_eight_smiley_people,
            R.string.spoken_description_emoji_category_eight_animals_nature,
            R.string.spoken_description_emoji_category_eight_food_drink,
            R.string.spoken_description_emoji_category_eight_travel_places,
            R.string.spoken_description_emoji_category_eight_activity,
            R.string.spoken_description_emoji_category_objects,
            R.string.spoken_description_emoji_category_symbols,
            R.string.spoken_description_emoji_category_flags,
            R.string.spoken_description_emoji_category_emoticons
        )

        private val sCategoryElementId = intArrayOf(
            KeyboardId.ELEMENT_EMOJI_RECENTS,
            KeyboardId.ELEMENT_EMOJI_CATEGORY1,
            KeyboardId.ELEMENT_EMOJI_CATEGORY2,
            KeyboardId.ELEMENT_EMOJI_CATEGORY3,
            KeyboardId.ELEMENT_EMOJI_CATEGORY4,
            KeyboardId.ELEMENT_EMOJI_CATEGORY5,
            KeyboardId.ELEMENT_EMOJI_CATEGORY6,
            KeyboardId.ELEMENT_EMOJI_CATEGORY7,
            KeyboardId.ELEMENT_EMOJI_CATEGORY8,
            KeyboardId.ELEMENT_EMOJI_CATEGORY9,
            KeyboardId.ELEMENT_EMOJI_CATEGORY10
        )

        fun getCategoryName(categoryId: Int, categoryPageId: Int): String {
            return sCategoryName[categoryId] + "-" + categoryPageId
        }

        private fun getCategoryKeyboardMapKey(categoryId: Int, id: Int): Long {
            return (categoryId.toLong() shl Integer.SIZE) or id.toLong()
        }

        private val EMOJI_KEY_COMPARATOR = Comparator<Key> { lhs, rhs ->
            val lHitBox = lhs.hitBox
            val rHitBox = rhs.hitBox
            if (lHitBox.top < rHitBox.top) return@Comparator -1
            else if (lHitBox.top > rHitBox.top) return@Comparator 1
            if (lHitBox.left < rHitBox.left) return@Comparator -1
            else if (lHitBox.left > rHitBox.left) return@Comparator 1
            if (lhs.code == rhs.code) return@Comparator 0
            if (lhs.code < rhs.code) -1 else 1
        }

        private fun sortKeysGrouped(inKeys: List<Key>, maxPageCount: Int): Array<Array<Key?>> {
            val keys = ArrayList(inKeys)
            Collections.sort(keys, EMOJI_KEY_COMPARATOR)
            val pageCount = (keys.size - 1) / maxPageCount + 1
            val retval = Array(pageCount) { arrayOfNulls<Key>(maxPageCount) }
            for (i in keys.indices) {
                retval[i / maxPageCount][i % maxPageCount] = keys[i]
            }
            return retval
        }

        private fun canShowFlagEmoji(): Boolean {
            val paint = Paint()
            val switzerland = "\uD83C\uDDE8\uD83C\uDDED"
            return PaintCompat.hasGlyph(paint, switzerland)
        }
    }
}
