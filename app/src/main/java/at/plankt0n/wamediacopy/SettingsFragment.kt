package at.plankt0n.wamediacopy

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class SettingsFragment : Fragment() {

    private lateinit var sourcesLayout: LinearLayout
    private lateinit var destEdit: EditText
    private lateinit var aliasEdit: EditText
    private lateinit var ageSeek: SeekBar
    private lateinit var ageText: TextView
    private lateinit var toggle: Switch
    private lateinit var manual: Button
    private lateinit var stop: Button
    private lateinit var addSource: Button
    private lateinit var pickDest: Button

    private val sources = mutableListOf<String>()

    private val manager by lazy { WorkManager.getInstance(requireContext()) }
    private val notifId = 1

    private fun showStatusNotification() {
        Log.d(TAG, "showStatusNotification")
        val channel = NotificationChannel(CHANNEL_ID, "Copy status", NotificationManager.IMPORTANCE_LOW)
        val nm = requireContext().getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
        val notif = NotificationCompat.Builder(requireContext(), CHANNEL_ID)
            .setContentTitle("Background copy running")
            .setContentText("Files sync every 30 minutes")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
        NotificationManagerCompat.from(requireContext()).notify(notifId, notif)
    }

    private fun hideStatusNotification() {
        Log.d(TAG, "hideStatusNotification")
        NotificationManagerCompat.from(requireContext()).cancel(notifId)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated")

        sourcesLayout = view.findViewById(R.id.layout_sources)
        destEdit = view.findViewById(R.id.edit_dest)
        aliasEdit = view.findViewById(R.id.edit_alias)
        addSource = view.findViewById(R.id.button_add_source)
        pickDest = view.findViewById(R.id.button_pick_dest)
        ageSeek = view.findViewById(R.id.seek_age)
        ageText = view.findViewById(R.id.text_age)
        toggle = view.findViewById(R.id.switch_run)
        manual = view.findViewById(R.id.button_manual)
        stop = view.findViewById(R.id.button_stop)

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        sources.clear()
        sources.addAll(prefs.getStringSet(FileCopyWorker.PREF_SOURCES, emptySet()) ?: emptySet())
        refreshSources(prefs)
        destEdit.setText(prefs.getString(FileCopyWorker.PREF_DEST, ""))
        aliasEdit.setText(prefs.getString(FileCopyWorker.PREF_ALIAS, ""))
        val hours = prefs.getInt(FileCopyWorker.PREF_MAX_AGE_HOURS, 24)
        val days = if (hours / 24 > 0) hours / 24 else 1
        ageSeek.progress = days
        ageText.text = "$days Tag" + if (days == 1) "" else "e"
        toggle.isChecked = prefs.getBoolean(PREF_ENABLED, false)

        toggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_ENABLED, isChecked).apply()
            Log.d(TAG, "toggle periodic $isChecked")
            if (isChecked) scheduleWork() else cancelWork()
        }

        manual.setOnClickListener {
            Log.d(TAG, "manual copy pressed")
            scheduleOnce()
        }
        stop.setOnClickListener {
            Log.d(TAG, "stop pressed")
            cancelWork()
            toggle.isChecked = false
        }
        addSource.setOnClickListener {
            Log.d(TAG, "add source pressed")
            pickFolder(REQ_PICK_SOURCE)
        }
        pickDest.setOnClickListener {
            Log.d(TAG, "pick dest pressed")
            pickFolder(REQ_PICK_DEST)
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                prefs.edit()
                    .putStringSet(FileCopyWorker.PREF_SOURCES, sources.toSet())
                    .putString(FileCopyWorker.PREF_DEST, destEdit.text.toString())
                    .putString(FileCopyWorker.PREF_ALIAS, aliasEdit.text.toString())
                    .putInt(FileCopyWorker.PREF_MAX_AGE_HOURS, ageSeek.progress * 24)
                    .apply()
                Log.d(TAG, "prefs updated")
            }
        }
        destEdit.addTextChangedListener(watcher)
        aliasEdit.addTextChangedListener(watcher)

        ageSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val label = "$progress Tag" + if (progress == 1) "" else "e"
                ageText.text = label
                prefs.edit()
                    .putInt(FileCopyWorker.PREF_MAX_AGE_HOURS, progress * 24)
                    .apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        if (toggle.isChecked) scheduleWork() else cancelWork()
    }

    private fun scheduleWork() {
        Log.d(TAG, "scheduleWork")
        val request = PeriodicWorkRequestBuilder<FileCopyWorker>(30, TimeUnit.MINUTES).build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        showStatusNotification()
    }

    private fun scheduleOnce() {
        Log.d(TAG, "scheduleOnce")
        val request = OneTimeWorkRequestBuilder<FileCopyWorker>().build()
        manager.enqueue(request)
    }

    private fun cancelWork() {
        Log.d(TAG, "cancelWork")
        manager.cancelUniqueWork(WORK_NAME)
        hideStatusNotification()
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
                text = uri
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val remove = Button(requireContext()).apply { text = "X" }
            remove.setOnClickListener {
                Log.d(TAG, "remove source $uri")
                sources.remove(uri)
                prefs.edit().putStringSet(FileCopyWorker.PREF_SOURCES, sources.toSet()).apply()
                refreshSources(prefs)
            }
            row.addView(tv)
            row.addView(remove)
            sourcesLayout.addView(row)
        }
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
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            when (requestCode) {
                REQ_PICK_SOURCE -> {
                    sources.add(uri.toString())
                    prefs.edit().putStringSet(FileCopyWorker.PREF_SOURCES, sources.toSet()).apply()
                    refreshSources(prefs)
                }
                REQ_PICK_DEST -> {
                    destEdit.setText(uri.toString())
                    prefs.edit().putString(FileCopyWorker.PREF_DEST, uri.toString()).apply()
                }
            }
        }
    }

    companion object {
        const val WORK_NAME = "fileCopyPeriodic"
        const val PREF_ENABLED = "enabled"
        const val REQ_PICK_SOURCE = 1001
        const val REQ_PICK_DEST = 1002
        const val CHANNEL_ID = "copy_status"
        const val TAG = "SettingsFrag"
    }
}
