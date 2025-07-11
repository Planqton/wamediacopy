package at.plankt0n.wamediacopy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import android.text.format.DateFormat
import java.io.File

class ReportsFragment : Fragment() {

    private lateinit var reportList: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private val reports = mutableListOf<ReportStore.CopyReport>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_reports, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        reportList = view.findViewById(R.id.list_reports)
        adapter = ArrayAdapter(requireContext(), R.layout.item_log, mutableListOf())
        reportList.adapter = adapter
        reportList.setOnItemClickListener { _, _, position, _ ->
            val rep = reports[position]
            val ts = DateFormat.format("yyyy-MM-dd HH:mm", rep.timestamp)
            val text = try {
                File(rep.file).readText()
            } catch (e: Exception) {
                "Failed to read log"
            }
            AlertDialog.Builder(requireContext())
                .setTitle(ts)
                .setMessage(text)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        reports.clear()
        reports.addAll(ReportStore.get(requireContext()).asReversed())
        adapter.clear()
        adapter.addAll(reports.map { summary(it) })
    }

    private fun summary(r: ReportStore.CopyReport): String {
        val ts = DateFormat.format("yyyy-MM-dd HH:mm", r.timestamp)
        return "$ts - Copied:${r.copied} - Too Old:${r.old} - Blacklisted:${r.skipped}"
    }
}
