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
    private lateinit var existLayout: LinearLayout
    private lateinit var destEdit: EditText
    private lateinit var addSource: Button
    private lateinit var addExist: Button
    private lateinit var pickDest: Button

    private lateinit var prefs: SharedPreferences

    private val sources = mutableListOf<String>()
    private val existing = mutableListOf<String>()

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
        existLayout = view.findViewById(R.id.layout_exist)
        destEdit = view.findViewById(R.id.edit_dest)
        addSource = view.findViewById(R.id.button_add_source)
        addExist = view.findViewById(R.id.button_add_exist)
        pickDest = view.findViewById(R.id.button_pick_dest)

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        sources.clear()
        sources.addAll(prefs.getStringSet(FileCopyWorker.PREF_SOURCES, emptySet()) ?: emptySet())
        refreshSources(prefs)
        existing.clear()
        existing.addAll(prefs.getStringSet(FileCopyWorker.PREF_EXISTING_DIRS, emptySet()) ?: emptySet())
        refreshExisting(prefs)
        destEdit.setText(Uri.decode(prefs.getString(FileCopyWorker.PREF_DEST, "")))
        destEdit.setSingleLine(true)
        destEdit.setHorizontallyScrolling(true)

        addSource.setOnClickListener {
            Log.d(TAG, "add source pressed")
            pickFolder(REQ_PICK_SOURCE)
        }
        addExist.setOnClickListener {
            Log.d(TAG, "add existing pressed")
            pickFolder(REQ_PICK_EXIST)
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
            }
            val scroll = android.widget.HorizontalScrollView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(tv)
            }
            val remove = Button(requireContext()).apply { text = "X" }
            remove.setOnClickListener {
                val decoded = Uri.decode(uri)
                Log.d(TAG, "remove source $decoded")
                sources.remove(uri)
                prefs.edit().putStringSet(FileCopyWorker.PREF_SOURCES, sources.toSet()).apply()
                refreshSources(prefs)
                AppLog.add(requireContext(), "Removed source $decoded")
            }
            row.addView(scroll)
            row.addView(remove)
            sourcesLayout.addView(row)
        }
        updateEnabledState()
    }

    private fun refreshExisting(prefs: android.content.SharedPreferences) {
        existLayout.removeAllViews()
        for (uri in existing) {
            val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
            val tv = TextView(requireContext()).apply { text = Uri.decode(uri) }
            val scroll = android.widget.HorizontalScrollView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(tv)
            }
            val remove = Button(requireContext()).apply { text = "X" }
            remove.setOnClickListener {
                val decoded = Uri.decode(uri)
                Log.d(TAG, "remove exist $decoded")
                existing.remove(uri)
                prefs.edit().putStringSet(FileCopyWorker.PREF_EXISTING_DIRS, existing.toSet()).apply()
                refreshExisting(prefs)
                AppLog.add(requireContext(), "Removed existing $decoded")
            }
            row.addView(scroll)
            row.addView(remove)
            existLayout.addView(row)
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
                    val uriStr = uri.toString()
                    sources.add(uriStr)
                    prefs.edit().putStringSet(FileCopyWorker.PREF_SOURCES, sources.toSet()).apply()
                    refreshSources(prefs)
                    AppLog.add(requireContext(), "Added source ${Uri.decode(uriStr)}")
                }
                REQ_PICK_DEST -> {
                    val uriStr = uri.toString()
                    destEdit.setText(Uri.decode(uriStr))
                    prefs.edit().putString(FileCopyWorker.PREF_DEST, uriStr).apply()
                    AppLog.add(requireContext(), "Set destination ${Uri.decode(uriStr)}")
                }
                REQ_PICK_EXIST -> {
                    val uriStr = uri.toString()
                    existing.add(uriStr)
                    prefs.edit().putStringSet(FileCopyWorker.PREF_EXISTING_DIRS, existing.toSet()).apply()
                    refreshExisting(prefs)
                    AppLog.add(requireContext(), "Added existing ${Uri.decode(uriStr)}")
                }
            }
        }
    }

    private fun updateEnabledState() {
        val running = prefs.getBoolean(FileCopyWorker.PREF_IS_RUNNING, false)
        addSource.isEnabled = !running
        addExist.isEnabled = !running
        pickDest.isEnabled = !running
        destEdit.isEnabled = false
        for (i in 0 until sourcesLayout.childCount) {
            sourcesLayout.getChildAt(i).isEnabled = !running
        }
        for (i in 0 until existLayout.childCount) {
            existLayout.getChildAt(i).isEnabled = !running
        }
    }

    override fun onResume() {
        super.onResume()
        prefs.registerOnSharedPreferenceChangeListener(this)
        existing.clear()
        existing.addAll(prefs.getStringSet(FileCopyWorker.PREF_EXISTING_DIRS, emptySet()) ?: emptySet())
        refreshExisting(prefs)
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
        const val REQ_PICK_EXIST = 1003
        const val TAG = "FoldersFrag"
    }
}
