package org.havenapp.main.service

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.havenapp.main.pairing.SupervisorBus
import java.util.concurrent.TimeUnit

/** Child side: 15-min heartbeat + parent-command poll while supervised mode is on. */
class SupervisorWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        private const val WORK = "haven_supervisor"

        @JvmStatic
        fun reschedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            if (!SupervisorBus.active(context)) {
                wm.cancelUniqueWork(WORK)
                return
            }
            wm.enqueueUniquePeriodicWork(
                WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SupervisorWorker>(15, TimeUnit.MINUTES).build()
            )
        }
    }

    override fun doWork(): Result {
        SupervisorBus.tick(applicationContext)
        return Result.success()
    }
}
