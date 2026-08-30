package tech.unispace.pillreminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.unispace.pillreminder.container
import tech.unispace.pillreminder.data.DoseStatus
import tech.unispace.pillreminder.data.MINUTE_MS
import tech.unispace.pillreminder.data.Settings
import tech.unispace.pillreminder.ui.Lang

/**
 * Срабатывает в момент приёма: показывает уведомление и, если приём так и не отмечен,
 * сам ставит следующий будильник — так напоминание возвращается даже после смахивания.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val doseId = intent.getLongExtra(Notifications.EXTRA_DOSE_ID, -1L)
        val attempt = intent.getIntExtra(EXTRA_ATTEMPT, 0)
        val app = context.applicationContext
        val settings = Settings(app)

        if (intent.getBooleanExtra(EXTRA_TEST, false)) {
            val testFullScreen = intent.getBooleanExtra(EXTRA_TEST_FULL_SCREEN, false)
            Notifications.show(
                app,
                AlarmScheduler.TEST_ID,
                if (testFullScreen) Lang.s.testFsTitle else Lang.s.testTitle,
                Lang.s.testBody,
                useAlarmChannel = settings.alarmSound,
                fullScreen = testFullScreen,
            )
            return
        }

        if (doseId < 0) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = app.container.db
                val dose = db.doseDao().getById(doseId) ?: return@launch
                // Успели отметить между будильниками — молчим и цепочку не продолжаем.
                if (dose.status != DoseStatus.PENDING) {
                    Notifications.dismiss(app, doseId)
                    return@launch
                }

                // Тихие часы: повтор (не первое напоминание) откладывается до их конца.
                val now = System.currentTimeMillis()
                if (attempt > 0 && isQuiet(settings, now)) {
                    AlarmScheduler(app).schedule(
                        dose = dose,
                        triggerAt = quietEndMillis(settings, now),
                        attempt = attempt,
                    )
                    return@launch
                }
                val med = db.medicationDao().getById(dose.medId) ?: return@launch

                // Режим конфиденциальности: ни названия, ни комментария в шторке.
                val private = settings.privateNotifications
                val text = if (private) {
                    ""
                } else {
                    buildString {
                        append(formatAmount(dose.amount))
                        if (med.comment.isNotBlank()) {
                            append(" · ")
                            append(med.comment)
                        }
                    }
                }
                Notifications.show(
                    context = app,
                    doseId = doseId,
                    title = if (private) {
                        Lang.s.timeToTakeFallback
                    } else {
                        Lang.s.timeToTake(med.name)
                    },
                    text = text,
                    useAlarmChannel = settings.alarmSound,
                    attempt = attempt,
                    fullScreen = settings.fullScreenAlarm,
                )

                if (settings.repeatEnabled && attempt + 1 < settings.repeatCount) {
                    var nextAt = System.currentTimeMillis() +
                        settings.repeatIntervalMinutes * MINUTE_MS
                    if (isQuiet(settings, nextAt)) {
                        nextAt = quietEndMillis(settings, nextAt)
                    }
                    AlarmScheduler(app).schedule(
                        dose = dose,
                        triggerAt = nextAt,
                        attempt = attempt + 1,
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ATTEMPT = "attempt"
        const val EXTRA_TEST = "test"
        const val EXTRA_TEST_FULL_SCREEN = "testFullScreen"
    }
}

/** Попадает ли момент в тихие часы (диапазон может пересекать полночь). */
fun isQuiet(settings: Settings, atMillis: Long): Boolean {
    if (!settings.quietEnabled) return false
    val ldt = java.time.Instant.ofEpochMilli(atMillis)
        .atZone(java.time.ZoneId.systemDefault()).toLocalTime()
    val m = ldt.hour * 60 + ldt.minute
    val from = settings.quietFromMinutes
    val to = settings.quietToMinutes
    return if (from <= to) m in from until to else (m >= from || m < to)
}

/** Ближайший конец тихих часов после указанного момента. */
fun quietEndMillis(settings: Settings, atMillis: Long): Long {
    val zone = java.time.ZoneId.systemDefault()
    val date = java.time.Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate()
    val time = java.time.Instant.ofEpochMilli(atMillis).atZone(zone).toLocalTime()
    val m = time.hour * 60 + time.minute
    val to = settings.quietToMinutes
    val endTime = java.time.LocalTime.of(to / 60, to % 60)
    val endDate = if (m < to) date else date.plusDays(1)
    return endDate.atTime(endTime).atZone(zone).toInstant().toEpochMilli()
}
