// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.HorizontalScrollView

/**
 * Horizontal strip host for emoji category tabs that locks outer horizontal scrolling during
 * inline emoji search so the search input stays pinned while results scroll independently.
 */
class EmojiTabStripScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.horizontalScrollViewStyle
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    var isHorizontalScrollLocked: Boolean = false
        set(value) {
            field = value
            if (value) {
                super.scrollTo(0, scrollY)
            }
        }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (isHorizontalScrollLocked) return false
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (isHorizontalScrollLocked) return false
        return super.onTouchEvent(ev)
    }

    override fun canScrollHorizontally(direction: Int): Boolean {
        if (isHorizontalScrollLocked) return false
        return super.canScrollHorizontally(direction)
    }

    override fun scrollTo(x: Int, y: Int) {
        if (isHorizontalScrollLocked) {
            super.scrollTo(0, y)
        } else {
            super.scrollTo(x, y)
        }
    }
}
