package org.havenapp.main.service

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.havenapp.main.PreferenceManager
import org.havenapp.main.alerts.AlertManager
import org.havenapp.main.security.IntegrityAuditor
import java.util.concurrent.TimeUnit

/**
 * Periodic compromise self-audit: diffs security-relevant device state against the stored
 * baseline and, on any change, fires an alert through every enabled channel.
 */
class IntegrityAuditWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        const val TAG = "HavenIntegrityAudit"

        @JvmStatic
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<IntegrityAuditWorker>(6, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TAG, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        @JvmStatic
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<IntegrityAuditWorker>().build()
            )
        }
    }

    override fun doWork(): Result {
        if (!PreferenceManager(applicationContext).securityAuditEnabled) return Result.success()
        return try {
            val changes = IntegrityAuditor(applicationContext).auditAgainstBaseline()
            if (changes.isNotEmpty()) {
                val msg = "Haven security audit — device changed:\n" + changes.joinToString("\n")
                Log.w(TAG, msg)
                AlertManager(applicationContext).sendAlert(msg, null, -1)
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "audit failed", e)
            Result.retry()
        }
    }
}
