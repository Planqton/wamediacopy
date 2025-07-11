package at.plankt0n.wamediacopy

import android.content.Context
import android.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

/** Stores a reference to each copy report log file. */
object ReportStore {
    private const val PREF_REPORTS = "copyReports"
    private const val MAX_REPORTS = 20

    data class CopyReport(
        val timestamp: Long,
        val file: String,
        val copied: Int,
        val old: Int,
        val skipped: Int,
    )

    fun add(context: Context, report: CopyReport) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val arr = JSONArray(prefs.getString(PREF_REPORTS, "[]"))
        val obj = JSONObject().apply {
            put("ts", report.timestamp)
            put("file", report.file)
            put("c", report.copied)
            put("o", report.old)
            put("s", report.skipped)
        }
        arr.put(obj)
        val trimmed = if (arr.length() > MAX_REPORTS) {
            val newArr = JSONArray()
            for (i in arr.length() - MAX_REPORTS until arr.length()) {
                newArr.put(arr.getJSONObject(i))
            }
            newArr
        } else arr
        prefs.edit().putString(PREF_REPORTS, trimmed.toString()).apply()
    }

    fun get(context: Context): List<CopyReport> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val arr = JSONArray(prefs.getString(PREF_REPORTS, "[]"))
        val res = mutableListOf<CopyReport>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            res.add(
                CopyReport(
                    obj.getLong("ts"),
                    obj.getString("file"),
                    obj.optInt("c"),
                    obj.optInt("o"),
                    obj.optInt("s"),
                )
            )
        }
        return res
    }

    fun clear(context: Context) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .remove(PREF_REPORTS).apply()
    }
}
