package org.havenapp.main.service

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.havenapp.main.PreferenceManager
import org.havenapp.main.alerts.AlertManager
import org.havenapp.main.backup.BackupManager
import java.util.concurrent.TimeUnit

/** Encrypted off-device backup: weekly when enabled, or on demand. */
class BackupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        private const val WORK = "haven_backup"

        @JvmStatic
        fun reschedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            val prefs = PreferenceManager(context)
            if (!prefs.backupEnabled || !BackupManager.configured(context)) {
                wm.cancelUniqueWork(WORK)
                return
            }
            wm.enqueueUniquePeriodicWork(
                WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.UNMETERED)
                            .setRequiresBatteryNotLow(true)
                            .build()
                    ).build()
            )
        }

        @JvmStatic
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<BackupWorker>().build()
            )
        }
    }

    override fun doWork(): Result {
        val err = BackupManager.runBackup(applicationContext)
        if (err != null) {
            Log.w("BackupWorker", "backup failed: $err")
            AlertManager(applicationContext).sendAlert("Haven backup failed: $err", null, -1)
            return Result.retry()
        }
        return Result.success()
    }
}
