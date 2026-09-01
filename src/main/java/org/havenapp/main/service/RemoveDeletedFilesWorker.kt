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
            enforceStorageCap()
            Log.d(TAG, "Cleanup complete. Work id: $id")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed", e)
            Result.retry()
        }
    }

    /**
     * Ring-buffer the evidence directory: while it exceeds [PreferenceManager.getMaxStorageMb],
     * drop whole events oldest-first (their trigger rows and media files) until it's back
     * under ~90 % of the cap. 0 MB disables the cap.
     */
    private fun enforceStorageCap() {
        val capMb = PreferenceManager(applicationContext).maxStorageMb
        if (capMb <= 0) return
        val capBytes = capMb.toLong() * 1024 * 1024
        val root = storageRoot() ?: return
        if (!root.exists()) return

        fun dirSize(): Long = root.walkTopDown().filter { it.isFile }.map { it.length() }.sum()

        var total = dirSize()
        if (total <= capBytes) return
        Log.i(TAG, "evidence ${total / 1048576} MB over ${capMb} MB cap — pruning oldest")

        val database = HavenApp.getDataBaseInstance()
        val target = (capBytes * 9) / 10
        val oldestFirst = database.getEventDAO().getAllEvent() // ORDER BY ID = chronological

        for (event in oldestFirst) {
            if (total <= target) break
            val triggers = database.getEventTriggerDAO().getEventTriggerList(event.id)
            var freed = 0L
            for (t in triggers) {
                val p = t.path ?: continue
                val f = File(p)
                if (f.isFile) { freed += f.length(); f.delete() }
            }
            if (triggers.isNotEmpty()) database.getEventTriggerDAO().deleteAll(triggers)
            database.getEventDAO().delete(event)
            total -= freed
            Log.i(TAG, "pruned event ${event.id} (${freed / 1024} KB)")
        }

        // sweep any now-empty session directories
        root.walkBottomUp().forEach { f ->
            if (f != root && f.isDirectory && (f.listFiles()?.isEmpty() != false)) f.delete()
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
