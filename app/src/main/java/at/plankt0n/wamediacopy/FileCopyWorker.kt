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
import at.plankt0n.wamediacopy.CopyReports
import android.text.format.DateFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileCopyWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    private fun createForegroundInfo(processed: Long): ForegroundInfo {
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
        var maxAgeMinutes = prefs.getInt(PREF_MAX_AGE_MINUTES, 24 * 60)
        var sinceAgeMinutes = prefs.getInt(PREF_SINCE_AGE_MINUTES, maxAgeMinutes)
        val copyMode = prefs.getInt(PREF_COPY_MODE, 0)
        val lastCopy = prefs.getLong(PREF_LAST_COPY, 0L)
        val intervalMin = prefs.getInt(PREF_INTERVAL_MINUTES, 0)
        val requireManual = prefs.getBoolean(PREF_REQUIRE_MANUAL_FIRST, true)
        val alias = prefs.getString(PREF_ALIAS, "") ?: ""
        val existingDirs = prefs.getStringSet(PREF_EXISTING_DIRS, emptySet()) ?: emptySet()
        val copied = prefs.getStringSet(PREF_COPIED, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        var newCount = 0L
        var oldSkipped = 0L
        var alreadySkipped = 0L
        var existingSkipped = 0L
        var blackSkipped = 0L
        var processed = 0L
        var newestOldName: String? = null
        var newestOldTime = 0L
        var stop = false

        prefs.edit()
            .putLong(PREF_PROCESSED, 0L)
            .putLong(PREF_COUNT_COPIED, 0L)
            .putLong(PREF_COUNT_OLD, 0L)
            .putLong(PREF_COUNT_SKIPPED, 0L)
            .putLong(PREF_COUNT_EXISTING, 0L)
            .putLong(PREF_COUNT_BLACKLISTED, 0L)
            .putBoolean(PREF_STOP_REQUESTED, false)
            .apply()
        setForeground(createForegroundInfo(0L))
        val manual = inputData.getBoolean(KEY_MANUAL, false)
        if (!manual && requireManual) {
            Log.d(TAG, "Waiting for manual first copy")
            AppLog.add(applicationContext, "Waiting for manual first copy")
            return@withContext Result.success()
        }
        if (manual) {
            prefs.edit().putBoolean(PREF_REQUIRE_MANUAL_FIRST, false).apply()
        }

        if (!manual && intervalMin > 0 && lastCopy != 0L) {
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
        val existDirs = existingDirs.mapNotNull {
            DocumentFile.fromTreeUri(applicationContext, Uri.parse(it))
        }

        val startTs = System.currentTimeMillis()
        if (copyMode == 1 && lastCopy > 0L) {
            sinceAgeMinutes = ((startTs - lastCopy) / 60_000).toInt()
            prefs.edit().putInt(PREF_SINCE_AGE_MINUTES, sinceAgeMinutes).apply()
            maxAgeMinutes = sinceAgeMinutes
        }
        val cutoff = if (copyMode == 0) {
            startTs - maxAgeMinutes * 60_000L
        } else {
            lastCopy
        }


        suspend fun traverse(dir: DocumentFile) {
            for (doc in dir.listFiles()) {
                if (prefs.getBoolean(PREF_STOP_REQUESTED, false)) {
                    stop = true
                    return
                }
                if (doc.isDirectory) {
                    traverse(doc)
                } else if (doc.isFile) {
                    processed++
                    prefs.edit()
                        .putLong(PREF_PROCESSED, processed)
                        .apply()
                    setForeground(createForegroundInfo(processed))
                    val key = doc.uri.toString()
                    if (copied.contains(key)) {
                        Log.d(TAG, "Already copied file")
                        alreadySkipped++
                        blackSkipped++
                        prefs.edit()
                            .putLong(PREF_COUNT_SKIPPED, alreadySkipped)
                            .putLong(PREF_COUNT_BLACKLISTED, blackSkipped)
                            .apply()
                    } else if (doc.lastModified() >= cutoff) {
                        try {
                            val name = doc.name ?: return
                            val finalName = if (alias.isNotBlank()) alias + name else name
                            val existsElsewhere = existDirs.any { it.findFile(finalName) != null }
                            if (existsElsewhere) {
                                Log.d(TAG, "Exists elsewhere")
                                AppLog.add(applicationContext, "skipped existfile")
                                alreadySkipped++
                                existingSkipped++
                                copied.add(key)
                                prefs.edit()
                                    .putLong(PREF_COUNT_SKIPPED, alreadySkipped)
                                    .putLong(PREF_COUNT_EXISTING, existingSkipped)
                                    .apply()
                                continue
                            }
                                var targetName = finalName
                                var target = destDir.findFile(targetName)
                                if (target != null) {
                                    val dot = targetName.lastIndexOf('.')
                                    val base = if (dot >= 0) targetName.substring(0, dot) else targetName
                                    val ext = if (dot >= 0) targetName.substring(dot) else ""
                                    var index = 1
                                    var candidate: DocumentFile? = destDir.findFile("${base}_$index$ext")
                                    while (candidate != null) {
                                        index++
                                        candidate = destDir.findFile("${base}_$index$ext")
                                    }
                                    targetName = "${base}_$index$ext"
                                    target = null
                                }
                                if (target == null) {
                                    target = destDir.createFile(doc.type ?: "application/octet-stream", targetName)
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
                                    prefs.edit().putLong(PREF_COUNT_COPIED, newCount).apply()
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to copy file", e)
                            }
                    } else {
                        Log.d(TAG, "Too old file")
                        oldSkipped++
                        prefs.edit().putLong(PREF_COUNT_OLD, oldSkipped).apply()
                        val ts = doc.lastModified()
                        if (ts > newestOldTime) {
                            newestOldTime = ts
                            newestOldName = doc.name ?: doc.uri.toString()
                        }
                    }
                }
            }
        }

        try {
            for (src in sources) {
                if (prefs.getBoolean(PREF_STOP_REQUESTED, false)) {
                    stop = true
                    break
                }
                val decoded = Uri.decode(src)
                Log.d(TAG, "Processing source $decoded")
                AppLog.add(applicationContext, "Processing source $decoded")
                val sDir = DocumentFile.fromTreeUri(applicationContext, Uri.parse(src))
                if (sDir != null && sDir.isDirectory) {
                    traverse(sDir)
                    if (stop) break
                }
            }

            if (stop) {
                Log.d(TAG, "Copy stopped by user")
                AppLog.add(applicationContext, "Copy stopped by user")
                StatusNotifier.hideService(applicationContext)
                return@withContext Result.success()
            }

            val now = System.currentTimeMillis()
            prefs.edit()
                .putStringSet(PREF_COPIED, copied)
                .putLong(PREF_LAST_COPY, now)
                .apply()

            val summary = "Copied:$newCount - Too Old:$oldSkipped - Skipped existing:$existingSkipped - Skipped:$alreadySkipped"
            StatusNotifier.showResult(applicationContext, newCount, oldSkipped, existingSkipped, alreadySkipped)
            AppLog.add(applicationContext, summary)
            CopyReports.add(applicationContext, startTs, now, newCount, oldSkipped, existingSkipped, blackSkipped)

            if (intervalMin > 0) {
                val periodMin = maxOf(intervalMin, MIN_INTERVAL_MINUTES)
                val nextScheduled = now + periodMin * 60_000L
                prefs.edit().putLong(PREF_NEXT_COPY, nextScheduled).apply()
            }

            if (newestOldName != null) {
                val age = formatAge(now - newestOldTime)
                val tsStr = DateFormat.format("yyyy-MM-dd HH:mm:ss", newestOldTime).toString()
                val msg = "Newest too old: $newestOldName - $age - $tsStr"
                AppLog.add(applicationContext, msg)
            }

            Log.d(TAG, "Worker finished")

            return@withContext Result.success()
        } finally {
            prefs.edit()
                .putBoolean(PREF_IS_RUNNING, false)
                .remove(PREF_PROCESSED)
                .remove(PREF_COUNT_COPIED)
                .remove(PREF_COUNT_OLD)
                .remove(PREF_COUNT_SKIPPED)
                .remove(PREF_COUNT_EXISTING)
                .remove(PREF_COUNT_BLACKLISTED)
                .putBoolean(PREF_STOP_REQUESTED, false)
                .apply()
        }
    }

    private fun formatAge(ms: Long): String {
        var s = ms / 1000
        val days = s / (24 * 3600)
        s %= 24 * 3600
        val hours = s / 3600
        s %= 3600
        val minutes = s / 60
        s %= 60
        return String.format("%02d:%02d:%02d:%02d", days, hours, minutes, s)
    }

    companion object {
        const val MIN_INTERVAL_MINUTES = 15
        const val PREF_SOURCES = "sources"
        const val PREF_DEST = "dest"
        const val PREF_ALIAS = "alias"
        const val PREF_EXISTING_DIRS = "existingDirs"
        const val PREF_MAX_AGE_MINUTES = "maxAgeM"
        const val PREF_SINCE_AGE_MINUTES = "sinceAgeM"
        const val PREF_COPIED = "copiedFiles"
        const val PREF_LAST_COPY = "lastCopy"
        const val PREF_NEXT_COPY = "nextCopy"
        const val PREF_COPY_MODE = "copyMode"
        const val PREF_INTERVAL_MINUTES = "intervalM"
        const val PREF_IS_RUNNING = "copyRunning"
        const val PREF_PROCESSED = "processed"
        const val PREF_COUNT_COPIED = "countCopied"
        const val PREF_COUNT_OLD = "countOld"
        const val PREF_COUNT_SKIPPED = "countSkipped"
        const val PREF_COUNT_EXISTING = "countExisting"
        const val PREF_COUNT_BLACKLISTED = "countBlacklisted"
        const val PREF_REQUIRE_MANUAL_FIRST = "requireManual"
        const val PREF_STOP_REQUESTED = "stopRequested"
        const val KEY_MANUAL = "manual"
        const val CHANNEL_ID = "copy_status"
        const val FOREGROUND_ID = 100
        const val TAG = "FileCopyWorker"
    }
}
