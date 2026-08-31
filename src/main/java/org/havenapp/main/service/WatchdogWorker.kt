package org.havenapp.main.service

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.havenapp.main.PreferenceManager
import java.util.concurrent.TimeUnit

/**
 * Safety net: if monitoring is supposed to be running but the service process is gone
 * (OS killed the whole app and didn't restart it), prompt to re-arm. Only scheduled
 * while armed; 30 min cadence to stay light.
 */
class WatchdogWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        private const val WORK = "haven_watchdog"

        @JvmStatic
        fun start(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WatchdogWorker>(30, TimeUnit.MINUTES).build()
            )
        }

        @JvmStatic
        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
        }
    }

    override fun doWork(): Result {
        val prefs = PreferenceManager(applicationContext)
        val shouldRun = prefs.monitorServiceActive
        val isRunning = MonitorService.getInstance()?.isRunning == true
        if (shouldRun && !isRunning) {
            ResumeNotifier.post(applicationContext, "Haven stopped unexpectedly — tap to re-arm")
        } else if (!shouldRun) {
            stop(applicationContext)
        }
        return Result.success()
    }
}
