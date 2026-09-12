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

/**
 * Моменты напоминаний о визите по выбранным смещениям — только будущие, по убыванию заблаговременности.
 * Чистая функция: экран визита показывает этот же список, чтобы было ясно, когда именно придёт уведомление.
 */
fun visitReminderMoments(visitAt: Long, offsetsMinutes: Collection<Int>, now: Long): List<Long> =
    offsetsMinutes.map { visitAt - it * 60_000L }.filter { it > now }.distinct().sorted()

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
        // Отменяем всё, что могло быть поставлено раньше, включая уже убранные смещения и запасное (0).
        val toCancel = VISIT_OFFSET_PRESETS.toSet() + settings.visitOffsetsEver + enabled + SOON_OFFSET

        fun set(at: Long, pi: PendingIntent) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (_: SecurityException) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }

        for (visit in db.visitDao().getAll()) {
            for (offset in toCancel) alarmManager.cancel(intentFor(context, visit.id, offset))
            // Напоминание выключено у самого визита — он остаётся в списке, но будильников не получает.
            if (!visit.remind) continue
            var scheduled = false
            for (offset in enabled) {
                val at = visit.atMillis - offset * 60_000L
                if (at > now) {
                    set(at, intentFor(context, visit.id, offset))
                    scheduled = true
                }
            }
            // Визит ближе всех включённых смещений (записали «через два часа») — одно напоминание через
            // минуту вместо тишины: пользователь видит «напоминания включены» и ждёт хоть одно.
            if (!scheduled && visit.atMillis > now) set(now + 60_000L, intentFor(context, visit.id, SOON_OFFSET))
        }
    }

    /** Смещение запасного напоминания «визит уже скоро»; 0 не пересекается с пресетами. */
    private const val SOON_OFFSET = 0
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
                // Адрес или кабинет — в тексте: перед выходом он нужнее всего.
                Notifications.showVisit(
                    app,
                    visitId,
                    listOf(visit.title, visit.place).filter { it.isNotBlank() }.joinToString(" · "),
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
