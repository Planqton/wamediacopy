package at.plankt0n.wamediacopy

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import at.plankt0n.wamediacopy.AppLog
import android.content.SharedPreferences
import android.net.Uri

class FoldersFragment : Fragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var sourcesLayout: LinearLayout
    private lateinit var destEdit: EditText
    private lateinit var addSource: Button
    private lateinit var pickDest: Button

    private lateinit var prefs: SharedPreferences

    private val sources = mutableListOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_folders, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sourcesLayout = view.findViewById(R.id.layout_sources)
        destEdit = view.findViewById(R.id.edit_dest)
        addSource = view.findViewById(R.id.button_add_source)
        pickDest = view.findViewById(R.id.button_pick_dest)

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        sources.clear()
        sources.addAll(prefs.getStringSet(FileCopyWorker.PREF_SOURCES, emptySet()) ?: emptySet())
        refreshSources(prefs)
        destEdit.setText(Uri.decode(prefs.getString(FileCopyWorker.PREF_DEST, "")))

        addSource.setOnClickListener {
            Log.d(TAG, "add source pressed")
            pickFolder(REQ_PICK_SOURCE)
        }
        pickDest.setOnClickListener {
            Log.d(TAG, "pick dest pressed")
            pickFolder(REQ_PICK_DEST)
        }

        destEdit.isEnabled = false
        updateEnabledState()
    }

    private fun pickFolder(request: Int) {
        Log.d(TAG, "pickFolder request=$request")
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, request)
    }

    private fun refreshSources(prefs: android.content.SharedPreferences) {
        sourcesLayout.removeAllViews()
        for (uri in sources) {
            val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
            val tv = TextView(requireContext()).apply {
                text = Uri.decode(uri)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val remove = Button(requireContext()).apply { text = "X" }
            remove.setOnClickListener {
                Log.d(TAG, "remove source $uri")
                sources.remove(uri)
                prefs.edit().putStringSet(FileCopyWorker.PREF_SOURCES, sources.toSet()).apply()
                refreshSources(prefs)
                AppLog.add(requireContext(), "Removed source $uri")
            }
            row.addView(tv)
            row.addView(remove)
            sourcesLayout.addView(row)
        }
        updateEnabledState()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && data != null) {
            val uri = data.data ?: return
            Log.d(TAG, "folder selected $uri for req=$requestCode")
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            when (requestCode) {
                REQ_PICK_SOURCE -> {
                    sources.add(uri.toString())
                    prefs.edit().putStringSet(FileCopyWorker.PREF_SOURCES, sources.toSet()).apply()
                    refreshSources(prefs)
                    AppLog.add(requireContext(), "Added source $uri")
                }
                REQ_PICK_DEST -> {
                    destEdit.setText(Uri.decode(uri.toString()))
                    prefs.edit().putString(FileCopyWorker.PREF_DEST, uri.toString()).apply()
                    AppLog.add(requireContext(), "Set destination $uri")
                }
            }
        }
    }

    private fun updateEnabledState() {
        val running = prefs.getBoolean(FileCopyWorker.PREF_IS_RUNNING, false)
        addSource.isEnabled = !running
        pickDest.isEnabled = !running
        destEdit.isEnabled = false
        for (i in 0 until sourcesLayout.childCount) {
            sourcesLayout.getChildAt(i).isEnabled = !running
        }
    }

    override fun onResume() {
        super.onResume()
        prefs.registerOnSharedPreferenceChangeListener(this)
        updateEnabledState()
    }

    override fun onPause() {
        super.onPause()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == FileCopyWorker.PREF_IS_RUNNING) {
            updateEnabledState()
        }
    }

    companion object {
        const val REQ_PICK_SOURCE = 1001
        const val REQ_PICK_DEST = 1002
        const val TAG = "FoldersFrag"
    }
}
