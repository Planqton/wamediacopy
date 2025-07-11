package at.plankt0n.wamediacopy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.preference.PreferenceManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import at.plankt0n.wamediacopy.StatusNotifier
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            if (prefs.getBoolean(SettingsFragment.PREF_ENABLED, false)) {
                val minutes = prefs.getInt(FileCopyWorker.PREF_INTERVAL_MINUTES, 720)
                val period = if (minutes < 15) 15L else minutes.toLong()
                val request = PeriodicWorkRequestBuilder<FileCopyWorker>(period, TimeUnit.MINUTES).build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    SettingsFragment.WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
                StatusNotifier.show(context, false)
            }
        }
    }
}
