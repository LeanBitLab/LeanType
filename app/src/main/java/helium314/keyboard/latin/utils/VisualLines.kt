// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

/**
 * Helpers to turn the character geometry an editor reports (`InputConnection.requestTextBoundsInfo`, Android 14+)
 * into the visual line boundaries the delete swipe uses. Kept free of Android classes so it can be unit tested.
 */
object VisualLines {
    /**
     * Offsets (absolute, ascending) of the first character of every visual line in a range of characters.
     *
     * [tops] and [bottoms] hold the vertical extent of each character, index 0 being the character at [start].
     * A new line begins after a line feed, or when the vertical centre of a character lies outside the
     * vertical extent of the line collected so far. The first entry is always [start], which is only a real
     * line start if the range happens to begin at one (the caller checks that).
     */
    fun lineStarts(start: Int, tops: FloatArray, bottoms: FloatArray, lineFeed: BooleanArray): List<Int> {
        val n = minOf(tops.size, bottoms.size, lineFeed.size)
        if (n == 0) return emptyList()
        val starts = mutableListOf(start)
        var lineTop = tops[0]
        var lineBottom = bottoms[0]
        for (i in 1 until n) {
            val centerY = (tops[i] + bottoms[i]) / 2
            val newLine = lineFeed[i - 1] || centerY > lineBottom || centerY < lineTop
            if (newLine) {
                starts.add(start + i)
                lineTop = tops[i]
                lineBottom = bottoms[i]
            } else {
                lineTop = minOf(lineTop, tops[i])
                lineBottom = maxOf(lineBottom, bottoms[i])
            }
        }
        return starts
    }

    /**
     * Geometry of the characters an editor reported: where each character starts and ends horizontally and
     * where the visual lines begin. Used to move the selection to the same horizontal position one line up.
     *
     * [lefts], [rights] and [lineFeed] hold one entry per character, index 0 being the character at [start].
     * [lineStarts] are the (absolute, ascending) offsets that really are the first character of a line.
     */
    class LineGeometry(
        val start: Int,
        private val lefts: FloatArray,
        private val rights: FloatArray,
        private val lineFeed: BooleanArray,
        val lineStarts: List<Int>,
    ) {
        val end: Int get() = start + lefts.size

        /**
         * Index in [lineStarts] of the line containing [offset], or -1 if it is before the first known line.
         * If the text ends with a line feed, its last line is empty, so the editor reports no character (and no
         * line start) for it: [end] right after a line feed is on that line, whose index is `lineStarts.size`.
         */
        fun lineIndexOf(offset: Int): Int {
            if (lineStarts.isEmpty() || offset < lineStarts[0]) return -1
            if (offset == end && end > start && lineFeed[end - start - 1]) return lineStarts.size
            var lo = 0
            var hi = lineStarts.size - 1
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                if (lineStarts[mid] <= offset) lo = mid else hi = mid - 1
            }
            return lo
        }

        /** Horizontal position of the caret at [offset]. */
        fun xAt(offset: Int): Float = when {
            offset in start until end -> lefts[offset - start]
            offset == end && offset > start -> if (lineFeed[offset - start - 1]) (lefts.minOrNull() ?: 0f) else rights[offset - start - 1]
            else -> lefts.minOrNull() ?: 0f
        }

        /** Offset on the line with index [line] (in [lineStarts]) whose horizontal position is closest to [x]. */
        fun columnOffset(line: Int, x: Float): Int {
            if (line >= lineStarts.size) return end // the empty last line, see lineIndexOf
            val lineStart = lineStarts[line]
            val lineEnd = if (line + 1 < lineStarts.size) lineStarts[line + 1] else end
            if (lineEnd <= lineStart) return lineStart
            var best = lineStart
            var bestDistance = Math.abs(lefts[lineStart - start] - x)
            for (o in lineStart + 1 until lineEnd) {
                val d = Math.abs(lefts[o - start] - x)
                if (d < bestDistance) {
                    best = o
                    bestDistance = d
                }
            }
            return best
        }

        /**
         * Boundaries for the delete swipe: index 0 = [baseStart], then for every line above the one containing
         * [baseStart] the offset closest to [x]; finally the start of the text if the first line starts there.
         */
        fun boundaries(baseStart: Int, x: Float): List<Int> {
            val result = mutableListOf(baseStart)
            var line = lineIndexOf(baseStart) - 1
            while (line >= 0) {
                result.add(columnOffset(line, x))
                line--
            }
            if (lineStarts.firstOrNull() == 0 && result.last() > 0) result.add(0)
            return result
        }
    }
}
