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
     * line start if the range happens to begin at one (see [buildBoundaries]).
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
     * Boundaries for the delete swipe in the same layout as `getLineBoundariesBackwards`:
     * index 0 = [baseStart], then the start of the line the selection begins in, then the lines above it.
     *
     * [exactStarts] are the line starts from [lineStarts]. If [firstIsRealLineStart] is false the range began in the
     * middle of a line, so its first entry is not a real line start and is dropped. Lines older than the exact
     * ones are continued from [approxBoundaries] (the estimate), so swiping further up than the editor was asked
     * about still works. Entries below [minOffset] are not used (that is as far back as the swipe knows the text).
     */
    fun buildBoundaries(
        baseStart: Int,
        exactStarts: List<Int>,
        firstIsRealLineStart: Boolean,
        approxBoundaries: List<Int>,
        minOffset: Int,
    ): List<Int> {
        val result = mutableListOf(baseStart)
        val usable = if (firstIsRealLineStart) exactStarts else exactStarts.drop(1)
        for (s in usable.asReversed()) {
            if (s < result.last() && s >= minOffset) result.add(s)
        }
        for (b in approxBoundaries) {
            if (b < result.last() && b >= minOffset) result.add(b)
        }
        return result
    }
}
