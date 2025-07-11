package at.plankt0n.wamediacopy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.preference.PreferenceManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import at.plankt0n.wamediacopy.CopyService
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            if (prefs.getBoolean(SettingsFragment.PREF_ENABLED, true)) {
                val minutes = prefs.getInt(FileCopyWorker.PREF_INTERVAL_MINUTES, 720)
                val period = if (minutes < 15) 15L else minutes.toLong()
                val request = PeriodicWorkRequestBuilder<FileCopyWorker>(period, TimeUnit.MINUTES).build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    SettingsFragment.WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
                val svc = Intent(context, CopyService::class.java)
                context.startForegroundService(svc)
            }
        }
    }
}
