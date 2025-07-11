package at.plankt0n.wamediacopy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.preference.PreferenceManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object StatusNotifier {
    private const val NOTIF_ID = 1
    const val CHANNEL_ID = "copy_status"

    private fun remainingLabel(context: Context): String? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val interval = prefs.getInt(FileCopyWorker.PREF_INTERVAL_MINUTES, 0)
        if (interval <= 0) return null
        val last = prefs.getLong(FileCopyWorker.PREF_LAST_COPY, 0L)
        if (last == 0L) return null
        val next = last + interval * 60_000L
        val remaining = next - System.currentTimeMillis()
        if (remaining <= 0) return null
        val mins = (remaining / 60_000L).toInt()
        val hours = mins / 60
        return if (hours > 0) {
            "Noch ${hours} h bis n\u00e4chste Kopie"
        } else {
            "Noch ${mins} min bis n\u00e4chste Kopie"
        }
    }

    fun show(context: Context, running: Boolean) {
        val channel = NotificationChannel(CHANNEL_ID, "Copy status", NotificationManager.IMPORTANCE_LOW)
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
        val text = remainingLabel(context) ?: if (running) "Kopiere..." else "Bereit"
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("WhatsappCopy l\u00e4uft")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
    }

    fun hide(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID)
    }
}
