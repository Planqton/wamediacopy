package at.plankt0n.wamediacopy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.fragment.app.Fragment

class LogsFragment : Fragment() {

    private lateinit var logList: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var clearButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_logs, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        logList = view.findViewById(R.id.list_logs)
        clearButton = view.findViewById(R.id.button_clear_logs)

        adapter = ArrayAdapter(requireContext(), R.layout.item_log, mutableListOf())
        logList.adapter = adapter

        clearButton.setOnClickListener {
            AppLog.clear(requireContext())
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val lines = AppLog.get(requireContext()).asReversed()
        adapter.clear()
        adapter.addAll(lines)
    }
}
