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

        // 1. Direct Cartesian hit-test for wide, modifier, and functional keys (Spacebar, Shift, Enter, Delete, etc.)
        for (key in keyboard.getNearestKeys(touchX, touchY)) {
            if (isSpecialOrWide(key)) {
                if (key.isOnKey(touchX, touchY)) {
                    return key
                }
            }
        }

        // 2. Exact inside-hexagon test for letter keys
        var bestHexKey: Key? = null
        var minDistance = Int.MAX_VALUE

        for (key in keyboard.getNearestKeys(touchX, touchY)) {
            if (key.isSpacer || isSpecialOrWide(key)) continue
            val cx = key.x + key.width / 2
            val cy = key.y + key.height / 2
            val dx = touchX - cx
            val dy = touchY - cy
            val dist = dx * dx + dy * dy

            if (isInsideHexagon(touchX, touchY, key.x, key.y, key.width, key.height)) {
                if (dist < minDistance) {
                    minDistance = dist
                    bestHexKey = key
                }
            }
        }

        if (bestHexKey != null) return bestHexKey

        // 3. Fallback: find nearest hex letter key by center distance among candidate keys
        for (key in keyboard.getNearestKeys(touchX, touchY)) {
            if (key.isSpacer || isSpecialOrWide(key)) continue
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

    private fun isSpecialOrWide(key: Key): Boolean {
        return key.hasFunctionalBackground() ||
                key.hasActionKeyBackground() ||
                key.backgroundType == Key.BACKGROUND_TYPE_SPACEBAR ||
                key.isModifier() ||
                key.code <= 0 ||
                key.code == Constants.CODE_SPACE ||
                key.width > key.height * 1.3f
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
