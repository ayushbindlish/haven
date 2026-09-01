package org.havenapp.main.database.async

import org.havenapp.main.HavenApp
import org.havenapp.main.model.EventTrigger

class EventTriggerDeleteAsync(private val callback: DeleteCallback) {
    fun execute(vararg params: EventTrigger) {
        val trigger = params[0]
        AppExecutors.io.execute {
            HavenApp.getDataBaseInstance().getEventTriggerDAO().delete(trigger)
            AppExecutors.main.post { callback.onEventTriggerDeleted() }
        }
    }

    interface DeleteCallback {
        fun onEventTriggerDeleted()
    }
}
