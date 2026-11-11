// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.cjk

import android.content.Context

/**
 * In-memory mock CJK Pinyin engine for testing the composition pipeline, UI candidate strip,
 * and key interception without requiring native librime binaries.
 */
class MockPinyinEngine : ICjkEngine {
    private var isReady = false
    private val buffer = StringBuilder()
    private var pendingCommit: String? = null
    private var cachedContext: CjkContextData? = null

    // Sample dictionary for testing common syllables and phrases
    private val dictionary: Map<String, List<String>> = mapOf(
        "ni" to listOf("你", "泥", "拟", "逆", "妮"),
        "hao" to listOf("好", "号", "毫", "浩", "耗"),
        "nihao" to listOf("你好", "拟好"),
        "zhong" to listOf("中", "种", "重", "钟"),
        "guo" to listOf("国", "过", "果", "裹"),
        "zhongguo" to listOf("中国"),
        "wo" to listOf("我", "握", "窝"),
        "men" to listOf("们", "门", "闷"),
        "women" to listOf("我们"),
        "ta" to listOf("他", "她", "它"),
        "shi" to listOf("是", "时", "事", "十", "市"),
        "de" to listOf("的", "得", "地"),
        "xian" to listOf("先", "现", "线", "西安"),
        "leantype" to listOf("LeanType")
    )

    override fun init(context: Context, sharedDir: String, userDir: String): Boolean {
        isReady = true
        return true
    }

    override fun isAvailable(): Boolean = isReady

    override fun processKey(keysym: Int, mask: Int): Boolean {
        if (!isReady) return false

        when (keysym) {
            KeycodeTranslator.XK_BACKSPACE -> {
                if (buffer.isNotEmpty()) {
                    buffer.deleteCharAt(buffer.length - 1)
                    updateContext()
                    return true
                }
                return false
            }

            KeycodeTranslator.XK_RETURN -> {
                if (buffer.isNotEmpty()) {
                    // Enter commits raw pre-edit Latin text directly
                    pendingCommit = buffer.toString()
                    buffer.clear()
                    cachedContext = null
                    return true
                }
                return false
            }

            KeycodeTranslator.XK_SPACE -> {
                if (buffer.isNotEmpty()) {
                    val candidates = cachedContext?.candidates
                    if (!candidates.isNullOrEmpty()) {
                        pendingCommit = candidates.first().text
                    } else {
                        pendingCommit = buffer.toString()
                    }
                    buffer.clear()
                    cachedContext = null
                    return true
                }
                return false
            }

            in 0x31..0x39 -> { // Numbers '1'..'9'
                if (buffer.isNotEmpty()) {
                    val index = keysym - 0x31
                    val candidates = cachedContext?.candidates
                    if (candidates != null && index in candidates.indices) {
                        pendingCommit = candidates[index].text
                        buffer.clear()
                        cachedContext = null
                        return true
                    }
                }
            }
        }

        // Printable ASCII letters (preserving case for uppercase input)
        val ch = keysym.toChar()
        if (ch in 'a'..'z' || ch in 'A'..'Z') {
            buffer.append(ch)
            updateContext()
            return true
        }

        // Single quote as syllable separator
        if (ch == '\'') {
            buffer.append('\'')
            updateContext()
            return true
        }

        return false
    }

    private fun updateContext() {
        if (buffer.isEmpty()) {
            cachedContext = null
            return
        }

        val raw = buffer.toString()
        val lookupKey = raw.replace("'", "")
        val words = dictionary[lookupKey.lowercase()] ?: listOf(raw)
        val candidateItems = words.mapIndexed { idx, word ->
            CjkCandidate(index = idx, text = word)
        }

        cachedContext = CjkContextData(
            preedit = raw,
            candidates = candidateItems,
            selectedIndex = 0,
            hasPrevPage = false,
            hasNextPage = false
        )
    }

    override fun getContext(): CjkContextData? = cachedContext

    override fun getCommit(): String? {
        val commit = pendingCommit
        pendingCommit = null
        return commit
    }

    override fun selectCandidate(index: Int): Boolean {
        val candidates = cachedContext?.candidates ?: return false
        if (index in candidates.indices) {
            pendingCommit = candidates[index].text
            buffer.clear()
            cachedContext = null
            return true
        }
        return false
    }

    override fun clearComposition() {
        buffer.clear()
        pendingCommit = null
        cachedContext = null
    }

    override fun destroySession() {
        clearComposition()
    }

    override fun cleanup() {
        clearComposition()
        isReady = false
    }
}
