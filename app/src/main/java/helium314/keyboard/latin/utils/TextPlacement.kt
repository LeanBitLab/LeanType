package helium314.keyboard.latin.utils

class TextPlacement(
    var text: String,
    val selectionStart: Int
) {
    fun selectionEnd(): Int {
        return selectionStart + text.length
    }
}
