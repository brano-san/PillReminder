package tech.unispace.pillreminder.data

/** Сколько ждать еду после базового времени приёма «после еды», прежде чем напомнить без неё. */
const val MEAL_WAIT_MAX_MS = 3 * 60 * 60_000L

/**
 * Насколько давняя еда ещё считается «едой перед этим приёмом» для первого приёма набора.
 * Без этого завтрак после подъёма «открывал» бы обеденный приём со смещением «Днём (+6 ч)».
 */
const val MEAL_LOOKBACK_MS = 3 * 60 * 60_000L

/**
 * Приём «после еды» ждёт кнопку «Еда»: еда должна быть отмечена после порога. Для первого приёма набора
 * порог — подъём дня, но не раньше чем за [MEAL_LOOKBACK_MS] до базового времени приёма; для следующих —
 * фактическое время предыдущего приёма этой таблетки в тот же день.
 * Приёмы «по часам» и без правила не ждут. Нет данных о подъёме — не ждём: сравнивать нечего.
 *
 * Одна и та же функция решает, ставить ли будильник ([Planner.rescheduleAlarms]), что писать на карточке
 * (`MedRow.waitsMeal`), что показывать на виджете и кого объединять в групповое уведомление —
 * иначе экран и звонок разойдутся.
 */
fun mealSatisfied(med: Medication, dose: Dose, dayDoses: List<Dose>, meals: List<Long>, wakeAt: Long?): Boolean {
    if (med.afterMealMinutes <= 0 || med.byClock) return true
    val previous = dayDoses
        .filter {
            it.medId == dose.medId && it.dayEpochDay == dose.dayEpochDay &&
                it.indexInDay < dose.indexInDay && it.status != DoseStatus.PENDING
        }
        .maxByOrNull { it.indexInDay }
    val threshold = when {
        previous != null -> previous.takenAt ?: previous.plannedAt
        wakeAt != null -> maxOf(wakeAt, (dose.baseAt ?: dose.plannedAt) - MEAL_LOOKBACK_MS)
        else -> return true
    }
    return meals.any { it >= threshold }
}
