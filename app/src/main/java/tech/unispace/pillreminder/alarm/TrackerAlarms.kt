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
import tech.unispace.pillreminder.data.TrackerType
import tech.unispace.pillreminder.data.askTimesList
import tech.unispace.pillreminder.ui.Lang
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

fun trackerDisplayName(type: String): String = when (type) {
    TrackerType.WEIGHT -> Lang.s.trackerWeight
    TrackerType.MOOD -> Lang.s.trackerMood
    else -> Lang.s.trackerSleep
}

/** Максимум времён опроса в день — ограничивает диапазон requestCode. */
const val MAX_ASK_TIMES = 8

/** Напоминания трекеров: «пора записать вес/настроение/сон» — N раз в день в заданные часы. */
object TrackerAlarms {

    private fun intentFor(context: Context, trackerId: Long, slot: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (820_000 + trackerId * MAX_ASK_TIMES + slot).toInt(),
            Intent(context, TrackerReceiver::class.java)
                .setData(Uri.parse("pill://tracker/" + trackerId + "/" + slot))
                .putExtra(TrackerReceiver.EXTRA_TRACKER_ID, trackerId)
                .putExtra(TrackerReceiver.EXTRA_SLOT, slot),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    suspend fun reschedule(context: Context, db: AppDatabase) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()

        for (tracker in db.trackerDao().getAll()) {
            val times = tracker.askTimesList().take(MAX_ASK_TIMES)
            // Снимаем все слоты, включая те, что были в прошлой конфигурации.
            for (slot in 0 until MAX_ASK_TIMES) alarmManager.cancel(intentFor(context, tracker.id, slot))
            if (!tracker.remindEnabled) continue

            times.forEachIndexed { slot, minutes ->
                val time = LocalTime.of(minutes / 60, minutes % 60)
                var at = LocalDate.now().atTime(time).atZone(zone).toInstant().toEpochMilli()
                if (at <= now) {
                    at = LocalDate.now().plusDays(1).atTime(time).atZone(zone).toInstant().toEpochMilli()
                }
                val pi = intentFor(context, tracker.id, slot)
                try {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                } catch (_: SecurityException) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                }
            }
        }
    }
}

class TrackerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val trackerId = intent.getLongExtra(EXTRA_TRACKER_ID, -1L)
        val slot = intent.getIntExtra(EXTRA_SLOT, 0)
        if (trackerId < 0) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = app.container.db
                val tracker = db.trackerDao().getById(trackerId) ?: return@launch
                val times = tracker.askTimesList()
                // Напоминаем, если после предыдущего слота (или с полуночи) записи не было.
                val zone = ZoneId.systemDefault()
                val prevMinutes = times.getOrNull(slot - 1)
                val since = if (prevMinutes == null) {
                    LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
                } else {
                    LocalDate.now().atTime(LocalTime.of(prevMinutes / 60, prevMinutes % 60))
                        .atZone(zone).toInstant().toEpochMilli()
                }
                val last = db.trackerDao().lastEntry(trackerId)
                if (last == null || last.atMillis < since) {
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
        const val EXTRA_SLOT = "slot"
    }
}
