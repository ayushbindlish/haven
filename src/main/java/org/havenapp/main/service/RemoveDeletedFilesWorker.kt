package org.havenapp.main.service

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.havenapp.main.HavenApp
import org.havenapp.main.PreferenceManager
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Deletes media files on disk that are no longer referenced by any event trigger,
 * plus event triggers that point at events which no longer exist.
 *
 * Replaces the old `com.evernote:android-job` based `RemoveDeletedFilesJob`. Scoped
 * storage safe: it walks the app-private external files directory, never the
 * (now inaccessible) legacy external storage root.
 *
 * Created by Arka Prava Basu, migrated to WorkManager 2026.
 */
class RemoveDeletedFilesWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        const val TAG = "HavenCleanupWork"

        /** Schedule the recurring 24h cleanup (only runs while charging). Idempotent. */
        @JvmStatic
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .build()
            val request = PeriodicWorkRequestBuilder<RemoveDeletedFilesWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** Kick off a one-off cleanup immediately (used by the debug menu item). */
        @JvmStatic
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<RemoveDeletedFilesWorker>().build()
            )
        }
    }

    override fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting cleanup. Work id: $id")
            removeDeletedLogsFromDisk()
            Log.d(TAG, "Cleanup complete. Work id: $id")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed", e)
            Result.retry()
        }
    }

    private fun storageRoot(): File? {
        val base = PreferenceManager(applicationContext).baseStoragePath
        val ext = applicationContext.getExternalFilesDir(null) ?: return null
        return File(ext, base)
    }

    private fun removeDeletedLogsFromDisk() {
        val database = HavenApp.getDataBaseInstance()

        val eventIds = database.getEventDAO().getAllEvent().mapNotNull { it.id }.toHashSet()

        // delete triggers whose parent event is gone
        val orphanedTriggers = database.getEventTriggerDAO().getAllEventTriggers()
            .filter { it.eventId !in eventIds }
        if (orphanedTriggers.isNotEmpty()) {
            database.getEventTriggerDAO().deleteAll(orphanedTriggers)
        }

        val root = storageRoot() ?: return
        if (!root.exists()) return

        val referencedPaths = database.getEventTriggerDAO().getAllEventTriggers()
            .mapNotNull { it.path }
            .toHashSet()

        root.walkBottomUp().forEach { file ->
            when {
                file == root -> Unit
                file.isFile && file.name != ".nomedia" &&
                    file.absolutePath !in referencedPaths -> file.delete()
                file.isDirectory && (file.listFiles()?.isEmpty() != false) -> file.delete()
            }
        }
    }
}
