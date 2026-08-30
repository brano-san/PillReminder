package tech.unispace.pillreminder.data

import android.content.Context
import tech.unispace.pillreminder.alarm.AlarmScheduler
import tech.unispace.pillreminder.alarm.Notifications
import tech.unispace.pillreminder.alarm.WakeReminder
import tech.unispace.pillreminder.widget.PillWidgetProvider
import java.time.LocalDate
import java.time.ZoneId

const val MINUTE_MS = 60_000L

fun today(): Long = LocalDate.now().toEpochDay()

fun epochDayOf(millis: Long): Long =
    java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

/**
 * Вся логика расписания живёт здесь.
 *
 * Правила:
 *  1. День начинается с кнопки «я проснулся». До неё приёмов на день не запланировано.
 *  2. Первый приём таблетки = момент пробуждения + её персональное смещение.
 *     Именно так разводятся «за 15 мин до еды» (смещение 0) и «через 15 мин после еды»
 *     (смещение, скажем, 45) — обе утренние, но с зазором.
 *  3. Каждый следующий приём отсчитывается от ФАКТИЧЕСКОГО времени предыдущего:
 *     нажал «выпил» — таймер до следующего пошёл заново от этого момента.
 */
class Planner(private val context: Context, private val db: AppDatabase) {

    private val meds = db.medicationDao()
    private val doses = db.doseDao()
    private val wakes = db.wakeDao()
    private val groups = db.groupDao()

    suspend fun ensureDefaultGroup(): Long {
        val existing = groups.getAll()
        return existing.firstOrNull()?.id ?: groups.insert(MedGroup(name = "Мои таблетки"))
    }

    /** Принимается ли таблетка в этот день по правилу «N раз в N дней». */
    fun isDueOn(med: Medication, day: Long): Boolean {
        if (med.everyNDays <= 1) return true
        val n = med.everyNDays.toLong()
        return ((day - med.cycleStartEpochDay) % n + n) % n == 0L
    }

    /**
     * Нажата кнопка «я проснулся»: фиксируем момент и пересобираем весь день заново.
     * Уже отмеченные приёмы не трогаем — сдвигаются только ожидающие.
     */
    suspend fun wakeUp(now: Long = System.currentTimeMillis()): Long {
        val day = epochDayOf(now)
        wakes.upsert(WakeEvent(dayEpochDay = day, wakeAt = now))
        doses.deletePendingOnDay(day)
        val takenIndexes = doses.getDay(day)
            .groupBy { it.medId }
            .mapValues { (_, list) -> list.count { it.status != DoseStatus.PENDING } }

        val planned = mutableListOf<Dose>()
        for (med in meds.getActive()) {
            // Курс закончился — таблетка сама уходит в неактивные.
            if (med.isExpiredOn(day)) {
                meds.deactivate(med.id)
                continue
            }
            // Связанные таблетки планируются не от пробуждения, а от приёма родителя.
            if (med.asNeeded || med.linkedToMedId != null || !isDueOn(med, day)) continue
            val alreadyTaken = takenIndexes[med.id] ?: 0
            val first = now + med.firstDoseOffsetMinutes * MINUTE_MS
            for (k in alreadyTaken until med.timesPerDay) {
                planned += Dose(
                    medId = med.id,
                    dayEpochDay = day,
                    indexInDay = k,
                    plannedAt = first + (k - alreadyTaken) * med.intervalMinutes * MINUTE_MS,
                    amount = med.dosesPerIntake,
                    medNameSnapshot = med.name,
                )
            }
        }
        doses.insertAll(planned)
        rescheduleAlarms()
        return now
    }

    /**
     * Пересобрать ожидающие приёмы одной таблетки на сегодня — после добавления
     * или редактирования, если «я проснулся» на сегодня уже нажато.
     */
    suspend fun refreshMedToday(medId: Long) {
        val day = today()
        val wake = wakes.getDay(day) ?: return
        val med = meds.getById(medId) ?: return
        doses.deletePendingForMedOnDay(medId, day)
        if (!med.active || med.asNeeded || med.isExpiredOn(day) || !isDueOn(med, day)) {
            rescheduleAlarms()
            return
        }
        val done = doses.getDay(day).filter { it.medId == medId && it.status != DoseStatus.PENDING }
        val alreadyTaken = done.size
        // Если приёмы уже были — отсчитываем от последнего фактического.
        // Иначе: связанная таблетка ждёт первого приёма родителя, обычная идёт от пробуждения.
        val base = done.maxByOrNull { it.indexInDay }
            ?.let { (it.takenAt ?: it.plannedAt) + med.intervalMinutes * MINUTE_MS }
            ?: if (med.linkedToMedId != null) {
                val parentFirst = doses.getDay(day)
                    .firstOrNull {
                        it.medId == med.linkedToMedId && it.indexInDay == 0 &&
                            it.status == DoseStatus.TAKEN
                    }
                if (parentFirst == null) {
                    // Родитель ещё не выпит — приёмы появятся после его отметки.
                    rescheduleAlarms()
                    return
                }
                (parentFirst.takenAt ?: parentFirst.plannedAt) + med.linkedDelayMinutes * MINUTE_MS
            } else {
                wake.wakeAt + med.firstDoseOffsetMinutes * MINUTE_MS
            }

        val planned = (alreadyTaken until med.timesPerDay).map { k ->
            Dose(
                medId = med.id,
                dayEpochDay = day,
                indexInDay = k,
                plannedAt = base + (k - alreadyTaken) * med.intervalMinutes * MINUTE_MS,
                amount = med.dosesPerIntake,
                medNameSnapshot = med.name,
            )
        }
        doses.insertAll(planned)
        rescheduleAlarms()
    }

    /** «Выпил». Помечает приём и сдвигает все последующие приёмы этой таблетки на сегодня. */
    suspend fun markTaken(doseId: Long, now: Long = System.currentTimeMillis()) {
        val dose = doses.getById(doseId) ?: return
        if (dose.status == DoseStatus.TAKEN) return
        doses.update(dose.copy(status = DoseStatus.TAKEN, takenAt = now))
        shiftFollowing(dose, from = now)
        if (dose.indexInDay == 0) {
            startLinkedMeds(dose, now)
        }
        decrementStock(dose.medId, dose.amount)
        rescheduleAlarms()
    }

    /** Первый приём родителя выпит — запускаем таймеры связанных с ним таблеток. */
    private suspend fun startLinkedMeds(parentDose: Dose, now: Long) {
        val day = parentDose.dayEpochDay
        val todays = doses.getDay(day)
        for (child in meds.childrenOf(parentDose.medId)) {
            if (child.asNeeded || child.isExpiredOn(day) || !isDueOn(child, day)) continue
            if (todays.any { it.medId == child.id }) continue
            val first = now + child.linkedDelayMinutes * MINUTE_MS
            doses.insertAll(
                (0 until child.timesPerDay).map { k ->
                    Dose(
                        medId = child.id,
                        dayEpochDay = day,
                        indexInDay = k,
                        plannedAt = first + k * child.intervalMinutes * MINUTE_MS,
                        amount = child.dosesPerIntake,
                        medNameSnapshot = child.name,
                    )
                },
            )
        }
    }

    /** Списать остаток и, если запас пересёк порог, напомнить о покупке. */
    private suspend fun decrementStock(medId: Long, amount: Double) {
        val med = meds.getById(medId) ?: return
        val stock = med.stockCount ?: return
        val newStock = (stock - amount).coerceAtLeast(0.0)
        meds.update(med.copy(stockCount = newStock))
        val threshold = Settings(context).lowStockThreshold.toDouble()
        if (stock > threshold && newStock <= threshold) {
            Notifications.showLowStock(context, med.id, med.name, newStock)
        }
    }

    /** «Пропустил». Следующий приём отсчитывается от планового времени пропущенного. */
    suspend fun markSkipped(doseId: Long, now: Long = System.currentTimeMillis()) {
        val dose = doses.getById(doseId) ?: return
        if (dose.status != DoseStatus.PENDING) return
        doses.update(dose.copy(status = DoseStatus.SKIPPED, takenAt = now))
        shiftFollowing(dose, from = maxOf(dose.plannedAt, now))
        rescheduleAlarms()
    }

    private suspend fun shiftFollowing(dose: Dose, from: Long) {
        val med = meds.getById(dose.medId) ?: return
        val following = doses.pendingForMedOnDay(dose.medId, dose.dayEpochDay)
            .filter { it.indexInDay > dose.indexInDay }
            .sortedBy { it.indexInDay }
        if (following.isEmpty()) return
        var t = from + med.intervalMinutes * MINUTE_MS
        val updated = following.map { d ->
            d.copy(plannedAt = t).also { t += med.intervalMinutes * MINUTE_MS }
        }
        doses.updateAll(updated)
    }

    /** Зафиксировать внеплановый приём «по необходимости» — прямо сейчас, без будильников. */
    suspend fun takeNow(medId: Long, now: Long = System.currentTimeMillis()) {
        val med = meds.getById(medId) ?: return
        val day = epochDayOf(now)
        val index = doses.getDay(day).count { it.medId == medId }
        doses.insert(
            Dose(
                medId = medId,
                dayEpochDay = day,
                indexInDay = index,
                plannedAt = now,
                status = DoseStatus.TAKEN,
                takenAt = now,
                amount = med.dosesPerIntake,
                medNameSnapshot = med.name,
            ),
        )
        decrementStock(medId, med.dosesPerIntake)
    }

    /** Завершить курс вручную: снять таблетку с планирования и убрать её приёмы на сегодня. */
    suspend fun finishCourse(medId: Long) {
        meds.deactivate(medId)
        doses.deletePendingForMedOnDay(medId, today())
        rescheduleAlarms()
    }

    /** Отменить отметку — вернуть приём в ожидание (для истории/опечаток). */
    suspend fun undo(doseId: Long) {
        val dose = doses.getById(doseId) ?: return
        // Возврат «выпитого» возвращает и списанный остаток.
        if (dose.status == DoseStatus.TAKEN) {
            meds.getById(dose.medId)?.let { med ->
                med.stockCount?.let { meds.update(med.copy(stockCount = it + dose.amount)) }
            }
        }
        doses.update(dose.copy(status = DoseStatus.PENDING, takenAt = null))
        rescheduleAlarms()
    }

    /** «Выпить всё, что пора»: отметить все ожидающие приёмы, чьё время уже наступило. */
    suspend fun takeAllDue(now: Long = System.currentTimeMillis()) {
        doses.getDay(epochDayOf(now))
            .filter { it.status == DoseStatus.PENDING && it.plannedAt <= now }
            .forEach { markTaken(it.id, now) }
    }

    suspend fun rescheduleAlarms() {
        val scheduler = AlarmScheduler(context)
        val settings = Settings(context)
        val now = System.currentTimeMillis()
        val day = today()
        val todayDoses = doses.getDay(day)
        scheduler.cancelAll(todayDoses.map { it.id } + doses.getDay(day + 1).map { it.id })

        for (dose in todayDoses.filter { it.status == DoseStatus.PENDING }) {
            val at = when {
                dose.plannedAt > now -> dose.plannedAt
                // Приём просрочен (перезагрузка, смена времени) — не звоним сразу,
                // а подхватываем цепочку повторов через обычный интервал.
                settings.repeatEnabled -> now + settings.repeatIntervalMinutes * MINUTE_MS
                else -> continue
            }
            scheduler.schedule(dose, at)
        }
        PillWidgetProvider.refresh(context)
        WakeReminder.schedule(context)
    }
}
