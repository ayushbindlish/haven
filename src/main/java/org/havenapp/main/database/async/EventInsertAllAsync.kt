package org.havenapp.main.database.async

import org.havenapp.main.HavenApp
import org.havenapp.main.model.Event

class EventInsertAllAsync(private val listener: EventInsertListener) {
    fun execute(vararg params: List<Event>) {
        val events = params[0]
        AppExecutors.io.execute {
            val ids = HavenApp.getDataBaseInstance().getEventDAO().insertAll(events)
            AppExecutors.main.post { listener.onInsertionComplete(ids) }
        }
    }

    interface EventInsertListener {
        fun onInsertionComplete(eventIdList: List<Long>)
    }
}
