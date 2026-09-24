package helium314.keyboard.keyboard

import helium314.keyboard.latin.common.Constants
import kotlin.math.abs

open class HexKeyDetector(
    keyHysteresisDistance: Float = 0.0f,
    keyHysteresisDistanceForSlidingModifier: Float = 0.0f
) : KeyDetector(keyHysteresisDistance, keyHysteresisDistanceForSlidingModifier) {

    override fun detectHitKey(x: Int, y: Int): Key? {
        val keyboard = getKeyboard() ?: return null
        if (!keyboard.isHexagonal) {
            return super.detectHitKey(x, y)
        }

        val touchX = getTouchX(x)
        val touchY = getTouchY(y)

        // 1. Direct hit-test for non-hex keys (e.g. wide rectangular spacebar if any)
        for (key in keyboard.getNearestKeys(touchX, touchY)) {
            if (isNonHexKey(key)) {
                if (key.isOnKey(touchX, touchY)) {
                    return key
                }
            }
        }

        // 2. Exact inside-shape test for hexagonal & half-hexagonal keys
        var bestHexKey: Key? = null
        var minDistance = Int.MAX_VALUE

        for (key in keyboard.getNearestKeys(touchX, touchY)) {
            if (key.isSpacer || isNonHexKey(key)) continue
            val cx = key.x + key.width / 2
            val cy = key.y + key.height / 2
            val dx = touchX - cx
            val dy = touchY - cy
            val dist = dx * dx + dy * dy

            if (isInsideKey(touchX, touchY, key)) {
                if (dist < minDistance) {
                    minDistance = dist
                    bestHexKey = key
                }
            }
        }

        if (bestHexKey != null) return bestHexKey

        // 3. Fallback: find nearest hex key by center distance among candidate keys
        for (key in keyboard.getNearestKeys(touchX, touchY)) {
            if (key.isSpacer || isNonHexKey(key)) continue
            val cx = key.x + key.width / 2
            val cy = key.y + key.height / 2
            val dx = touchX - cx
            val dy = touchY - cy
            val dist = dx * dx + dy * dy
            if (dist < minDistance) {
                minDistance = dist
                bestHexKey = key
            }
        }

        return bestHexKey ?: super.detectHitKey(x, y)
    }

    private fun isNonHexKey(key: Key): Boolean {
        return (key.code == Constants.CODE_SPACE || key.backgroundType == Key.BACKGROUND_TYPE_SPACEBAR) && key.width > key.height * 1.4f
    }

    private fun isInsideKey(px: Int, py: Int, key: Key): Boolean {
        val lx = (px - key.x).toFloat()
        val ly = (py - key.y).toFloat()
        val w = key.width.toFloat()
        val h = key.height.toFloat()

        if (lx < 0 || lx > w || ly < 0 || ly > h) return false

        val qh = h / 4f
        return when (key.hexVariant) {
            HexVariant.HALF_LEFT -> {
                // Flat left edge, pointy right vertices (Shift)
                if (ly < qh) lx <= w * (ly / qh)
                else if (ly > h - qh) lx <= w * ((h - ly) / qh)
                else true
            }
            HexVariant.HALF_RIGHT -> {
                // Pointy left vertices, flat right edge (Delete)
                if (ly < qh) lx >= w * (1f - ly / qh)
                else if (ly > h - qh) lx >= w * (1f - (h - ly) / qh)
                else true
            }
            else -> {
                isInsideHexagon(px, py, key.x, key.y, key.width, key.height)
            }
        }
    }

    private fun isInsideHexagon(px: Int, py: Int, keyX: Int, keyY: Int, width: Int, height: Int): Boolean {
        val cx = keyX + width / 2
        val cy = keyY + height / 2
        val dx = abs(px - cx)
        val dy = abs(py - cy)
        if (dx > width / 2 || dy > height / 2) return false
        return dx * height + 2 * dy * width <= width * height
    }
}
