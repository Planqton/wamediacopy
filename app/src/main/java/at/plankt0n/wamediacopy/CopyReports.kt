package at.plankt0n.wamediacopy

import android.content.Context
import android.preference.PreferenceManager
import android.text.format.DateFormat

object CopyReports {
    private const val PREF_REPORTS = "reports"
    private const val MAX_LINES = 100

    fun add(
        context: Context,
        start: Long,
        end: Long,
        copied: Long,
        old: Long,
        exist: Long,
        black: Long
    ) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val existing = prefs.getString(PREF_REPORTS, "") ?: ""
        val startStr = DateFormat.format("yyyy-MM-dd HH:mm:ss", start)
        val endStr = DateFormat.format("yyyy-MM-dd HH:mm:ss", end)
        val line = "$startStr - $endStr Copied:$copied Skipped old:$old Skipped existed:$exist Skipped blacklisted:$black"
        val lines = if (existing.isBlank()) emptyList() else existing.split('\n')
        val updated = (lines + line).takeLast(MAX_LINES).joinToString("\n")
        prefs.edit().putString(PREF_REPORTS, updated).apply()
    }

    fun get(context: Context): List<String> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val data = prefs.getString(PREF_REPORTS, "") ?: ""
        return if (data.isBlank()) emptyList() else data.split('\n')
    }

    fun clear(context: Context) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().remove(PREF_REPORTS).apply()
    }
}

