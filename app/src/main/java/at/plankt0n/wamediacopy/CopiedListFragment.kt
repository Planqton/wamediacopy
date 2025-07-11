package at.plankt0n.wamediacopy

import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment

class CopiedListFragment : Fragment() {

    private lateinit var listLayout: LinearLayout
    private lateinit var clearButton: Button
    private val copied = mutableListOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_copied_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listLayout = view.findViewById(R.id.layout_copied)
        clearButton = view.findViewById(R.id.button_clear_all)

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        copied.clear()
        copied.addAll(prefs.getStringSet(FileCopyWorker.PREF_COPIED, emptySet()) ?: emptySet())
        refresh(prefs)

        clearButton.setOnClickListener {
            Log.d(TAG, "clear all pressed")
            copied.clear()
            prefs.edit().remove(FileCopyWorker.PREF_COPIED).apply()
            refresh(prefs)
        }
    }

    private fun refresh(prefs: android.content.SharedPreferences) {
        listLayout.removeAllViews()
        for (uri in copied) {
            val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
            val tv = TextView(requireContext()).apply {
                text = uri
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val remove = Button(requireContext()).apply { text = "X" }
            remove.setOnClickListener {
                Log.d(TAG, "remove copied $uri")
                copied.remove(uri)
                prefs.edit().putStringSet(FileCopyWorker.PREF_COPIED, copied.toSet()).apply()
                refresh(prefs)
            }
            row.addView(tv)
            row.addView(remove)
            listLayout.addView(row)
        }
    }

    companion object {
        const val TAG = "CopiedListFrag"
    }
}
