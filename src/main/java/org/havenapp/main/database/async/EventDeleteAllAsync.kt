package org.havenapp.main.database.async

import org.havenapp.main.HavenApp
import org.havenapp.main.model.Event

class EventDeleteAllAsync(private val listener: EventDeleteAllListener) {
    fun execute(vararg params: List<Event>) {
        val events = params[0]
        AppExecutors.io.execute {
            HavenApp.getDataBaseInstance().getEventDAO().deleteAll(events)
            AppExecutors.main.post { listener.onEventsDeleted() }
        }
    }

    interface EventDeleteAllListener {
        fun onEventsDeleted()
    }
}
