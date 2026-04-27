fun main() {
    val smiley = "\uD83D\uDE00"
    println("Smiley length: ${smiley.length}")
    for (i in 0 until smiley.length) {
        val codeUnit = smiley[i].code
        println(String.format("%%06x", codeUnit))
    }
}
