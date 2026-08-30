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

/** Пресеты смещений напоминаний о визите, в минутах; пользователь может добавить свои. */
val VISIT_OFFSET_PRESETS = listOf(10080, 4320, 2880, 1440, 720, 180, 60)

/** Будильники на визиты к врачу: за N часов/дней — что выбрано в настройках. */
object VisitAlarms {

    /**
     * requestCode — детерминированный хэш пары (визит, смещение): смещения теперь
     * произвольные, индексом их не пронумеровать.
     */
    private fun intentFor(context: Context, visitId: Long, offsetMinutes: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            ("visit-$visitId-$offsetMinutes").hashCode(),
            Intent(context, VisitReceiver::class.java)
                .setData(Uri.parse("pill://visit/" + visitId + "/" + offsetMinutes))
                .putExtra(VisitReceiver.EXTRA_VISIT_ID, visitId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Пересобрать все будильники визитов: после изменения визита, настроек или перезагрузки. */
    suspend fun reschedule(context: Context, db: AppDatabase) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        val settings = Settings(context)
        val enabled = settings.visitOffsetsMinutes
        // Отменяем всё, что могло быть поставлено раньше, включая уже убранные смещения.
        val toCancel = VISIT_OFFSET_PRESETS.toSet() + settings.visitOffsetsEver + enabled

        for (visit in db.visitDao().getAll()) {
            for (offset in toCancel) alarmManager.cancel(intentFor(context, visit.id, offset))
            for (offset in enabled) {
                val at = visit.atMillis - offset * 60_000L
                if (at > now) {
                    val pi = intentFor(context, visit.id, offset)
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
