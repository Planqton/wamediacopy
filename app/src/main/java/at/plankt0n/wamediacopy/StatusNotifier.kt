package at.plankt0n.wamediacopy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.preference.PreferenceManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object StatusNotifier {
    private const val SERVICE_ID = 1
    private const val RESULT_ID = 2
    const val CHANNEL_ID = "copy_status"

    private fun remainingMs(context: Context): Long? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val interval = prefs.getInt(FileCopyWorker.PREF_INTERVAL_MINUTES, 0)
        if (interval <= 0) return null
        val last = prefs.getLong(FileCopyWorker.PREF_LAST_COPY, 0L)
        if (last == 0L) return null
        val next = last + interval * 60_000L
        val remaining = next - System.currentTimeMillis()
        return if (remaining > 0) remaining else null
    }

    private fun formatRemaining(ms: Long): String {
        val sec = ms / 1000
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return String.format("Next Copy in %02d:%02d:%02d", h, m, s)
    }

    fun showService(context: Context, processed: Int? = null) {
        val channel = NotificationChannel(CHANNEL_ID, "Copy status", NotificationManager.IMPORTANCE_LOW)
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
        val remaining = remainingMs(context)
        val text = processed?.let { "Processed Files: $it" }
            ?: remaining?.let { formatRemaining(it) }
            ?: ""
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Whatsapp Copy")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
        NotificationManagerCompat.from(context).notify(SERVICE_ID, notif)
    }

    fun showResult(context: Context, copied: Int, old: Int, skipped: Int) {
        val channel = NotificationChannel(CHANNEL_ID, "Copy status", NotificationManager.IMPORTANCE_LOW)
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
        val text = "Copied Files:$copied - Skipped Old:$old - Skipped Blacklist:$skipped"
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Whatsapp Copy: Copy Finished")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .build()
        NotificationManagerCompat.from(context).notify(RESULT_ID, notif)
    }

    fun hideService(context: Context) {
        NotificationManagerCompat.from(context).cancel(SERVICE_ID)
    }
}
