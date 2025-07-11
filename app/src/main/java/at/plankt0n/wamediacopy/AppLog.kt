package at.plankt0n.wamediacopy

import android.content.Context
import android.preference.PreferenceManager
import android.text.format.DateFormat

object AppLog {
    private const val PREF_LOG = "logs"
    private const val MAX_LINES = 200

    fun add(context: Context, msg: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val existing = prefs.getString(PREF_LOG, "") ?: ""
        val time = DateFormat.format("yyyy-MM-dd HH:mm:ss", System.currentTimeMillis())
        val newEntry = "$time $msg"
        val lines = if (existing.isBlank()) emptyList() else existing.split('\n')
        val updated = (lines + newEntry).takeLast(MAX_LINES).joinToString("\n")
        prefs.edit().putString(PREF_LOG, updated).apply()
    }

    fun get(context: Context): List<String> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val data = prefs.getString(PREF_LOG, "") ?: ""
        return if (data.isBlank()) emptyList() else data.split('\n')
    }

    fun clear(context: Context) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().remove(PREF_LOG).apply()
    }
}
