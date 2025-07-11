package at.plankt0n.wamediacopy

import android.content.Context
import android.net.Uri
import android.preference.PreferenceManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import at.plankt0n.wamediacopy.AppLog
import at.plankt0n.wamediacopy.StatusNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileCopyWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    private fun createForegroundInfo(processed: Int): ForegroundInfo {
        val channel = NotificationChannel(CHANNEL_ID, "Copy progress", NotificationManager.IMPORTANCE_LOW)
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Whatsapp Copy")
            .setContentText("Processed files: $processed")
            .setOngoing(true)
            .build()
        val type = if (android.os.Build.VERSION.SDK_INT >= 34) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else 0
        return ForegroundInfo(FOREGROUND_ID, notif, type)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Worker started")
        AppLog.add(applicationContext, "Worker started")
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        if (prefs.getBoolean(PREF_IS_RUNNING, false)) {
            Log.d(TAG, "Already running")
            AppLog.add(applicationContext, "Copy already running")
            return@withContext Result.success()
        }
        val sources = prefs.getStringSet(PREF_SOURCES, emptySet()) ?: emptySet()
        val destUri = prefs.getString(PREF_DEST, null)
        val maxAgeHours = prefs.getInt(PREF_MAX_AGE_HOURS, 24)
        val copyMode = prefs.getInt(PREF_COPY_MODE, 0)
        val lastCopy = prefs.getLong(PREF_LAST_COPY, 0L)
        val intervalMin = prefs.getInt(PREF_INTERVAL_MINUTES, 0)
        val requireManual = prefs.getBoolean(PREF_REQUIRE_MANUAL_FIRST, true)
        val alias = prefs.getString(PREF_ALIAS, "") ?: ""
        val copied = prefs.getStringSet(PREF_COPIED, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        var newCount = 0
        var oldSkipped = 0
        var alreadySkipped = 0
        var processed = 0

        setForeground(createForegroundInfo(0))
        val manual = inputData.getBoolean(KEY_MANUAL, false)
        if (!manual && requireManual) {
            Log.d(TAG, "Waiting for manual first copy")
            AppLog.add(applicationContext, "Waiting for manual first copy")
            return@withContext Result.success()
        }
        if (manual) {
            prefs.edit().putBoolean(PREF_REQUIRE_MANUAL_FIRST, false).apply()
        }

        if (intervalMin > 0 && lastCopy != 0L) {
            val elapsed = System.currentTimeMillis() - lastCopy
            if (elapsed < intervalMin * 60_000L) {
                Log.d(TAG, "Not due yet")
                return@withContext Result.success()
            }
        }

        if (destUri.isNullOrBlank()) {
            Log.d(TAG, "No destination set")
            AppLog.add(applicationContext, "No destination set")
            return@withContext Result.failure()
        }
        prefs.edit().putBoolean(PREF_IS_RUNNING, true).apply()
        val destDir = DocumentFile.fromTreeUri(applicationContext, Uri.parse(destUri))
        if (destDir == null) {
            prefs.edit().putBoolean(PREF_IS_RUNNING, false).apply()
            return@withContext Result.failure()
        }

        val cutoff = if (copyMode == 0) {
            System.currentTimeMillis() - maxAgeHours * 3600_000L
        } else {
            lastCopy
        }

        suspend fun traverse(dir: DocumentFile) {
            for (doc in dir.listFiles()) {
                if (doc.isDirectory) {
                    traverse(doc)
                } else if (doc.isFile) {
                    processed++
                    setForeground(createForegroundInfo(processed))
                    if (doc.lastModified() >= cutoff) {
                        val key = doc.uri.toString()
                        if (!copied.contains(key)) {
                            try {
                                val name = doc.name ?: return
                                val finalName = if (alias.isNotBlank()) alias + name else name
                                var target = destDir.findFile(finalName)
                                if (target == null) {
                                    target = destDir.createFile(doc.type ?: "application/octet-stream", finalName)
                                }
                                if (target != null) {
                                    applicationContext.contentResolver.openInputStream(doc.uri)?.use { input ->
                                        applicationContext.contentResolver.openOutputStream(target.uri)?.use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    Log.d(TAG, "Copied file")
                                    copied.add(key)
                                    newCount++
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to copy file", e)
                            }
                        } else {
                            Log.d(TAG, "Already copied file")
                            alreadySkipped++
                        }
                    } else {
                        Log.d(TAG, "Too old file")
                        oldSkipped++
                    }
                }
            }
        }

        try {
            for (src in sources) {
                Log.d(TAG, "Processing source $src")
                AppLog.add(applicationContext, "Processing source $src")
                val sDir = DocumentFile.fromTreeUri(applicationContext, Uri.parse(src))
                if (sDir != null && sDir.isDirectory) {
                    traverse(sDir)
                }
            }

            val now = System.currentTimeMillis()
            val next = if (intervalMin > 0) now + intervalMin * 60_000L else 0L
            prefs.edit()
                .putStringSet(PREF_COPIED, copied)
                .putLong(PREF_LAST_COPY, now)
                .putLong(PREF_NEXT_COPY, next)
                .apply()

            val summary = "Copied:$newCount - Too Old:$oldSkipped - Skipped:$alreadySkipped"
            StatusNotifier.showResult(applicationContext, newCount, oldSkipped, alreadySkipped)
            AppLog.add(applicationContext, summary)

            Log.d(TAG, "Worker finished")

            return@withContext Result.success()
        } finally {
            prefs.edit().putBoolean(PREF_IS_RUNNING, false).apply()
        }
    }

    companion object {
        const val PREF_SOURCES = "sources"
        const val PREF_DEST = "dest"
        const val PREF_ALIAS = "alias"
        const val PREF_MAX_AGE_HOURS = "maxAgeH"
        const val PREF_COPIED = "copiedFiles"
        const val PREF_LAST_COPY = "lastCopy"
        const val PREF_NEXT_COPY = "nextCopy"
        const val PREF_COPY_MODE = "copyMode"
        const val PREF_INTERVAL_MINUTES = "intervalM"
        const val PREF_IS_RUNNING = "copyRunning"
        const val PREF_REQUIRE_MANUAL_FIRST = "requireManual"
        const val KEY_MANUAL = "manual"
        const val CHANNEL_ID = "copy_status"
        const val FOREGROUND_ID = 100
        const val TAG = "FileCopyWorker"
    }
}
