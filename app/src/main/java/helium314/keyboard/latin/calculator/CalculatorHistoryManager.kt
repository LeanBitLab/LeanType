// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.calculator

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import helium314.keyboard.latin.utils.prefs
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(
    val id: Long,
    val expression: String,
    val result: String,
    val timestamp: Long
)

class CalculatorHistoryManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.prefs()
    private val memoryHistory = mutableListOf<HistoryEntry>()

    init {
        loadHistoryFromPrefs()
    }

    @Synchronized
    fun addEntry(expression: String, result: String) {
        if (expression.isBlank() || result.isBlank() || result == "Error") return
        val entry = HistoryEntry(
            id = System.currentTimeMillis(),
            expression = expression.trim(),
            result = result.trim(),
            timestamp = System.currentTimeMillis()
        )
        // Remove identical duplicate if it was the immediately preceding entry
        if (memoryHistory.isNotEmpty() && memoryHistory.first().expression == entry.expression) {
            memoryHistory.removeAt(0)
        }
        memoryHistory.add(0, entry)
        if (memoryHistory.size > MAX_HISTORY_ITEMS) {
            memoryHistory.removeAt(memoryHistory.size - 1)
        }
        saveHistoryToPrefs()
    }

    @Synchronized
    fun getHistory(): List<HistoryEntry> {
        return memoryHistory.toList()
    }

    @Synchronized
    fun getLastAnswer(): String? {
        return memoryHistory.firstOrNull()?.result
    }

    @Synchronized
    fun clearHistory() {
        memoryHistory.clear()
        prefs.edit { remove(PREF_CALCULATOR_HISTORY) }
    }

    private fun loadHistoryFromPrefs() {
        val jsonString = prefs.getString(PREF_CALCULATOR_HISTORY, null) ?: return
        try {
            val jsonArray = JSONArray(jsonString)
            memoryHistory.clear()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                memoryHistory.add(
                    HistoryEntry(
                        id = obj.optLong("id", System.currentTimeMillis()),
                        expression = obj.optString("expression", ""),
                        result = obj.optString("result", ""),
                        timestamp = obj.optLong("timestamp", 0L)
                    )
                )
            }
        } catch (_: Exception) {
            // Ignore corrupted JSON
        }
    }

    private fun saveHistoryToPrefs() {
        try {
            val jsonArray = JSONArray()
            for (entry in memoryHistory) {
                val obj = JSONObject().apply {
                    put("id", entry.id)
                    put("expression", entry.expression)
                    put("result", entry.result)
                    put("timestamp", entry.timestamp)
                }
                jsonArray.put(obj)
            }
            prefs.edit { putString(PREF_CALCULATOR_HISTORY, jsonArray.toString()) }
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val PREF_CALCULATOR_HISTORY = "pref_calculator_history_json"
        private const val MAX_HISTORY_ITEMS = 30

        @SuppressLint("StaticFieldLeak") // holds the application context
        @Volatile
        private var instance: CalculatorHistoryManager? = null

        fun getInstance(context: Context): CalculatorHistoryManager {
            return instance ?: synchronized(this) {
                val appCtx = runCatching { context.applicationContext }.getOrNull() ?: context
                instance ?: CalculatorHistoryManager(appCtx).also { instance = it }
            }
        }
    }
}
