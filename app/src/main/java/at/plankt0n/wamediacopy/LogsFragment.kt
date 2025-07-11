package at.plankt0n.wamediacopy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment

class LogsFragment : Fragment() {

    private lateinit var logText: TextView
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
        logText = view.findViewById(R.id.text_logs)
        clearButton = view.findViewById(R.id.button_clear_logs)

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
        logText.text = AppLog.get(requireContext()).joinToString("\n")
    }
}
