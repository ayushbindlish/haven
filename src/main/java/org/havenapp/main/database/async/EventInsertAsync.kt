package org.havenapp.main.database.async

import org.havenapp.main.HavenApp
import org.havenapp.main.model.Event

class EventInsertAsync(val listener: EventInsertListener) {
    fun execute(vararg params: Event) {
        val event = params[0]
        AppExecutors.io.execute {
            val id = HavenApp.getDataBaseInstance().getEventDAO().insert(event)
            AppExecutors.main.post { listener.onInsertionComplete(id) }
        }
    }

    interface EventInsertListener {
        fun onInsertionComplete(eventId: Long)
    }
}
