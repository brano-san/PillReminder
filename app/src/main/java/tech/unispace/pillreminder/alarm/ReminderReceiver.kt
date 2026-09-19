package tech.unispace.pillreminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.unispace.pillreminder.container
import tech.unispace.pillreminder.data.Dose
import tech.unispace.pillreminder.data.DoseStatus
import tech.unispace.pillreminder.data.MEAL_WAIT_MAX_MS
import tech.unispace.pillreminder.data.MINUTE_MS
import tech.unispace.pillreminder.data.OVERDUE_GRACE_MS
import tech.unispace.pillreminder.data.Planner
import tech.unispace.pillreminder.data.Settings
import tech.unispace.pillreminder.data.mealSatisfied
import tech.unispace.pillreminder.ui.Lang
import tech.unispace.pillreminder.ui.formatClock
import tech.unispace.pillreminder.data.snoozedUntil

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
            // Проверочное уведомление — без кнопок действий: за ним нет приёма, нажимать было бы нечего.
            Notifications.show(
                app,
                AlarmScheduler.TEST_ID,
                title = if (testFullScreen) Lang.s.testFsTitle else Lang.s.testTitle,
                text = Lang.s.testBody,
                useAlarmChannel = settings.alarmSound,
                fullScreen = testFullScreen,
                withActions = false,
            )
            return
        }

        if (doseId < 0) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = app.container.db
                val planner = app.container.planner
                val dose = db.doseDao().getById(doseId) ?: return@launch
                // Успели отметить между будильниками — молчим и цепочку не продолжаем.
                if (dose.status != DoseStatus.PENDING) {
                    Notifications.dismiss(app, doseId)
                    return@launch
                }

                // Тихие часы: повтор (не первое напоминание) откладывается до их конца.
                val now = System.currentTimeMillis()
                if (attempt > 0 && isQuiet(settings, now)) {
                    planner.rememberRepeat(doseId, quietEndMillis(settings, now), attempt)
                    return@launch
                }
                val medsById = db.medicationDao().getAllIncludingInactive().associateBy { it.id }
                val med = medsById[dose.medId] ?: return@launch
                val private = settings.privateNotifications

                val dayDoses = db.doseDao().getDay(dose.dayEpochDay)
                val meals = db.mealDao().getAll().map { it.atMillis }
                val wakeAt = db.wakeDao().getDay(dose.dayEpochDay)?.wakeAt
                // Приём «после еды» дождался времени, а «Еда» не нажата: не звоним, а мягко просим отметить еду —
                // забытая кнопка иначе стоила бы трёх часов тишины. Страховку через MEAL_WAIT_MAX_MS ставит пересборка.
                val gated = !mealSatisfied(med, dose, dayDoses, meals, wakeAt)
                if (gated && System.currentTimeMillis() - dose.plannedAt < MEAL_WAIT_MAX_MS) {
                    val relation = Lang.s.mealRelation(med.afterMealMinutes, 0, med.mealCalories) ?: Lang.s.mealAfterNow
                    Notifications.showMealPrompt(
                        context = app,
                        doseId = doseId,
                        title = if (private) Lang.s.mealPromptTitleFallback else Lang.s.mealPromptTitle(med.name),
                        text = Lang.s.mealPromptBody(relation),
                    )
                    planner.rescheduleAlarms()
                    return@launch
                }

                // Режим конфиденциальности: ни названия, ни комментария в шторке.
                val text = if (private) {
                    ""
                } else {
                    buildString {
                        // Плановое время: увидев напоминание в 11 утра, человек должен понимать,
                        // это про 08:00 или про сейчас.
                        append(Lang.s.plannedAtShort(formatClock(dose.plannedAt)))
                        append(" · ")
                        append(formatAmount(dose.amount, med.form))
                        // Связь с едой обязана быть видна в шторке; личный комментарий — нет,
                        // он остаётся на карточке таблетки.
                        Lang.s.mealRelation(med.afterMealMinutes, med.beforeMealMinutes, med.mealCalories)?.let {
                            append(" · ")
                            append(it)
                        }
                        // Три часа без «Еда»: напоминаем всё равно и честно пишем, почему.
                        if (gated) append(" · ").append(Lang.s.mealNotMarked)
                    }
                }
                // Всё, чему сейчас пора, — одним уведомлением, а не стопкой: иначе приёмы с разным плановым временем
                // («Сразу» и «+30 мин») вели отдельные цепочки повторов и звонили вдвоём каждые три минуты.
                // В группу не берём: ждущих кнопку «Еда» (тот же судья, что у будильника — mealSatisfied), отложенных
                // и просроченных больше OVERDUE_GRACE_MS — те остаются в списке дня молча. Сам сработавший приём — всегда.
                val batch = (
                    dayDoses.filter { d ->
                        d.status == DoseStatus.PENDING && d.plannedAt <= now + GROUP_WINDOW_MS &&
                            now - d.plannedAt <= OVERDUE_GRACE_MS && d.snoozedUntil(now) == null &&
                            medsById[d.medId]?.let { m -> m.active && mealSatisfied(m, d, dayDoses, meals, wakeAt) } == true
                    } + dose
                    ).distinctBy { it.id }.sortedBy { it.id }
                val leader = batch.first()
                if (batch.size > 1 && leader.id != doseId) {
                    // Уведомление ведёт «старший» приём группы, но свой повтор ведомый ставит сам:
                    // когда старшего отметят, следующим звонком старшим станет он. Своё прежнее одиночное
                    // уведомление ведомый снимает — иначе в шторке висели бы и оно, и групповое.
                    Notifications.dismiss(app, doseId)
                    scheduleNext(planner, settings, dose, attempt)
                    return@launch
                }

                if (batch.size > 1) {
                    val names = if (private) {
                        ""
                    } else {
                        batch.joinToString("\n") { d ->
                            // Слово «таблетки/капли» — по форме каждой таблетки, а не по форме старшей.
                            formatClock(d.plannedAt) + " · " + d.medNameSnapshot + " · " +
                                formatAmount(d.amount, medsById[d.medId]?.form ?: med.form)
                        }
                    }
                    Notifications.showGroup(
                        context = app,
                        doses = batch,
                        title = Lang.s.groupNotifTitle(batch.size),
                        text = names,
                        useAlarmChannel = settings.alarmSound,
                        attempt = attempt,
                        fullScreen = settings.fullScreenAlarm,
                    )
                } else {
                    Notifications.show(
                        context = app,
                        doseId = doseId,
                        plannedAt = dose.plannedAt,
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
                }

                scheduleNext(planner, settings, dose, attempt)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Следующий повтор — или конец цепочки. И то и другое записывается в приём (`remindAt`, `attempt`),
     * чтобы пересборка будильников продолжила цепочку с того же места, а не начала заново.
     */
    private suspend fun scheduleNext(planner: Planner, settings: Settings, dose: Dose, attempt: Int) {
        if (settings.repeatEnabled && attempt + 1 < settings.repeatCount) {
            var nextAt = System.currentTimeMillis() + settings.repeatIntervalMinutes * MINUTE_MS
            if (isQuiet(settings, nextAt)) {
                nextAt = quietEndMillis(settings, nextAt)
            }
            planner.rememberRepeat(dose.id, nextAt, attempt + 1)
        } else {
            planner.rememberRepeat(dose.id, null, attempt + 1)
        }
    }

    companion object {
        const val EXTRA_ATTEMPT = "attempt"

        /** Запас вперёд: приём, чьё время наступает в ближайшую минуту, попадает в группу к текущему звонку. */
        private const val GROUP_WINDOW_MS = 60_000L
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
