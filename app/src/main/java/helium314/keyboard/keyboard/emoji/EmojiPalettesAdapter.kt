/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.emoji

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.Log

class EmojiPalettesAdapter(
    private val mEmojiCategory: EmojiCategory,
    private val mCategoryId: Int,
    private val mEmojiViewCallback: EmojiViewCallback
) : RecyclerView.Adapter<EmojiPalettesAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val keyboardView = inflater.inflate(R.layout.emoji_keyboard_page, parent, false) as EmojiPageKeyboardView
        keyboardView.setEmojiViewCallback(mEmojiViewCallback)
        return ViewHolder(keyboardView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (DEBUG_PAGER) {
            Log.d(TAG, "instantiate item: $position")
        }
        val keyboard = mEmojiCategory.getKeyboardFromAdapterPosition(mCategoryId, position)
        if (keyboard != null) {
            holder.getKeyboardView().setKeyboard(keyboard)
        }
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        holder.getKeyboardView().releaseCurrentKey(false)
        holder.getKeyboardView().deallocateMemory()
    }

    override fun getItemCount(): Int {
        return mEmojiCategory.getCategoryPageCount(mCategoryId)
    }

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val customView: EmojiPageKeyboardView = v as EmojiPageKeyboardView

        fun getKeyboardView(): EmojiPageKeyboardView {
            return customView
        }
    }

    companion object {
        private const val TAG = "EmojiPalettesAdapter"
        private const val DEBUG_PAGER = false
    }
}
