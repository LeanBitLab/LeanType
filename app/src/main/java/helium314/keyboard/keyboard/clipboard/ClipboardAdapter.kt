// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.keyboard.clipboard

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import androidx.core.graphics.ColorUtils
import helium314.keyboard.latin.ClipboardHistoryEntry
import helium314.keyboard.latin.ClipboardHistoryManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.isDarkColor

sealed class ClipboardDisplayItem {
    data class Header(val count: Int, val isFolded: Boolean) : ClipboardDisplayItem()
    data class Clip(val entry: ClipboardHistoryEntry) : ClipboardDisplayItem()
}

class ClipboardAdapter(
       val clipboardLayoutParams: ClipboardLayoutParams,
       val keyEventListener: OnKeyEventListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var clipboardHistoryManager: ClipboardHistoryManager? = null

    var pinnedIconResId = 0
    var itemBackgroundId = 0
    var itemTypeFace: Typeface? = null
    var itemTextColor = 0
    var itemTextSize = 0f

    private var filteredList: List<ClipboardHistoryEntry>? = null
    private var searchQuery: String = ""
    
    private val displayList = mutableListOf<ClipboardDisplayItem>()
    private var isPinnedFolded = true

    var isSelectionMode = false
        private set
    val selectedEntryIds = mutableSetOf<Long>()

    var onSelectionChangedListener: ((selectedCount: Int, totalCount: Int) -> Unit)? = null
    var onClipLongClickListener: ((ClipboardHistoryEntry, View) -> Boolean)? = null

    val isFiltering: Boolean
        get() = filteredList != null

    fun setSelectionMode(enabled: Boolean) {
        if (isSelectionMode != enabled) {
            isSelectionMode = enabled
            if (!enabled) {
                selectedEntryIds.clear()
            }
            refresh()
            onSelectionChangedListener?.invoke(selectedEntryIds.size, getAllClipEntries().size)
        }
    }

    fun toggleSelection(id: Long) {
        if (selectedEntryIds.contains(id)) {
            selectedEntryIds.remove(id)
        } else {
            selectedEntryIds.add(id)
        }
        refresh()
        onSelectionChangedListener?.invoke(selectedEntryIds.size, getAllClipEntries().size)
    }

    fun selectAll() {
        val all = getAllClipEntries()
        selectedEntryIds.clear()
        selectedEntryIds.addAll(all.map { it.id })
        refresh()
        onSelectionChangedListener?.invoke(selectedEntryIds.size, all.size)
    }

    fun deselectAll() {
        selectedEntryIds.clear()
        refresh()
        onSelectionChangedListener?.invoke(0, getAllClipEntries().size)
    }

    fun getAllClipEntries(): List<ClipboardHistoryEntry> {
        return filteredList ?: clipboardHistoryManager?.getClips() ?: emptyList()
    }

    fun getSelectedEntries(): List<ClipboardHistoryEntry> {
        val all = getAllClipEntries()
        return all.filter { selectedEntryIds.contains(it.id) }
    }

    fun getItemById(id: Long): ClipboardHistoryEntry? {
        return getAllClipEntries().firstOrNull { it.id == id }
    }

    fun findEntryPosition(id: Long): Int {
        return displayList.indexOfFirst { it is ClipboardDisplayItem.Clip && it.entry.id == id }
    }

    fun filter(query: String) {
        searchQuery = query
        if (query.isEmpty()) {
            filteredList = null
        } else {
            val allClips = clipboardHistoryManager?.getClips() ?: emptyList()
            filteredList = allClips.filter { it.text.contains(query, ignoreCase = true) }
        }
        refresh()
    }

    fun rebuildDisplayList() {
        displayList.clear()
        val allClips = filteredList ?: clipboardHistoryManager?.getClips() ?: emptyList()
        
        if (isFiltering) {
            allClips.forEach { displayList.add(ClipboardDisplayItem.Clip(it)) }
            return
        }

        val isFoldEnabled = Settings.getInstance()?.readClipboardFoldPinned()
            ?: Settings.getValues()?.mClipboardFoldPinned
            ?: false
        if (isFoldEnabled) {
            val pinnedClips = allClips.filter { it.isPinned }
            val unpinnedClips = allClips.filter { !it.isPinned }

            if (pinnedClips.isNotEmpty()) {
                displayList.add(ClipboardDisplayItem.Header(pinnedClips.size, isPinnedFolded))
                if (!isPinnedFolded) {
                    pinnedClips.forEach { displayList.add(ClipboardDisplayItem.Clip(it)) }
                }
            }
            unpinnedClips.forEach { displayList.add(ClipboardDisplayItem.Clip(it)) }
        } else {
            allClips.forEach { displayList.add(ClipboardDisplayItem.Clip(it)) }
        }
    }

    fun refresh() {
        rebuildDisplayList()
        notifyDataSetChanged()
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_CLIP = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (displayList[position]) {
            is ClipboardDisplayItem.Header -> VIEW_TYPE_HEADER
            is ClipboardDisplayItem.Clip -> VIEW_TYPE_CLIP
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            val textView = TextView(parent.context).apply {
                val lp = StaggeredGridLayoutManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.isFullSpan = true
                layoutParams = lp
                gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                setPadding(36, 24, 36, 24)
            }
            clipboardLayoutParams.setItemProperties(textView)
            HeaderViewHolder(textView)
        } else {
            val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.clipboard_entry_key, parent, false)
            ClipViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = displayList[position]) {
            is ClipboardDisplayItem.Header -> (holder as HeaderViewHolder).bind(item)
            is ClipboardDisplayItem.Clip -> (holder as ClipViewHolder).setContent(item.entry)
        }
    }

    fun getItem(position: Int): ClipboardHistoryEntry? {
        val item = displayList.getOrNull(position)
        return if (item is ClipboardDisplayItem.Clip) item.entry else null
    }

    fun removeDisplayItem(position: Int): ClipboardDisplayItem? {
        if (position in 0 until displayList.size) {
            return displayList.removeAt(position)
        }
        return null
    }

    override fun getItemCount(): Int {
        return displayList.size
    }

    private fun createCardBackground(view: View): Drawable {
        val colors = Settings.getValues().mColors
        val radiusDp = Settings.getValues().mKeyBorderRadius.takeIf { it >= 0f } ?: 8f
        val radiusPx = radiusDp * view.resources.displayMetrics.density
        val cardBg = colors.get(ColorType.POPUP_KEYS_BACKGROUND)
        val mainBg = colors.get(ColorType.MAIN_BACKGROUND)
        val isDark = isDarkColor(mainBg) || isDarkColor(cardBg)

        val contentDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(cardBg)
        }

        val maskDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(Color.WHITE)
        }

        val rippleColor = if (isDark) {
            ColorUtils.setAlphaComponent(Color.WHITE, 0x26)
        } else {
            ColorUtils.setAlphaComponent(Color.BLACK, 0x18)
        }

        return RippleDrawable(ColorStateList.valueOf(rippleColor), contentDrawable, maskDrawable)
    }

    inner class HeaderViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView) {
        init {
            textView.apply {
                typeface = itemTypeFace
                setTextColor(itemTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, itemTextSize)
                background = createCardBackground(this)
                clipToOutline = true
                
                setOnClickListener {
                    isPinnedFolded = !isPinnedFolded
                    refresh()
                }
            }
        }

        fun bind(header: ClipboardDisplayItem.Header) {
            textView.apply {
                text = buildString {
                    append(if (header.isFolded) "▶  " else "▼  ")
                    append(context.getString(R.string.clipboard_pinned))
                    append(" (")
                    append(header.count)
                    append(")")
                }
            }
        }
    }

    inner class ClipViewHolder(
            view: View
    ) : RecyclerView.ViewHolder(view), View.OnClickListener, View.OnTouchListener, View.OnLongClickListener {

        private val selectIconView: ImageView
        private val selectedOverlay: View?
        private val pinnedIconView: ImageView
        private val contentView: TextView
        private val imageContainer: View
        private val imageView: ImageView
        private val imageNameView: TextView

        init {
            view.apply {
                setOnClickListener(this@ClipViewHolder)
                setOnTouchListener(this@ClipViewHolder)
                setOnLongClickListener(this@ClipViewHolder)
                background = createCardBackground(this)
                isHapticFeedbackEnabled = false
                clipToOutline = true
            }
            selectIconView = view.findViewById(R.id.clipboard_entry_select_icon)
            selectedOverlay = view.findViewById(R.id.clipboard_entry_selected_overlay)
            selectedOverlay?.background = GradientDrawable().apply {
                val colors = Settings.getValues().mColors
                val accentColor = colors.get(ColorType.ACTION_KEY_BACKGROUND)
                val radiusDp = Settings.getValues().mKeyBorderRadius.takeIf { it >= 0f } ?: 8f
                val radiusPx = radiusDp * view.resources.displayMetrics.density
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radiusPx
                setColor(ColorUtils.setAlphaComponent(accentColor, 0x26))
            }
            pinnedIconView = view.findViewById<ImageView>(R.id.clipboard_entry_pinned_icon).apply {
                visibility = View.GONE
                if (pinnedIconResId != 0) {
                    setImageResource(pinnedIconResId)
                }
            }
            imageContainer = view.findViewById<View>(R.id.clipboard_entry_image_container).apply {
                visibility = View.GONE
            }
            imageView = view.findViewById(R.id.clipboard_entry_image)
            imageNameView = view.findViewById(R.id.clipboard_entry_image_name)
            contentView = view.findViewById<TextView>(R.id.clipboard_entry_content).apply {
                typeface = itemTypeFace
                setTextColor(itemTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, itemTextSize)
                maxLines = 4
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            clipboardLayoutParams.setItemProperties(view)
            val colors = Settings.getValues().mColors
            colors.setColor(pinnedIconView, ColorType.CLIPBOARD_PIN)
        }

        fun setContent(historyEntry: ClipboardHistoryEntry?) {
            val id = historyEntry?.id ?: 0L
            itemView.tag = id
            val isSelected = isSelectionMode && selectedEntryIds.contains(id)
            val colors = Settings.getValues().mColors

            if (pinnedIconResId != 0) {
                pinnedIconView.setImageResource(pinnedIconResId)
            }
            colors.setColor(pinnedIconView, ColorType.CLIPBOARD_PIN)

            if (isSelectionMode) {
                selectIconView.visibility = View.VISIBLE
                if (isSelected) {
                    selectIconView.setImageResource(R.drawable.ic_check_circle_filled)
                    selectIconView.alpha = 1.0f
                    colors.setColor(selectIconView, ColorType.ACTION_KEY_BACKGROUND)
                    selectedOverlay?.visibility = View.VISIBLE
                } else {
                    selectIconView.setImageResource(R.drawable.ic_check_circle_outline)
                    selectIconView.alpha = 0.5f
                    colors.setColor(selectIconView, ColorType.KEY_ICON)
                    selectedOverlay?.visibility = View.GONE
                }
            } else {
                selectIconView.visibility = View.GONE
                selectedOverlay?.visibility = View.GONE
            }

            if (historyEntry?.imageUri != null) {
                contentView.visibility = View.GONE
                imageContainer.visibility = View.VISIBLE
                val file = java.io.File(historyEntry.imageUri)
                if (file.exists()) {
                    imageView.setImageURI(android.net.Uri.fromFile(file))
                    imageNameView.text = file.name
                }
            } else {
                contentView.visibility = View.VISIBLE
                imageContainer.visibility = View.GONE
                contentView.text = historyEntry?.text?.take(1000) // truncate displayed text for performance reasons
            }
            pinnedIconView.visibility = if (historyEntry?.isPinned == true) View.VISIBLE else View.GONE
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            if (isSelectionMode) return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    keyEventListener.onKeyDown(view.tag as Long)
                    view.animate()
                        .scaleX(0.97f)
                        .scaleY(0.97f)
                        .setDuration(90)
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (view.scaleX != 1.0f) {
                        view.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(120)
                            .start()
                    }
                }
            }
            return false
        }

        override fun onClick(view: View) {
            val id = view.tag as? Long ?: return
            if (isSelectionMode) {
                toggleSelection(id)
                return
            }
            keyEventListener.onKeyUp(id)
        }

        override fun onLongClick(view: View): Boolean {
            val id = view.tag as? Long ?: return false
            if (isSelectionMode) {
                toggleSelection(id)
                return true
            }
            val entry = getItemById(id) ?: return false
            view.isPressed = false
            return onClipLongClickListener?.invoke(entry, view) ?: false
        }
    }
}
