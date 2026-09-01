package org.havenapp.main.database.async

import org.havenapp.main.HavenApp
import org.havenapp.main.model.Event

class EventDeleteAsync(private val listener: EventDeleteListener) {
    fun execute(vararg params: Event) {
        val event = params[0]
        AppExecutors.io.execute {
            HavenApp.getDataBaseInstance().getEventDAO().delete(event)
            AppExecutors.main.post { listener.onDeleteEvent() }
        }
    }

    interface EventDeleteListener {
        fun onDeleteEvent()
    }
}
