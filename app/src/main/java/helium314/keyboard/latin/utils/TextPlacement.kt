package helium314.keyboard.latin.utils

class TextPlacement(
    @JvmField var text: String,
    @JvmField val selectionStart: Int
) {
    fun selectionEnd(): Int {
        return selectionStart + text.length
    }
}
