package at.plankt0n.wamediacopy

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment

class CopiedListFragment : Fragment() {

    private lateinit var listView: ListView
    private lateinit var exportAll: Button
    private lateinit var exportSel: Button
    private lateinit var importBtn: Button
    private lateinit var deleteAll: Button
    private lateinit var deleteSel: Button
    private val copied = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_copied_list, container, false)
    }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                requireContext().contentResolver.openInputStream(it)?.bufferedReader()?.useLines { lines ->
                    copied.clear()
                    copied.addAll(lines)
                    save()
                }
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView = view.findViewById(R.id.list_copied)
        exportAll = view.findViewById(R.id.button_export_all)
        exportSel = view.findViewById(R.id.button_export_selected)
        importBtn = view.findViewById(R.id.button_import)
        deleteAll = view.findViewById(R.id.button_delete_all)
        deleteSel = view.findViewById(R.id.button_delete_selected)

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        copied.clear()
        copied.addAll(prefs.getStringSet(FileCopyWorker.PREF_COPIED, emptySet()) ?: emptySet())

        adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_multiple_choice, copied)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE

        exportAll.setOnClickListener { share(copied) }
        exportSel.setOnClickListener { share(selectedItems()) }
        importBtn.setOnClickListener { importLauncher.launch("text/plain") }
        deleteAll.setOnClickListener { copied.clear(); save() }
        deleteSel.setOnClickListener { removeSelected(); save() }
    }

    private fun save() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        prefs.edit().putStringSet(FileCopyWorker.PREF_COPIED, copied.toSet()).apply()
        adapter.notifyDataSetChanged()
    }

    private fun selectedItems(): List<String> {
        val res = mutableListOf<String>()
        for (i in 0 until listView.count) {
            if (listView.isItemChecked(i)) res.add(copied[i])
        }
        return res
    }

    private fun removeSelected() {
        val iter = copied.listIterator()
        var i = 0
        while (iter.hasNext()) {
            iter.next()
            if (listView.isItemChecked(i)) {
                iter.remove()
            }
            i++
        }
    }

    private fun share(items: List<String>) {
        if (items.isEmpty()) return
        val text = items.joinToString("\n")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Export"))
    }

    companion object {
        private const val TAG = "CopiedListFrag"
    }
}
