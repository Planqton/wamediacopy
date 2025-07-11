package at.plankt0n.wamediacopy

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment

class LogFragment : Fragment() {

    private lateinit var textLog: TextView
    private lateinit var refresh: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_log, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        textLog = view.findViewById(R.id.text_log)
        refresh = view.findViewById(R.id.button_refresh_log)
        refresh.setOnClickListener { update() }
        update()
    }

    private fun update() {
        try {
            val proc = Runtime.getRuntime().exec("logcat -d")
            val logText = proc.inputStream.bufferedReader().readText()
            textLog.text = logText
        } catch (e: Exception) {
            Log.w(TAG, "logcat read failed", e)
            textLog.text = e.toString()
        }
    }

    companion object {
        private const val TAG = "LogFragment"
    }
}
