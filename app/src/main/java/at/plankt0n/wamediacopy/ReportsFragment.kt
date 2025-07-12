package at.plankt0n.wamediacopy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.fragment.app.Fragment

class ReportsFragment : Fragment() {

    private lateinit var reportList: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var clearButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_reports, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        reportList = view.findViewById(R.id.list_reports)
        clearButton = view.findViewById(R.id.button_clear_reports)

        adapter = ArrayAdapter(requireContext(), R.layout.item_log, mutableListOf())
        reportList.adapter = adapter

        clearButton.setOnClickListener {
            CopyReports.clear(requireContext())
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val lines = CopyReports.get(requireContext()).asReversed()
        adapter.clear()
        adapter.addAll(lines)
    }
}

