package org.havenapp.main.service

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.havenapp.main.PreferenceManager
import org.havenapp.main.alerts.AlertManager
import org.havenapp.main.security.UsageReporter
import java.util.concurrent.TimeUnit

/** Daily "screen time" digest sent through the alert channels when enabled. */
class UsageReportWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        const val TAG = "HavenUsageReport"

        @JvmStatic
        fun reschedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            if (!PreferenceManager(context).usageReportEnabled) {
                wm.cancelUniqueWork(TAG)
                return
            }
            wm.enqueueUniquePeriodicWork(
                TAG,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<UsageReportWorker>(24, TimeUnit.HOURS).build()
            )
        }

        @JvmStatic
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<UsageReportWorker>().build()
            )
        }
    }

    override fun doWork(): Result {
        if (!PreferenceManager(applicationContext).usageReportEnabled) return Result.success()
        val digest = UsageReporter(applicationContext).buildDailyDigest(8) ?: return Result.success()
        AlertManager(applicationContext).sendAlert(digest, null, -1)
        return Result.success()
    }
}
