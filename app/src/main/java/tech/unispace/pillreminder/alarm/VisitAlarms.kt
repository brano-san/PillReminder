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
import tech.unispace.pillreminder.data.Settings
import tech.unispace.pillreminder.ui.Lang
import tech.unispace.pillreminder.ui.formatClock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Все возможные смещения напоминаний о визите; какие включены — решает настройка. */
val VISIT_OFFSET_CHOICES = listOf(2880, 1440, 180)

/** Будильники на визиты к врачу: за 2 дня / 1 день / 3 часа — что выбрано в настройках. */
object VisitAlarms {

    private fun intentFor(context: Context, visitId: Long, offsetIndex: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (900_000 + visitId * VISIT_OFFSET_CHOICES.size + offsetIndex).toInt(),
            Intent(context, VisitReceiver::class.java)
                .setData(Uri.parse("pill://visit/" + visitId + "/" + offsetIndex))
                .putExtra(VisitReceiver.EXTRA_VISIT_ID, visitId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Пересобрать все будильники визитов: после изменения визита, настроек или перезагрузки. */
    suspend fun reschedule(context: Context, db: AppDatabase) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        val enabled = Settings(context).visitOffsetsMinutes

        for (visit in db.visitDao().getAll()) {
            VISIT_OFFSET_CHOICES.forEachIndexed { index, offsetMinutes ->
                val pi = intentFor(context, visit.id, index)
                alarmManager.cancel(pi)
                val at = visit.atMillis - offsetMinutes * 60_000L
                if (offsetMinutes in enabled && at > now) {
                    try {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                    } catch (_: SecurityException) {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                    }
                }
            }
        }
    }
}

class VisitReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val visitId = intent.getLongExtra(EXTRA_VISIT_ID, -1L)
        if (visitId < 0) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val visit = app.container.db.visitDao().getById(visitId) ?: return@launch
                val dateFmt = DateTimeFormatter.ofPattern("d MMMM", Lang.s.locale)
                val date = Instant.ofEpochMilli(visit.atMillis)
                    .atZone(ZoneId.systemDefault()).toLocalDate().format(dateFmt)
                Notifications.showVisit(
                    app,
                    visitId,
                    visit.title,
                    date + ", " + formatClock(visit.atMillis),
                )
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_VISIT_ID = "visitId"
    }
}
