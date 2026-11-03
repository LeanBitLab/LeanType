package helium314.keyboard.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.widget.EditText

class InlineCursorTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : EditText(context, attrs, defStyleAttr) {

    init {
        isCursorVisible = false
    }

    var showInlineCursor: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var cursorPos: Int = 0
        set(value) {
            if (field != value) {
                field = value
                if (value in 0..text.length) {
                    setSelection(value)
                }
                invalidate()
            }
        }

    var cursorColor: Int = Color.WHITE
        set(value) {
            if (field != value) {
                field = value
                cursorPaint.color = value
                selectionHighlightColor = (value and 0x00FFFFFF) or 0x50000000
                invalidate()
            }
        }

    var selectionHighlightColor: Int = 0x50000000
        set(value) {
            if (field != value) {
                field = value
                highlightPaint.color = value
                invalidate()
            }
        }

    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = selectionHighlightColor
    }
    private val cursorWidth = (2 * resources.displayMetrics.density).coerceAtLeast(2f)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val l = layout
        val len = l?.text?.length ?: text.length
        val selStart = selectionStart
        val selEnd = selectionEnd

        if (selStart in 0..len && selEnd in 0..len && selStart != selEnd && l != null) {
            val start = minOf(selStart, selEnd)
            val end = maxOf(selStart, selEnd)
            val startLine = l.getLineForOffset(start)
            val endLine = l.getLineForOffset(end)
            val saveCount = canvas.save()
            for (line in startLine..endLine) {
                val left = totalPaddingLeft + (if (line == startLine) l.getPrimaryHorizontal(start) else l.getLineLeft(line))
                val right = totalPaddingLeft + (if (line == endLine) l.getPrimaryHorizontal(end) else l.getLineRight(line))
                val top = totalPaddingTop + l.getLineTop(line).toFloat()
                val bottom = totalPaddingTop + l.getLineBottom(line).toFloat()
                canvas.drawRect(minOf(left, right), top, maxOf(left, right), bottom, highlightPaint)
            }
            canvas.restoreToCount(saveCount)
            return
        }

        if (!showInlineCursor) return
        val effectivePos = if (selectionStart in 0..len) selectionStart else cursorPos
        val safePos = effectivePos.coerceIn(0, len)
        val cursorX = totalPaddingLeft + (if (text.isNotEmpty() && l != null) l.getPrimaryHorizontal(safePos) else 0f)
        val lineTop: Float
        val lineBottom: Float
        if (l != null && l.lineCount > 0) {
            val line = l.getLineForOffset(safePos)
            lineTop = totalPaddingTop + l.getLineTop(line).toFloat()
            lineBottom = totalPaddingTop + l.getLineBottom(line).toFloat()
        } else {
            val midY = height / 2f
            val halfH = textSize * 0.6f
            lineTop = midY - halfH
            lineBottom = midY + halfH
        }
        val inset = (lineBottom - lineTop) * 0.15f
        val top = lineTop + inset
        val bottom = lineBottom - inset

        val clipLeft = scrollX.toFloat()
        val clipTop = scrollY + totalPaddingTop.toFloat()
        val clipRight = scrollX + width.toFloat()
        val clipBottom = scrollY + (height - totalPaddingBottom).toFloat()
        if (clipLeft >= clipRight || clipTop >= clipBottom) return

        val saveCount = canvas.save()
        canvas.clipRect(clipLeft, clipTop, clipRight, clipBottom)
        canvas.drawRect(cursorX, top, cursorX + cursorWidth, bottom, cursorPaint)
        canvas.restoreToCount(saveCount)
    }
}
