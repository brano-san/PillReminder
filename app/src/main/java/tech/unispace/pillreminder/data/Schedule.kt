package tech.unispace.pillreminder.data

import java.time.LocalDate
import java.time.ZoneId

/**
 * Чистые правила планирования и правки истории. Вынесены из [Planner], чтобы их можно было проверить
 * тестом: ошибка здесь стоит пропущенного приёма.
 */

/**
 * Планировать ли приёмы «по часам» на этот день. Курс, который кончается сегодня, приёмы сегодня ещё даёт:
 * расписание собирается на сегодня и на завтра, и «завтра курса уже нет» не повод снимать сегодняшние.
 */
fun planFixedOn(med: Medication, day: Long): Boolean = !med.isExpiredOn(day) && med.isDueOn(day)

/** Пора ли убрать таблетку в архив: курс кончился по сегодняшнему дню, а не по любому из будущих. */
fun courseOver(med: Medication, today: Long): Boolean = med.isExpiredOn(today)

/**
 * Время отметки. У приёма прошлого дня пишем плановое время, а не «сейчас»: иначе в журнале
 * под вчерашней датой появляется «выпито в 14:00» сегодняшнего дня.
 */
fun markMoment(dose: Dose, now: Long, cycleDay: Long): Long =
    if (dose.dayEpochDay < cycleDay) dose.plannedAt else now

/**
 * Влияет ли отметка на расписание. Правка истории прошлого дня не двигает соседние приёмы
 * и не планирует связанные таблетки: иначе вчерашний день получает приёмы задним числом,
 * а сегодняшний вечер — будильник о вчерашней таблетке.
 */
fun affectsSchedule(dose: Dose, cycleDay: Long): Boolean = dose.dayEpochDay >= cycleDay

/** Сколько неотмеченных приёмов снимет кнопка «Сон»: «по часам» она не трогает. */
fun bedtimeDropCount(doses: List<Dose>, byClockMedIds: Set<Long>): Int =
    doses.count { it.status == DoseStatus.PENDING && it.medId !in byClockMedIds }

/** Действие в журнале; [UNDO] возвращает приём в ожидание. */
enum class JournalAction { TAKE, SKIP, UNDO }

/**
 * Какие кнопки показывать у приёма в журнале. Действие, совпадающее с текущим состоянием,
 * не предлагается: у выпитого приёма нет кнопки «Выпито».
 */
fun journalActions(status: DoseStatus, editable: Boolean, pastDay: Boolean): List<JournalAction> = when {
    !editable -> emptyList()
    status == DoseStatus.TAKEN -> listOf(JournalAction.SKIP, JournalAction.UNDO)
    status == DoseStatus.SKIPPED -> listOf(JournalAction.TAKE, JournalAction.UNDO)
    // Сегодняшний ожидающий приём отмечают на карточке — в журнале кнопок нет.
    pastDay -> listOf(JournalAction.TAKE, JournalAction.SKIP)
    else -> emptyList()
}

/**
 * Приём, который кнопка «Выпить всё, что пора» имеет право отметить: время наступило, но просрочка
 * не больше [graceMs]. Давно просроченный приём отмечать скопом нельзя — это запись в медицинской
 * истории, и человек должен решить по нему отдельно.
 */
fun isTakeAllCandidate(dose: Dose, now: Long, graceMs: Long = OVERDUE_GRACE_MS): Boolean =
    isDueNow(dose, now) && now - dose.plannedAt <= graceMs

/** Просрочен настолько, что сам себя уже не напомнит: его не берут в «пора» и показывают отдельно. */
fun isMissed(dose: Dose, now: Long, graceMs: Long = OVERDUE_GRACE_MS): Boolean =
    dose.status == DoseStatus.PENDING && now - dose.plannedAt > graceMs

/**
 * Приём, который «пора сейчас»: время наступило, он ещё не отмечен и не отложен.
 * Одно правило на счётчик кнопки «Выпить всё, что пора» и на само действие — иначе кнопка
 * обещает одно число, а отмечает другое (в том числе отложенные вручную приёмы).
 */
fun isDueNow(dose: Dose, now: Long): Boolean =
    dose.status == DoseStatus.PENDING && dose.plannedAt <= now && dose.snoozedUntil(now) == null

/** Отложен кнопкой «Отложить»: момент назначен и ещё впереди, а цепочка повторов не начиналась. */
fun Dose.snoozedUntil(now: Long): Long? = remindAt?.takeIf { attempt == 0 && it > now && plannedAt <= now }

/** Что делать с будильником ожидающего приёма: когда звонить и надо ли запомнить момент в приёме. */
data class AlarmPlan(val at: Long?, val remember: Boolean = false)

/**
 * Момент будильника для ожидающего приёма.
 *
 * Просроченный приём (телефон был выключен) не звонит сразу, а подхватывает цепочку повторов через
 * обычный интервал — и этот момент запоминается в приёме: иначе каждая следующая пересборка
 * расписания отодвигала бы напоминание ещё на интервал, и оно не прозвучало бы ни разу.
 */
fun alarmPlan(
    due: Long,
    now: Long,
    remindAt: Long?,
    attempt: Int,
    repeatEnabled: Boolean,
    repeatCount: Int,
    repeatIntervalMs: Long,
    graceMs: Long,
): AlarmPlan = when {
    due > now -> AlarmPlan(due)
    // «Отложить» или уже начатая цепочка повторов назначили момент — уважаем его.
    remindAt != null && remindAt > now -> AlarmPlan(remindAt)
    // Цепочка повторов исчерпана — не начинаем заново при каждой пересборке.
    attempt > 0 && (!repeatEnabled || attempt >= repeatCount) -> AlarmPlan(null)
    // Просрочен давно — молчим, чтобы не звонить среди дня о пропущенном утреннем приёме.
    now - due > graceMs -> AlarmPlan(null)
    repeatEnabled -> AlarmPlan(now + repeatIntervalMs, remember = true)
    else -> AlarmPlan(null)
}

/**
 * Момент «HH:MM этого дня» в местной зоне. Считать «полночь + N миллисекунд» нельзя:
 * в день перевода стрелок приём «по часам» уезжал на час.
 */
fun fixedTimeMillis(date: LocalDate, minutes: Int, zone: ZoneId = ZoneId.systemDefault()): Long =
    date.atTime(minutes / 60, minutes % 60).atZone(zone).toInstant().toEpochMilli()

/** Последний день курса; null — курс бессрочный. `isExpiredOn` срабатывает уже на следующий день. */
fun courseEndDay(cycleStartEpochDay: Long, durationDays: Int): Long? =
    if (durationDays > 0) cycleStartEpochDay + durationDays - 1 else null

/** Сколько дней хранить отметки «Еда»: дальше они не нужны ни расписанию, ни истории. */
const val MEAL_KEEP_DAYS = 180

/** Граница подрезки отметок «Еда»: всё старше удаляется, таблица не растёт бесконечно. */
fun mealPruneBefore(now: Long, keepDays: Int = MEAL_KEEP_DAYS): Long = now - keepDays * 24L * 60 * 60 * 1000

/**
 * Сколько дней курса осталось, считая сегодняшний. null — курс бессрочный, 0 — уже закончился.
 * Нужно карточке: курс заканчивается тихо, и без счётчика человек узнаёт об этом по исчезнувшей таблетке.
 */
fun courseDaysLeft(med: Medication, today: Long): Int? {
    val end = courseEndDay(med.cycleStartEpochDay, med.durationDays) ?: return null
    // Ноль не возвращаем: «курс закончился» на живой карточке — это состояние между истечением курса
    // и ближайшей пересборкой расписания, человеку оно ничего не говорит.
    return (end - today + 1).toInt().takeIf { it > 0 }
}
