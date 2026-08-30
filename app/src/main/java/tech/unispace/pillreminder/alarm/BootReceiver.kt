package tech.unispace.pillreminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.unispace.pillreminder.container

/** Будильники не переживают перезагрузку и смену времени — восстанавливаем их из базы. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.container.planner.rescheduleAlarms()
                VisitAlarms.reschedule(app, app.container.db)
                TrackerAlarms.reschedule(app, app.container.db)
            } finally {
                pending.finish()
            }
        }
    }
}
