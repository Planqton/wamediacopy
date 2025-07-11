package at.plankt0n.wamediacopy

import android.content.Context
import android.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

/** Stores details about each copy run */
object ReportStore {
    private const val PREF_REPORTS = "copyReports"
    private const val MAX_REPORTS = 20

    data class CopyReport(
        val timestamp: Long,
        val copied: List<String>,
        val old: List<String>,
        val blacklisted: List<String>,
    )

    fun add(context: Context, report: CopyReport) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val arr = JSONArray(prefs.getString(PREF_REPORTS, "[]"))
        val obj = JSONObject().apply {
            put("ts", report.timestamp)
            put("copied", JSONArray(report.copied))
            put("old", JSONArray(report.old))
            put("black", JSONArray(report.blacklisted))
        }
        arr.put(obj)
        if (arr.length() > MAX_REPORTS) {
            val newArr = JSONArray()
            for (i in arr.length() - MAX_REPORTS until arr.length()) {
                newArr.put(arr.getJSONObject(i))
            }
            prefs.edit().putString(PREF_REPORTS, newArr.toString()).apply()
        } else {
            prefs.edit().putString(PREF_REPORTS, arr.toString()).apply()
        }
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
                    obj.getJSONArray("copied").toList(),
                    obj.getJSONArray("old").toList(),
                    obj.getJSONArray("black").toList(),
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

private fun JSONArray.toList(): List<String> {
    val res = mutableListOf<String>()
    for (i in 0 until length()) {
        res.add(getString(i))
    }
    return res
}
