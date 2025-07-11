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
import android.widget.Switch
import android.widget.TextView
import com.google.android.material.slider.Slider
import android.os.PowerManager
import androidx.core.content.ContextCompat
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.fragment.app.Fragment
import androidx.appcompat.app.AlertDialog
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.workDataOf
import androidx.work.WorkManager
import at.plankt0n.wamediacopy.AppLog
import at.plankt0n.wamediacopy.StatusNotifier
import java.util.concurrent.TimeUnit
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

class SettingsFragment : Fragment(),
    android.content.SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var aliasEdit: EditText
    private lateinit var ageButton: Button
    private lateinit var ageText: TextView
    private lateinit var modeGroup: RadioGroup
    private lateinit var intervalButton: Button
    private lateinit var intervalText: TextView
    private lateinit var toggle: Switch
    private lateinit var manual: Button
    private lateinit var stop: Button
    private lateinit var modeLast: android.widget.RadioButton
    private lateinit var checkPerms: Button
    private lateinit var permStatus: TextView
    private lateinit var lastCopyText: TextView
    private lateinit var prefs: android.content.SharedPreferences

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
        ageButton = view.findViewById(R.id.button_age)
        ageText = view.findViewById(R.id.text_age)
        modeGroup = view.findViewById(R.id.radio_mode)
        modeLast = view.findViewById(R.id.radio_mode_last)
        intervalButton = view.findViewById(R.id.button_interval)
        intervalText = view.findViewById(R.id.text_interval)
        toggle = view.findViewById(R.id.switch_run)
        manual = view.findViewById(R.id.button_manual)
        stop = view.findViewById(R.id.button_stop)
        checkPerms = view.findViewById(R.id.button_check_perms)
        permStatus = view.findViewById(R.id.text_perm_status)
        lastCopyText = view.findViewById(R.id.text_last_copy)

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        aliasEdit.setText(prefs.getString(FileCopyWorker.PREF_ALIAS, ""))
        refreshAge()
        val mode = prefs.getInt(FileCopyWorker.PREF_COPY_MODE, 0)
        modeGroup.check(if (mode == 0) R.id.radio_mode_age else R.id.radio_mode_last)
        val interval = prefs.getInt(FileCopyWorker.PREF_INTERVAL_MINUTES, 720)
        intervalText.text = formatDuration(interval)
        toggle.isChecked = prefs.getBoolean(PREF_ENABLED, true)

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
            AppLog.add(requireContext(), "Stop requested")
            prefs.edit().putBoolean(FileCopyWorker.PREF_STOP_REQUESTED, true).apply()
        }
        checkPerms.setOnClickListener {
            permStatus.text = ensurePermissions(true)
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
            refreshAge()
        }

        intervalButton.setOnClickListener {
            val current = prefs.getInt(FileCopyWorker.PREF_INTERVAL_MINUTES, 720)
            showDurationDialog("Interval", current) {
                intervalText.text = formatDuration(it)
                prefs.edit().putInt(FileCopyWorker.PREF_INTERVAL_MINUTES, it).apply()
            }
        }

        ageButton.setOnClickListener {
            val current = prefs.getInt(FileCopyWorker.PREF_MAX_AGE_MINUTES, 24 * 60)
            showDurationDialog("Max age", current) {
                ageText.text = formatDuration(it)
                prefs.edit().putInt(FileCopyWorker.PREF_MAX_AGE_MINUTES, it).apply()
            }
        }

        refreshLastCopy(prefs)
        permStatus.text = ensurePermissions()
    }

    override fun onResume() {
        super.onResume()
        prefs.registerOnSharedPreferenceChangeListener(this)
        syncRunningState()
        refreshLastCopy(prefs)
        permStatus.text = ensurePermissions()
    }

    override fun onPause() {
        super.onPause()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun refreshLastCopy(prefs: android.content.SharedPreferences) {
        val ts = prefs.getLong(FileCopyWorker.PREF_LAST_COPY, 0L)
        val lastLabel = if (ts == 0L) {
            "Last: never"
        } else {
            val fmt = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", ts)
            "Last: $fmt"
        }
        val next = prefs.getLong(FileCopyWorker.PREF_NEXT_COPY, 0L)
        val nextLabel = if (next == 0L) {
            "Next: -"
        } else {
            val fmt = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", next)
            "Next: $fmt"
        }
        val running = prefs.getBoolean(FileCopyWorker.PREF_IS_RUNNING, false)
        val processed = prefs.getLong(FileCopyWorker.PREF_PROCESSED, 0L)
        val copied = prefs.getLong(FileCopyWorker.PREF_COUNT_COPIED, 0L)
        val skipped = prefs.getLong(FileCopyWorker.PREF_COUNT_SKIPPED, 0L)
        val old = prefs.getLong(FileCopyWorker.PREF_COUNT_OLD, 0L)

        manual.isEnabled = !running
        aliasEdit.isEnabled = !running
        modeGroup.isEnabled = !running
        intervalButton.isEnabled = !running
        toggle.isEnabled = !running
        stop.isEnabled = running
        checkPerms.isEnabled = !running

        val base = "$lastLabel\n$nextLabel"
        val combined = if (running) {
            "$base\nProcessed: $processed\nCopied: $copied\nSkipped: $skipped\nToo old: $old"
        } else base
        lastCopyText.text = combined
        refreshAge()
    }

    private fun refreshAge() {
        val mode = prefs.getInt(FileCopyWorker.PREF_COPY_MODE, 0)
        val last = prefs.getLong(FileCopyWorker.PREF_LAST_COPY, 0L)
        val diff = if (last == 0L) null else ((System.currentTimeMillis() - last) / 60_000).toInt()
        modeLast.text = if (diff == null) {
            "Since last copy -"
        } else {
            "Since last copy (${formatDuration(diff)})"
        }
        if (mode == 0) {
            val ageMinutes = prefs.getInt(FileCopyWorker.PREF_MAX_AGE_MINUTES, 24 * 60)
            ageText.text = formatDuration(ageMinutes)
        } else {
            if (diff == null) {
                ageText.text = "-"
            } else {
                ageText.text = formatDuration(diff)
                prefs.edit().putInt(FileCopyWorker.PREF_SINCE_AGE_MINUTES, diff).apply()
            }
        }
        val running = prefs.getBoolean(FileCopyWorker.PREF_IS_RUNNING, false)
        ageButton.isEnabled = !running && mode == 0
    }

    private fun formatDuration(minutes: Int): String {
        var m = minutes
        val weeks = m / (7 * 24 * 60)
        m %= 7 * 24 * 60
        val days = m / (24 * 60)
        m %= 24 * 60
        val hours = m / 60
        m %= 60
        val parts = mutableListOf<String>()
        if (weeks > 0) parts.add("${weeks}w")
        if (days > 0) parts.add("${days}d")
        if (hours > 0) parts.add("${hours}h")
        if (m > 0 || parts.isEmpty()) parts.add("${m}m")
        return parts.joinToString(" ")
    }

    private fun ensurePermissions(request: Boolean = false): String {
        val missing = mutableListOf<String>()
        val ctx = requireContext()
        val storagePerms = arrayOf(
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_MEDIA_VIDEO,
            android.Manifest.permission.READ_MEDIA_AUDIO,
            android.Manifest.permission.POST_NOTIFICATIONS
        )
        val needReq = storagePerms.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (needReq.isNotEmpty()) {
            if (request) {
                requestPermissions(needReq.toTypedArray(), REQ_PERMS)
            }
            missing.addAll(needReq.map { it.substringAfterLast('.') })
        }
        val pm = ctx.getSystemService(android.os.PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
            val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:" + ctx.packageName)
            startActivity(intent)
            missing.add("Battery optimizations")
        }
        val hasBoot = ctx.packageManager.checkPermission(
            android.Manifest.permission.RECEIVE_BOOT_COMPLETED,
            ctx.packageName
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasBoot) missing.add("Boot")
        return if (missing.isEmpty()) {
            "All permissions granted"
        } else {
            "Missing: ${missing.joinToString(", ")}"
        }
    }

    private fun scheduleWork() {
        Log.d(TAG, "scheduleWork")
        val minutes = prefs.getInt(FileCopyWorker.PREF_INTERVAL_MINUTES, 720)
        if (prefs.getLong(FileCopyWorker.PREF_LAST_COPY, 0L) == 0L) {
            prefs.edit().putBoolean(FileCopyWorker.PREF_REQUIRE_MANUAL_FIRST, true).apply()
        }
        val period = if (minutes < 15) 15L else minutes.toLong()
        val request = PeriodicWorkRequestBuilder<FileCopyWorker>(period, TimeUnit.MINUTES)
            .addTag(FileCopyWorker.TAG)
            .build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        val last = prefs.getLong(FileCopyWorker.PREF_LAST_COPY, 0L)
        val base = if (last > 0L) maxOf(System.currentTimeMillis(), last) else System.currentTimeMillis()
        val next = base + minutes * 60_000L
        prefs.edit()
            .putBoolean(FileCopyWorker.PREF_IS_RUNNING, false)
            .putLong(FileCopyWorker.PREF_PROCESSED, 0L)
            .putLong(FileCopyWorker.PREF_NEXT_COPY, next)
            .apply()
        refreshLastCopy(prefs)
    }

    private fun scheduleOnce() {
        Log.d(TAG, "scheduleOnce")
        if (prefs.getBoolean(FileCopyWorker.PREF_IS_RUNNING, false)) {
            AppLog.add(requireContext(), "Copy already running")
            return
        }
        prefs.edit()
            .putBoolean(FileCopyWorker.PREF_REQUIRE_MANUAL_FIRST, false)
            .apply()

        if (toggle.isChecked) {
            val minutes = prefs.getInt(FileCopyWorker.PREF_INTERVAL_MINUTES, 720)
            val period = if (minutes < 15) 15L else minutes.toLong()
            val requestPeriodic =
                PeriodicWorkRequestBuilder<FileCopyWorker>(period, TimeUnit.MINUTES)
                    .addTag(FileCopyWorker.TAG)
                    .build()
            manager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                requestPeriodic
            )
            val last = prefs.getLong(FileCopyWorker.PREF_LAST_COPY, 0L)
            val base = if (last > 0L) maxOf(System.currentTimeMillis(), last) else System.currentTimeMillis()
            val next = base + minutes * 60_000L
            prefs.edit().putLong(FileCopyWorker.PREF_NEXT_COPY, next).apply()
        }
        prefs.edit().putLong(FileCopyWorker.PREF_PROCESSED, 0L).apply()
        val request = OneTimeWorkRequestBuilder<FileCopyWorker>()
            .setInputData(androidx.work.workDataOf(FileCopyWorker.KEY_MANUAL to true))
            .addTag(FileCopyWorker.TAG)
            .build()
        manager.enqueue(request)
        refreshLastCopy(prefs)
    }

    private fun cancelWork() {
        Log.d(TAG, "cancelWork")
        manager.cancelUniqueWork(WORK_NAME)
        prefs.edit()
            .putBoolean(FileCopyWorker.PREF_IS_RUNNING, false)
            .remove(FileCopyWorker.PREF_NEXT_COPY)
            .remove(FileCopyWorker.PREF_PROCESSED)
            .apply()
        StatusNotifier.hideService(requireContext())
        refreshLastCopy(prefs)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMS) {
            permStatus.text = ensurePermissions()
        }
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: android.content.SharedPreferences?,
        key: String?
    ) {
        if (key == FileCopyWorker.PREF_IS_RUNNING ||
            key == FileCopyWorker.PREF_PROCESSED ||
            key == FileCopyWorker.PREF_LAST_COPY ||
            key == FileCopyWorker.PREF_NEXT_COPY ||
            key == FileCopyWorker.PREF_COPY_MODE ||
            key == FileCopyWorker.PREF_COUNT_COPIED ||
            key == FileCopyWorker.PREF_COUNT_SKIPPED ||
            key == FileCopyWorker.PREF_COUNT_OLD) {
            refreshLastCopy(prefs)
        }
    }

    private fun syncRunningState() {
        try {
            val infos = manager.getWorkInfosByTag(FileCopyWorker.TAG).get()
            val running = infos.any { it.state == androidx.work.WorkInfo.State.RUNNING }
            val current = prefs.getBoolean(FileCopyWorker.PREF_IS_RUNNING, false)
            if (running != current) {
                prefs.edit().putBoolean(FileCopyWorker.PREF_IS_RUNNING, running).apply()
            }
        } catch (_: Exception) {
        }
    }

    private fun showDurationDialog(title: String, startMinutes: Int, onResult: (Int) -> Unit) {
        val view = layoutInflater.inflate(R.layout.dialog_duration, null)
        val slider = view.findViewById<Slider>(R.id.slider_duration)
        val label = view.findViewById<TextView>(R.id.text_duration)

        slider.valueFrom = 0f
        slider.valueTo = 1f
        slider.stepSize = 0f
        slider.value = minutesToSlider(startMinutes).coerceIn(0f, 1f)
        label.text = formatDuration(startMinutes)

        slider.addOnChangeListener { _, value, _ ->
            label.text = formatDuration(sliderToMinutes(value))
        }
        slider.setLabelFormatter { value: Float ->
            formatDuration(sliderToMinutes(value))
        }

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onResult(sliderToMinutes(slider.value))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun minutesToSlider(minutes: Int): Float {
        val min = 1f
        val max = 43200f
        val minLog = ln(min)
        val maxLog = ln(max)
        return (ln(minutes.coerceIn(min.toInt(), max.toInt()).toFloat()) - minLog) /
            (maxLog - minLog)
    }

    private fun sliderToMinutes(value: Float): Int {
        val min = 1f
        val max = 43200f
        val minLog = ln(min)
        val maxLog = ln(max)
        val minutes = exp(minLog + value.coerceIn(0f, 1f) * (maxLog - minLog))
        val step = when {
            minutes < 60 -> 1      // < 1h -> 1 min steps
            minutes < 1440 -> 5    // < 1d -> 5 min steps
            else -> 60             // >=1d -> 1 h steps
        }
        return ((minutes / step).roundToInt() * step).toInt().coerceIn(min.toInt(), max.toInt())
    }


    companion object {
        const val WORK_NAME = "fileCopyPeriodic"
        const val PREF_ENABLED = "enabled"
        const val CHANNEL_ID = "copy_status"
        const val TAG = "SettingsFrag"
        private const val REQ_PERMS = 5678
    }
}
