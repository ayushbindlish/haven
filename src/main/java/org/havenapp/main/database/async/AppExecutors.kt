package org.havenapp.main.database.async

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * Small replacement for the removed [android.os.AsyncTask]: a single-threaded background
 * executor for short Room writes plus a main-thread handler to deliver the result back.
 * Serial execution keeps the previous AsyncTask.SERIAL_EXECUTOR ordering guarantees.
 */
object AppExecutors {
    @JvmField
    val io = Executors.newSingleThreadExecutor { r -> Thread(r, "haven-db") }

    @JvmField
    val main = Handler(Looper.getMainLooper())
}
