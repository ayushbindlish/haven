package org.havenapp.main.database.async

import org.havenapp.main.HavenApp
import org.havenapp.main.model.EventTrigger

class EventTriggerInsertAsync(private val callback: InsertCallback) {
    fun execute(vararg params: EventTrigger) {
        val trigger = params[0]
        AppExecutors.io.execute {
            val id = HavenApp.getDataBaseInstance().getEventTriggerDAO().insert(trigger)
            AppExecutors.main.post { callback.onEventTriggerInserted(id) }
        }
    }

    interface InsertCallback {
        fun onEventTriggerInserted(eventTriggerId: Long)
    }
}
