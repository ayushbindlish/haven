package org.havenapp.main.service

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.havenapp.main.PreferenceManager
import org.havenapp.main.security.DeadmanCheck
import java.util.concurrent.TimeUnit

/** Hourly dead-man's-switch check; only scheduled while the feature is enabled. */
class DeadmanWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        private const val WORK = "haven_deadman"

        @JvmStatic
        fun reschedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            if (PreferenceManager(context).deadmanHours <= 0) {
                wm.cancelUniqueWork(WORK)
                return
            }
            wm.enqueueUniquePeriodicWork(
                WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DeadmanWorker>(1, TimeUnit.HOURS).build()
            )
        }
    }

    override fun doWork(): Result {
        DeadmanCheck.run(applicationContext)
        return Result.success()
    }
}
