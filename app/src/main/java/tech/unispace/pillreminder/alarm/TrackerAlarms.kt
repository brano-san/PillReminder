package tech.unispace.pillreminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.unispace.pillreminder.container
import tech.unispace.pillreminder.data.AppDatabase
import tech.unispace.pillreminder.data.Tracker
import tech.unispace.pillreminder.data.TrackerType
import tech.unispace.pillreminder.data.epochDayOf
import tech.unispace.pillreminder.data.today
import tech.unispace.pillreminder.ui.Lang
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

fun trackerDisplayName(type: String): String = when (type) {
    TrackerType.WEIGHT -> Lang.s.trackerWeight
    TrackerType.MOOD -> Lang.s.trackerMood
    else -> Lang.s.trackerSleep
}

/** Напоминания трекеров: «пора записать вес/настроение/сон» в назначенное время. */
object TrackerAlarms {

    private fun intentFor(context: Context, trackerId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (820_000 + trackerId).toInt(),
            Intent(context, TrackerReceiver::class.java)
                .setData(Uri.parse("pill://tracker/" + trackerId))
                .putExtra(TrackerReceiver.EXTRA_TRACKER_ID, trackerId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Ближайший день по периодичности «раз в N дней» от дня создания. */
    private fun nextDueDay(tracker: Tracker, fromDay: Long): Long {
        val n = tracker.everyNDays.coerceAtLeast(1).toLong()
        val since = fromDay - tracker.startEpochDay
        val rem = ((since % n) + n) % n
        return if (rem == 0L) fromDay else fromDay + (n - rem)
    }

    suspend fun reschedule(context: Context, db: AppDatabase) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()

        for (tracker in db.trackerDao().getAll()) {
            val pi = intentFor(context, tracker.id)
            alarmManager.cancel(pi)
            if (!tracker.remindEnabled) continue

            val time = LocalTime.of(tracker.askAtMinutes / 60, tracker.askAtMinutes % 60)
            var day = nextDueDay(tracker, today())
            var at = LocalDate.ofEpochDay(day).atTime(time).atZone(zone).toInstant().toEpochMilli()
            // Сегодняшнее время уже прошло или запись уже есть — переносим на следующий цикл.
            val doneToday = db.trackerDao().lastEntry(tracker.id)
                ?.let { epochDayOf(it.atMillis) == today() } == true
            if (at <= now || (day == today() && doneToday)) {
                day = nextDueDay(tracker, day + 1)
                at = LocalDate.ofEpochDay(day).atTime(time).atZone(zone).toInstant().toEpochMilli()
            }
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (_: SecurityException) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
    }
}

class TrackerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val trackerId = intent.getLongExtra(EXTRA_TRACKER_ID, -1L)
        if (trackerId < 0) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = app.container.db
                val tracker = db.trackerDao().getById(trackerId) ?: return@launch
                val doneToday = db.trackerDao().lastEntry(trackerId)
                    ?.let { epochDayOf(it.atMillis) == today() } == true
                if (!doneToday) {
                    Notifications.showTracker(
                        app,
                        trackerId,
                        Lang.s.trackerNotifTitle(trackerDisplayName(tracker.type)),
                        Lang.s.trackerNotifBody,
                    )
                }
                TrackerAlarms.reschedule(app, db)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TRACKER_ID = "trackerId"
    }
}
