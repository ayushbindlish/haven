package org.havenapp.main.service

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.havenapp.main.PowerPolicy
import org.havenapp.main.PreferenceManager
import org.havenapp.main.alerts.AlertManager
import org.havenapp.main.security.IntegrityAuditor
import org.havenapp.main.security.UsageReporter
import java.util.concurrent.TimeUnit

/**
 * One coalesced periodic tick that does every low-frequency background job in a single
 * CPU wake-up: compromise self-audit, daily usage digest (and, as they land, location
 * batch-flush and multi-device heartbeat). It re-schedules itself with an interval taken
 * from [PowerPolicy], so the cadence stretches automatically on low battery / heat.
 */
class HousekeepingWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        const val TAG = "HavenHousekeeping"
        private const val WORK = "haven_housekeeping"

        /** Call once at app start; no-op if a tick is already queued. */
        @JvmStatic
        fun bootstrap(context: Context) {
            enqueue(context, ExistingWorkPolicy.KEEP)
        }

        private fun enqueue(context: Context, policy: ExistingWorkPolicy) {
            val delay = PowerPolicy.housekeepingIntervalMs(context)
            val req = OneTimeWorkRequestBuilder<HousekeepingWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder().setRequiresBatteryNotLow(false).build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK, policy, req)
        }
    }

    override fun doWork(): Result {
        val ctx = applicationContext
        try {
            val prefs = PreferenceManager(ctx)
            val alerts = AlertManager(ctx)

            if (prefs.securityAuditEnabled) {
                val changes = IntegrityAuditor(ctx).auditAgainstBaseline()
                if (changes.isNotEmpty()) {
                    alerts.sendAlert(
                        "Haven security audit — device changed:\n" + changes.joinToString("\n"),
                        null, -1
                    )
                }
            }

            if (prefs.usageReportEnabled) {
                UsageReporter(ctx).buildDailyDigest(8)?.let { alerts.sendAlert(it, null, -1) }
            }

            org.havenapp.main.mesh.MeshMonitor.tick(ctx)
        } catch (e: Exception) {
            Log.e(TAG, "housekeeping failed", e)
        } finally {
            // Always queue the next tick, even after a failure.
            enqueue(ctx, ExistingWorkPolicy.REPLACE)
        }
        return Result.success()
    }
}
