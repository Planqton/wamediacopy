package at.plankt0n.wamediacopy

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
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import at.plankt0n.wamediacopy.AppLog
import at.plankt0n.wamediacopy.StatusNotifier
import java.util.concurrent.TimeUnit

class SettingsFragment : Fragment() {

    private lateinit var aliasEdit: EditText
    private lateinit var ageSeek: SeekBar
    private lateinit var ageText: TextView
    private lateinit var modeGroup: RadioGroup
    private lateinit var intervalSeek: SeekBar
    private lateinit var intervalText: TextView
    private lateinit var toggle: Switch
    private lateinit var manual: Button
    private lateinit var stop: Button
    private lateinit var lastCopyText: TextView

    private val manager by lazy { WorkManager.getInstance(requireContext()) }

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

        aliasEdit = view.findViewById(R.id.edit_alias)
        ageSeek = view.findViewById(R.id.seek_age)
        ageText = view.findViewById(R.id.text_age)
        modeGroup = view.findViewById(R.id.radio_mode)
        intervalSeek = view.findViewById(R.id.seek_interval)
        intervalText = view.findViewById(R.id.text_interval)
        toggle = view.findViewById(R.id.switch_run)
        manual = view.findViewById(R.id.button_manual)
        stop = view.findViewById(R.id.button_stop)
        lastCopyText = view.findViewById(R.id.text_last_copy)

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        aliasEdit.setText(prefs.getString(FileCopyWorker.PREF_ALIAS, ""))
        val hours = prefs.getInt(FileCopyWorker.PREF_MAX_AGE_HOURS, 24)
        val days = if (hours / 24 > 0) hours / 24 else 1
        ageSeek.progress = days
        ageText.text = "$days Tag" + if (days == 1) "" else "e"
        val mode = prefs.getInt(FileCopyWorker.PREF_COPY_MODE, 0)
        modeGroup.check(if (mode == 0) R.id.radio_mode_age else R.id.radio_mode_last)
        val interval = prefs.getInt(FileCopyWorker.PREF_INTERVAL_MINUTES, 720)
        intervalSeek.max = 144
        intervalSeek.progress = interval / 10
        intervalText.text = formatInterval(interval)
        toggle.isChecked = prefs.getBoolean(PREF_ENABLED, false)

        toggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_ENABLED, isChecked).apply()
            Log.d(TAG, "toggle periodic $isChecked")
            if (isChecked) {
                AppLog.add(requireContext(), "Periodic copy enabled")
                scheduleWork()
            } else {
                AppLog.add(requireContext(), "Periodic copy disabled")
                cancelWork()
            }
        }

        manual.setOnClickListener {
            Log.d(TAG, "manual copy pressed")
            AppLog.add(requireContext(), "Manual copy requested")
            scheduleOnce()
        }
        stop.setOnClickListener {
            Log.d(TAG, "stop pressed")
            AppLog.add(requireContext(), "Periodic copy stopped")
            cancelWork()
            toggle.isChecked = false
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                prefs.edit()
                    .putString(FileCopyWorker.PREF_ALIAS, aliasEdit.text.toString())
                    .apply()
                Log.d(TAG, "prefs updated")
            }
        }
        aliasEdit.addTextChangedListener(watcher)

        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            val modeValue = if (checkedId == R.id.radio_mode_age) 0 else 1
            prefs.edit().putInt(FileCopyWorker.PREF_COPY_MODE, modeValue).apply()
        }

        intervalSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val minutes = progress * 10
                intervalText.text = formatInterval(minutes)
                prefs.edit().putInt(FileCopyWorker.PREF_INTERVAL_MINUTES, minutes).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

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

        refreshLastCopy(prefs)
    }

    override fun onResume() {
        super.onResume()
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        refreshLastCopy(prefs)
    }

    private fun refreshLastCopy(prefs: android.content.SharedPreferences) {
        val ts = prefs.getLong(FileCopyWorker.PREF_LAST_COPY, 0L)
        val label = if (ts == 0L) {
            "Last: never"
        } else {
            val fmt = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", ts)
            "Last: $fmt"
        }
        lastCopyText.text = label
        val running = prefs.getBoolean(FileCopyWorker.PREF_IS_RUNNING, false)
        manual.isEnabled = !running
        if (running) {
            lastCopyText.text = "$label (running)"
        }
    }

    private fun formatInterval(minutes: Int): String {
        return if (minutes % 60 == 0) {
            val h = minutes / 60
            "$h h"
        } else {
            "$minutes min"
        }
    }

    private fun scheduleWork() {
        Log.d(TAG, "scheduleWork")
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val minutes = prefs.getInt(FileCopyWorker.PREF_INTERVAL_MINUTES, 720)
        val request = PeriodicWorkRequestBuilder<FileCopyWorker>(minutes.toLong(), TimeUnit.MINUTES).build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        StatusNotifier.show(requireContext(), false)
    }

    private fun scheduleOnce() {
        Log.d(TAG, "scheduleOnce")
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        if (prefs.getBoolean(FileCopyWorker.PREF_IS_RUNNING, false)) {
            AppLog.add(requireContext(), "Copy already running")
            return
        }
        prefs.edit().putBoolean(FileCopyWorker.PREF_IS_RUNNING, true).apply()
        val request = OneTimeWorkRequestBuilder<FileCopyWorker>().build()
        manager.enqueue(request)
        refreshLastCopy(prefs)
    }

    private fun cancelWork() {
        Log.d(TAG, "cancelWork")
        manager.cancelUniqueWork(WORK_NAME)
        StatusNotifier.hide(requireContext())
    }


    companion object {
        const val WORK_NAME = "fileCopyPeriodic"
        const val PREF_ENABLED = "enabled"
        const val CHANNEL_ID = "copy_status"
        const val TAG = "SettingsFrag"
    }
}
