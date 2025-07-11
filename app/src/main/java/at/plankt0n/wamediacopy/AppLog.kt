package at.plankt0n.wamediacopy

import android.content.Context
import android.preference.PreferenceManager
import android.text.format.DateFormat

object AppLog {
    private const val PREF_LOG = "logs"

    fun add(context: Context, msg: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val existing = prefs.getString(PREF_LOG, "") ?: ""
        val time = DateFormat.format("yyyy-MM-dd HH:mm:ss", System.currentTimeMillis())
        val newEntry = "$time $msg"
        val updated = if (existing.isBlank()) newEntry else "$existing\n$newEntry"
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
