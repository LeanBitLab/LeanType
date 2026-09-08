// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.keyboard.clipboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import helium314.keyboard.latin.ClipboardHistoryEntry
import helium314.keyboard.latin.ClipboardHistoryManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import androidx.core.view.isVisible
import androidx.core.view.isInvisible

class ClipboardHistoryRecyclerView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    var placeholderView: View? = null
    val historyManager: ClipboardHistoryManager? get() = (adapter as? ClipboardAdapter?)?.clipboardHistoryManager

    // Undo state
    private var undoBar: View? = null
    private var lastDeletedEntry: ClipboardHistoryEntry? = null
    private val undoHandler = Handler(Looper.getMainLooper())
    private val undoDismissRunnable = Runnable { dismissUndoBar() }

    @Suppress("unused")
    private val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
        override fun onMove(recyclerView: RecyclerView, viewHolder: ViewHolder, target: ViewHolder) = false
        override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: ViewHolder): Int {
            val position = viewHolder.absoluteAdapterPosition
            val entry = (adapter as? ClipboardAdapter)?.getItem(position) ?: return 0
            val cacheIndex = historyManager?.getClips()?.indexOfFirst { it.id == entry.id } ?: -1
            if (cacheIndex == -1) return 0
            var dirs = 0
            if (historyManager?.canRemove(cacheIndex) == true) {
                dirs = dirs or ItemTouchHelper.LEFT
            }
            if (entry.imageUri == null && !entry.text.isNullOrEmpty()) {
                dirs = dirs or ItemTouchHelper.RIGHT
            }
            return dirs
        }
        override fun onSwiped(viewHolder: ViewHolder, dir: Int) {
            val position = viewHolder.absoluteAdapterPosition
            val entry = (adapter as? ClipboardAdapter)?.getItem(position)
            if (entry != null) {
                val cacheIndex = historyManager?.getClips()?.indexOfFirst { it.id == entry.id } ?: -1
                if (cacheIndex != -1) {
                    if (dir == ItemTouchHelper.LEFT) {
                        val deletedEntry = historyManager?.removeEntry(cacheIndex)
                        if (deletedEntry != null) {
                            (adapter as? ClipboardAdapter)?.removeDisplayItem(position)
                            adapter?.notifyItemRemoved(position)
                            showUndoBar(deletedEntry)
                        }
                        return
                    } else if (dir == ItemTouchHelper.RIGHT) {
                        adapter?.notifyItemChanged(position)
                        showEditDialog(entry)
                        return
                    }
                }
            }
            // fallback in case entry or index was invalid
            adapter?.notifyItemChanged(position)
        }
    }).attachToRecyclerView(this)

    private fun showEditDialog(entry: ClipboardHistoryEntry) {
        var view = parent
        while (view != null && view !is ClipboardHistoryView) {
            view = (view as? android.view.View)?.parent
        }
        (view as? ClipboardHistoryView)?.startEditMode(entry)
    }

    private fun showUndoBar(entry: ClipboardHistoryEntry) {
        // Cancel any pending dismiss from a previous undo
        undoHandler.removeCallbacks(undoDismissRunnable)
        lastDeletedEntry = entry

        // Dismiss confirmation bar if active
        (parent as? View)?.findViewById<View>(R.id.clipboard_confirmation_bar)?.visibility = View.GONE

        // Find the undo bar from our parent hierarchy (it's a sibling in the FrameLayout)
        val bar = undoBar ?: (parent as? View)?.findViewById<View>(R.id.clipboard_undo_bar)
        undoBar = bar ?: return

        // Apply theme colors
        try {
            val colors = Settings.getValues().mColors
            colors.setBackground(bar, ColorType.CLIPBOARD_SUGGESTION_BACKGROUND)
            bar.findViewById<TextView>(R.id.clipboard_undo_text)?.setTextColor(colors.get(ColorType.KEY_TEXT))
            bar.findViewById<TextView>(R.id.clipboard_undo_button)?.setTextColor(colors.get(ColorType.KEY_TEXT))
        } catch (_: Exception) { /* colors may not be available */ }

        bar.visibility = View.VISIBLE

        bar.findViewById<View>(R.id.clipboard_undo_button)?.setOnClickListener {
            lastDeletedEntry?.let { deletedEntry ->
                historyManager?.restoreEntry(deletedEntry)
                // The listener in the DAO will trigger onClipInserted, which notifies the adapter
            }
            dismissUndoBar()
        }

        // Auto-dismiss after 5 seconds
        undoHandler.postDelayed(undoDismissRunnable, 5000)
    }

    fun dismissUndoBar() {
        undoHandler.removeCallbacks(undoDismissRunnable)
        undoBar?.visibility = View.GONE
        lastDeletedEntry = null
    }

    private val adapterDataObserver: AdapterDataObserver = object : AdapterDataObserver() {
        override fun onChanged() {
            checkAdapterContentChange()
        }

        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
            checkAdapterContentChange()
        }

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            checkAdapterContentChange()
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            checkAdapterContentChange()
        }
    }

    private fun checkAdapterContentChange() {
        val placeholder = placeholderView ?: return
        val adapterIsEmpty = adapter == null || adapter?.itemCount == 0
        if (isVisible && adapterIsEmpty) {
            placeholder.visibility = VISIBLE
            visibility = INVISIBLE
        } else if (isInvisible && !adapterIsEmpty) {
            placeholder.visibility = INVISIBLE
            visibility = VISIBLE
        }
    }

    override fun setAdapter(adapter: Adapter<*>?) {
        this.adapter?.unregisterAdapterDataObserver(adapterDataObserver)
        super.setAdapter(adapter)
        checkAdapterContentChange()
        adapter?.registerAdapterDataObserver(adapterDataObserver)
    }

}
