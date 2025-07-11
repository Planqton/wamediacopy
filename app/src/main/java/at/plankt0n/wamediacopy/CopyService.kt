package at.plankt0n.wamediacopy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class CopyService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            StatusNotifier.showService(this@CopyService)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().putBoolean(FileCopyWorker.PREF_IS_RUNNING, false).apply()
        startForegroundInternal()
        handler.post(updateRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        StatusNotifier.hideService(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundInternal() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            StatusNotifier.CHANNEL_ID,
            "Copy status",
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(channel)
        val notif = NotificationCompat.Builder(this, StatusNotifier.CHANNEL_ID)
            .setContentTitle("WhatsappCopy l\u00e4uft")
            .setContentText("")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34)
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        else 0
        startForeground(FOREGROUND_ID, notif, type)
        StatusNotifier.showService(this)
    }

    companion object {
        const val ACTION_STOP = "stop"
        private const val FOREGROUND_ID = 50
    }
}
