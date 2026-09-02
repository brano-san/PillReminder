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
/** Как долго последняя еда влияет на сдвиг приёмов. */
private const val MEAL_RELEVANT_MS = 12 * 60 * 60_000L

/** Насколько просроченный приём ещё имеет смысл озвучивать будильником. */
private const val OVERDUE_GRACE_MS = 2 * 60 * 60_000L

/**
 * Предельная длина «дня»: график плавающий (лечь можно и в три ночи), поэтому день не
 * заканчивается в полночь. Но и бесконечным он быть не может — через 23 часа цикл считается
 * завершённым и кнопка «Я проснулся» появляется снова.
 */
const val CYCLE_MAX_MS = 18L * 60 * 60_000L

class Planner(private val context: Context, private val db: AppDatabase) {

    private val meds = db.medicationDao()
    private val doses = db.doseDao()
    private val wakes = db.wakeDao()
    private val groups = db.groupDao()
    private val mealDao = db.mealDao()

    suspend fun ensureDefaultGroup(): Long {
        val existing = groups.getAll()
        return existing.firstOrNull()?.id ?: groups.insert(MedGroup(name = "Мои таблетки"))
    }

    /**
     * Текущий незакрытый «день»: последнее пробуждение, если с него прошло меньше [CYCLE_MAX_MS].
     * null — день ещё не начат или уже слишком стар.
     */
    suspend fun currentCycle(now: Long = System.currentTimeMillis()): WakeEvent? =
        wakes.latest()?.takeIf { it.bedAt == null && now - it.wakeAt in 0 until CYCLE_MAX_MS }

    /** День, к которому относятся приёмы «сейчас»: цикл пробуждения, иначе календарный. */
    suspend fun cycleDay(now: Long = System.currentTimeMillis()): Long =
        currentCycle(now)?.dayEpochDay ?: epochDayOf(now)

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
        // Новый день — про «всё выпито» можно будет сказать снова.
        Settings(context).dayDoneNotifiedFor = -1L
        doses.deletePendingOnDay(day)
        // Снимок дня уже без ожидающих: остались только отмеченные приёмы.
        val dayDoses = doses.getDay(day).groupBy { it.medId }
        val takenIndexes = dayDoses.mapValues { (_, list) -> list.count { it.status != DoseStatus.PENDING } }
        val lastIndexes = dayDoses.mapValues { (_, list) -> list.maxOf { it.indexInDay } }

        val planned = mutableListOf<Dose>()
        for (med in meds.getActive()) {
            // Курс закончился — таблетка сама уходит в неактивные.
            if (med.isExpiredOn(day)) {
                meds.deactivate(med.id)
                continue
            }
            // Связанные таблетки планируются не от пробуждения, а от приёма родителя.
            // «По часам» и связанные не зависят от пробуждения.
            if (med.asNeeded || med.linkedToMedId != null || med.byClock || !isDueOn(med, day)) continue
            if (med.timesPerDay <= 0) continue
            val alreadyTaken = takenIndexes[med.id] ?: 0
            // Считаем не «сколько отмечено за сутки», а «сколько отмечено в текущем наборе»:
            // при полностью закрытом дне остаток нулевой, и «начать новый день» даёт полный набор.
            val takenInSet = alreadyTaken % med.timesPerDay
            val count = med.timesPerDay - takenInSet
            // Нумерация нового набора продолжается после уже существующих приёмов дня.
            val startIndex = maxOf((lastIndexes[med.id] ?: -1) + 1, alreadyTaken)
            val first = now + med.firstDoseOffsetMinutes * MINUTE_MS
            for (i in 0 until count) {
                planned += Dose(
                    medId = med.id,
                    dayEpochDay = day,
                    indexInDay = startIndex + i,
                    plannedAt = first + i * med.intervalMinutes * MINUTE_MS,
                    baseAt = first + i * med.intervalMinutes * MINUTE_MS,
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
        // День плавающий: цикл мог начаться вчера вечером.
        val day = cycleDay()
        val med = meds.getById(medId) ?: return
        doses.deletePendingForMedOnDay(medId, day)
        // Завтрашние заготовки «по часам» тоже пересобираем — правило могло измениться.
        doses.deletePendingForMedOnDay(medId, day + 1)
        if (med.byClock) {
            // Расписание по часам не ждёт кнопку «я проснулся».
            rescheduleAlarms()
            return
        }
        val wake = currentCycle() ?: run {
            rescheduleAlarms()
            return
        }
        if (!med.active || med.asNeeded || med.isExpiredOn(day) || !isDueOn(med, day)) {
            rescheduleAlarms()
            return
        }
        if (med.timesPerDay <= 0) {
            rescheduleAlarms()
            return
        }
        val mine = doses.getDay(day).filter { it.medId == medId }
        val done = mine.filter { it.status != DoseStatus.PENDING }
        val alreadyTaken = done.size
        val takenInSet = alreadyTaken % med.timesPerDay
        val count = med.timesPerDay - takenInSet
        val startIndex = maxOf((mine.maxOfOrNull { it.indexInDay } ?: -1) + 1, alreadyTaken)
        // Внутри начатого набора отсчитываем от последнего фактического приёма.
        // Набор новый (takenInSet == 0) — от пробуждения или от приёма родителя.
        val base = (if (takenInSet > 0) done.maxByOrNull { it.indexInDay } else null)
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

        val planned = (0 until count).map { i ->
            Dose(
                medId = med.id,
                dayEpochDay = day,
                indexInDay = startIndex + i,
                plannedAt = base + i * med.intervalMinutes * MINUTE_MS,
                baseAt = base + i * med.intervalMinutes * MINUTE_MS,
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
        // Родитель запускает связанные таблетки на КАЖДОМ первом приёме своего набора,
        // иначе во втором цикле тех же суток они не появятся.
        val parentTimes = meds.getById(dose.medId)?.timesPerDay?.coerceAtLeast(1) ?: 1
        if (dose.indexInDay % parentTimes == 0) {
            startLinkedMeds(dose, now)
        }
        decrementStock(dose.medId, dose.amount)
        rescheduleAlarms()
        notifyDayDoneIfNeeded(dose.dayEpochDay)
    }

    /** Первый приём родителя выпит — запускаем таймеры связанных с ним таблеток. */
    private suspend fun startLinkedMeds(parentDose: Dose, now: Long) {
        val day = parentDose.dayEpochDay
        val todays = doses.getDay(day)
        for (child in meds.childrenOf(parentDose.medId)) {
            if (child.asNeeded || child.isExpiredOn(day) || !isDueOn(child, day)) continue
            val mine = todays.filter { it.medId == child.id }
            // Незакрытый набор уже стоит — второй раз не плодим.
            if (mine.any { it.status == DoseStatus.PENDING }) continue
            val startIndex = (mine.maxOfOrNull { it.indexInDay } ?: -1) + 1
            val first = now + child.linkedDelayMinutes * MINUTE_MS
            doses.insertAll(
                (0 until child.timesPerDay).map { k ->
                    Dose(
                        medId = child.id,
                        dayEpochDay = day,
                        indexInDay = startIndex + k,
                        plannedAt = first + k * child.intervalMinutes * MINUTE_MS,
                        baseAt = first + k * child.intervalMinutes * MINUTE_MS,
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
        notifyDayDoneIfNeeded(dose.dayEpochDay)
    }

    private suspend fun shiftFollowing(dose: Dose, from: Long) {
        val med = meds.getById(dose.medId) ?: return
        // Приёмы «по часам» стоят на своих временах: ранний или поздний приём их не сдвигает.
        if (med.byClock) return
        val following = doses.pendingForMedOnDay(dose.medId, dose.dayEpochDay)
            .filter { it.indexInDay > dose.indexInDay }
            .sortedBy { it.indexInDay }
        if (following.isEmpty()) return
        var t = from + med.intervalMinutes * MINUTE_MS
        val updated = following.map { d ->
            d.copy(plannedAt = t, baseAt = t).also { t += med.intervalMinutes * MINUTE_MS }
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

    /**
     * Часовой пояс или время системы изменились: приёмы «по часам» держат абсолютное время,
     * поэтому их надо пересобрать от нового локального полудня.
     */
    suspend fun resyncAfterTimeChange() {
        val day = today()
        for (med in meds.getActive().filter { it.byClock }) {
            doses.deletePendingForMedOnDay(med.id, day)
            doses.deletePendingForMedOnDay(med.id, day + 1)
        }
        rescheduleAlarms()
    }

    /**
     * «Ложусь спать»: день закрывается сразу, кнопка «Я проснулся» возвращается на главную.
     * Момент отхода ко сну попадает в историю дня, даже если трекера сна нет.
     */
    suspend fun goToBed(now: Long = System.currentTimeMillis()) {
        val cycle = wakes.latest() ?: return
        wakes.upsert(cycle.copy(bedAt = now))
        rescheduleAlarms()
    }

    /** «Я поел»: фиксируем время и подвигаем приёмы, которые нельзя пить сразу после еды. */
    suspend fun recordMeal(now: Long = System.currentTimeMillis()) {
        mealDao.insert(MealEvent(atMillis = now))
        // Хранить историю еды дольше месяца незачем — она нужна только для сдвигов.
        mealDao.deleteOlderThan(now - 30L * 24 * 60 * 60_000L)
        rescheduleAlarms()
    }

    /**
     * Сдвинуть ожидающие приёмы под ограничения «не раньше чем через N минут после еды»
     * и «разносить с другими таблетками». Двигаем только вперёд и только ожидающие.
     */
    private suspend fun applyIntakeConstraints(day: Long) {
        val all = doses.getDay(day)
        val pending = all.filter { it.status == DoseStatus.PENDING }
        if (pending.isEmpty()) return
        // Берём только свежую еду: вчерашний ужин не должен двигать сегодняшние приёмы,
        // а за день приёмов пищи несколько — важен последний.
        val lastMeal = mealDao.last()?.atMillis?.takeIf { System.currentTimeMillis() - it < MEAL_RELEVANT_MS }
        val takenTimes = all.filter { it.status == DoseStatus.TAKEN }
        val medsById = meds.getAllIncludingInactive().associateBy { it.id }

        val moved = pending.mapNotNull { dose ->
            val med = medsById[dose.medId] ?: return@mapNotNull null
            // «По часам» — обещание точного времени, его не двигает ничто.
            if (med.byClock) return@mapNotNull null
            // Считаем от исходного времени: иначе каждый пересчёт добавлял бы ещё один сдвиг.
            val startFrom = dose.baseAt ?: dose.plannedAt
            var at = startFrom
            if (med.afterMealMinutes > 0 && lastMeal != null) {
                val earliest = lastMeal + med.afterMealMinutes * MINUTE_MS
                if (at < earliest) at = earliest
            }
            if (med.apartFromOthersMinutes > 0) {
                val gap = med.apartFromOthersMinutes * MINUTE_MS
                // Пустой список = «с любыми другими», иначе только с выбранными таблетками.
                // Родитель связки исключается всегда: его задержку пользователь задал явно.
                val only = med.apartFromList()
                takenTimes
                    .filter { it.medId != dose.medId && it.medId != med.linkedToMedId }
                    .filter { only.isEmpty() || it.medId in only }
                    .mapNotNull { it.takenAt }
                    .sorted()
                    .forEach { t -> if (at > t - gap && at < t + gap) at = t + gap }
            }
            // Сдвиг ограничен: лучше напомнить чуть раньше, чем увезти приём на вечер.
            val limit = startFrom + maxOf(med.intervalMinutes, 60) * 2 * MINUTE_MS
            if (at > limit) at = limit
            if (at != dose.plannedAt) dose.copy(plannedAt = at) else null
        }
        if (moved.isNotEmpty()) doses.updateAll(moved)
    }

    /** Все приёмы дня отмечены — сообщаем, что день можно закрывать. */
    private suspend fun notifyDayDoneIfNeeded(day: Long) {
        val settings = Settings(context)
        if (settings.dayDoneNotifiedFor == day) return
        val list = doses.getDay(day)
        if (list.isEmpty() || list.any { it.status == DoseStatus.PENDING }) return
        settings.dayDoneNotifiedFor = day
        Notifications.showDayDone(context)
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

    /**
     * Приёмы «по часам» не зависят от кнопки «я проснулся»: держим их в базе на сегодня
     * и на завтра, чтобы будильник стоял, даже если приложение не открывали.
     * Уже существующие приёмы (в том числе отмеченные) не трогаем.
     */
    suspend fun syncFixedSchedule(days: List<Long> = listOf(today(), today() + 1)) {
        val fixed = meds.getActive().filter { it.byClock && !it.asNeeded }
        if (fixed.isEmpty()) return
        for (day in days) {
            val existing = doses.getDay(day)
            val planned = mutableListOf<Dose>()
            val dayStart = LocalDate.ofEpochDay(day)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            for (med in fixed) {
                if (med.isExpiredOn(day) || !isDueOn(med, day)) continue
                val mine = existing.filter { it.medId == med.id }
                med.fixedTimesList().forEachIndexed { k, minutes ->
                    if (mine.none { it.indexInDay == k }) {
                        planned += Dose(
                            medId = med.id,
                            dayEpochDay = day,
                            indexInDay = k,
                            plannedAt = dayStart + minutes * MINUTE_MS,
                            baseAt = dayStart + minutes * MINUTE_MS,
                            amount = med.dosesPerIntake,
                            medNameSnapshot = med.name,
                        )
                    }
                }
            }
            doses.insertAll(planned)
        }
    }

    suspend fun rescheduleAlarms() {
        val scheduler = AlarmScheduler(context)
        val settings = Settings(context)
        val now = System.currentTimeMillis()
        val calendarDay = today()
        // «День» плавающий: приёмы могут относиться ко вчерашнему циклу, начатому вечером.
        val day = cycleDay(now)
        val days = listOf(day, calendarDay, calendarDay + 1).distinct()
        // Сначала дособираем расписание «по часам», потом уже ставим будильники.
        syncFixedSchedule(listOf(calendarDay, calendarDay + 1))
        applyIntakeConstraints(day)
        val relevant = days.flatMap { doses.getDay(it) }
        scheduler.cancelAll(relevant.map { it.id })

        for (dose in relevant.filter { it.status == DoseStatus.PENDING }) {
            val at = when {
                dose.plannedAt > now -> dose.plannedAt
                // Приём просрочен давно (телефон был выключен) — молчим, чтобы не звонить
                // среди дня о пропущенном утреннем приёме; он остаётся в списке дня.
                now - dose.plannedAt > OVERDUE_GRACE_MS -> continue
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
