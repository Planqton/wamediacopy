package at.plankt0n.wamediacopy

import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher

class BlacklistFragment : Fragment() {

    private lateinit var listLayout: LinearLayout
    private lateinit var clearButton: Button
    private lateinit var deleteSelected: Button
    private lateinit var exportSelected: Button
    private lateinit var exportAll: Button
    private lateinit var importButton: Button
    private lateinit var countText: TextView
    private lateinit var importLauncher: ActivityResultLauncher<Array<String>>
    private val copied = mutableListOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_blacklist, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listLayout = view.findViewById(R.id.layout_copied)
        clearButton = view.findViewById(R.id.button_clear_all)
        deleteSelected = view.findViewById(R.id.button_delete_selected)
        exportSelected = view.findViewById(R.id.button_export_selected)
        exportAll = view.findViewById(R.id.button_export_all)
        importButton = view.findViewById(R.id.button_import)
        countText = view.findViewById(R.id.text_count)

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { handleImport(it, prefs) }
        }
        copied.clear()
        copied.addAll(prefs.getStringSet(FileCopyWorker.PREF_COPIED, emptySet()) ?: emptySet())
        refresh(prefs)

        clearButton.setOnClickListener {
            Log.d(TAG, "clear all pressed")
            copied.clear()
            prefs.edit().remove(FileCopyWorker.PREF_COPIED).apply()
            refresh(prefs)
        }

        deleteSelected.setOnClickListener {
            val selected = selectedItems()
            copied.removeAll(selected)
            prefs.edit().putStringSet(FileCopyWorker.PREF_COPIED, copied.toSet()).apply()
            refresh(prefs)
        }

        exportAll.setOnClickListener { share(copied) }

        exportSelected.setOnClickListener { share(selectedItems()) }

        importButton.setOnClickListener {
            importLauncher.launch(arrayOf("text/plain"))
        }
    }

    private fun refresh(prefs: android.content.SharedPreferences) {
        listLayout.removeAllViews()
        for (uri in copied) {
            val cb = CheckBox(requireContext()).apply { text = Uri.decode(uri) }
            listLayout.addView(cb)
        }
        countText.text = "Count: ${copied.size}"
    }

    private fun selectedItems(): List<String> {
        val res = mutableListOf<String>()
        for (i in 0 until listLayout.childCount) {
            val view = listLayout.getChildAt(i)
            if (view is CheckBox && view.isChecked) {
                res.add(view.text.toString())
            }
        }
        return res
    }

    private fun share(items: Collection<String>) {
        if (items.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, items.joinToString("\n"))
        }
        startActivity(Intent.createChooser(intent, "Export"))
    }

    private fun handleImport(uri: Uri, prefs: android.content.SharedPreferences) {
        try {
            requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val lines = reader.readLines()
                copied.addAll(lines)
                copied.sort()
                prefs.edit().putStringSet(FileCopyWorker.PREF_COPIED, copied.toSet()).apply()
                refresh(prefs)
            }
        } catch (e: Exception) {
            Log.w(TAG, "import failed", e)
        }
    }

    companion object {
        const val TAG = "BlacklistFrag"
    }
}
