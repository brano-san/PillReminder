package tech.unispace.pillreminder.data

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * За сколько до планового времени отметка «выпил» считается преждевременной: диалог «Ещё рано»
 * на карточке и порог для кнопки «Выпито» на виджете.
 */
const val EARLY_TAKE_THRESHOLD_MS = 60 * 60_000L

/**
 * Предельная длина «дня»: график плавающий (лечь можно и в три ночи), поэтому день не
 * заканчивается в полночь. Но и бесконечным он быть не может — через CYCLE_MAX_MS (18 часов)
 * цикл считается завершённым и кнопка «Подъём» появляется снова.
 */
const val CYCLE_MAX_MS = 18L * 60 * 60_000L

class Planner(private val context: Context, private val db: AppDatabase) {

    private val meds = db.medicationDao()
    private val doses = db.doseDao()
    private val wakes = db.wakeDao()
    private val groups = db.groupDao()
    private val mealDao = db.mealDao()

    /**
     * Все мутации расписания идут под одним замком: `rescheduleAlarms()` запускается параллельно из
     * App.onCreate, BootReceiver и ViewModel, а `syncFixedSchedule` («прочитал — вставил») без замка
     * плодит дубли приёмов «по часам». Mutex не реентерабелен: публичные методы берут замок и зовут
     * *Locked-варианты, внутри друг друга — только *Locked.
     */
    private val lock = Mutex()

    suspend fun ensureDefaultGroup(): Long {
        val existing = groups.getAll()
        return existing.firstOrNull()?.id ?: groups.insert(MedGroup(name = "Мои таблетки"))
    }

    /**
     * Текущий незакрытый «день»: последнее пробуждение, если оно ещё [WakeEvent.isActive].
     * null — день ещё не начат, закрыт кнопкой «Сон» или уже слишком стар.
     */
    suspend fun currentCycle(now: Long = System.currentTimeMillis()): WakeEvent? =
        wakes.latest()?.takeIf { it.isActive(now) }

    /** День, к которому относятся приёмы «сейчас»: цикл пробуждения, иначе календарный. */
    suspend fun cycleDay(now: Long = System.currentTimeMillis()): Long =
        currentCycle(now)?.dayEpochDay ?: epochDayOf(now)

    /** Принимается ли таблетка в этот день по правилу «N раз в N дней». */
    fun isDueOn(med: Medication, day: Long): Boolean {
        if (med.everyNDays <= 1) return true
        val n = med.everyNDays.toLong()
        return ((day - med.cycleStartEpochDay) % n + n) % n == 0L
    }

    // ---------- Публичный API: каждая мутация под замком и заканчивается пересборкой будильников ----------

    /**
     * Нажата кнопка «я проснулся»: фиксируем момент и пересобираем весь день заново.
     * Уже отмеченные приёмы не трогаем — сдвигаются только ожидающие.
     */
    suspend fun wakeUp(now: Long = System.currentTimeMillis()): Long = lock.withLock { wakeUpLocked(now) }

    /** Пересобрать ожидающие приёмы одной таблетки — после добавления или редактирования. */
    suspend fun refreshMedToday(medId: Long) = lock.withLock { refreshMedTodayLocked(medId) }

    /** «Выпил». Помечает приём и сдвигает все последующие приёмы этой таблетки на сегодня. */
    suspend fun markTaken(doseId: Long, now: Long = System.currentTimeMillis()) = lock.withLock { markTakenLocked(doseId, now) }

    /** «Пропустил». Следующий приём отсчитывается от планового времени пропущенного. */
    suspend fun markSkipped(doseId: Long, now: Long = System.currentTimeMillis()) = lock.withLock {
        val dose = doses.getById(doseId) ?: return@withLock
        if (dose.status != DoseStatus.PENDING) return@withLock
        doses.update(dose.copy(status = DoseStatus.SKIPPED, takenAt = now, remindAt = null))
        Notifications.dismiss(context, dose.id)
        shiftFollowing(dose, from = maxOf(dose.plannedAt, now))
        // Связанные таблетки — свои лекарства, привязанные лишь по времени: пропуск родителя их не отменяет.
        if (isSetStart(dose)) planLinkedChildren(dose, maxOf(dose.plannedAt, now))
        rescheduleAlarmsLocked()
        notifyDayDoneIfNeeded(dose.dayEpochDay)
    }

    /**
     * Зафиксировать внеплановый приём «по необходимости» — прямо сейчас, без будильников.
     * Возвращает id записи, чтобы экран мог предложить «Вернуть».
     */
    suspend fun takeNow(medId: Long, now: Long = System.currentTimeMillis()): Long? = lock.withLock {
        val med = meds.getById(medId) ?: return@withLock null
        // День — цикл, а не календарь: в 01:00 после вечернего подъёма приём должен попасть в текущий день.
        val day = cycleDay(now)
        val index = doses.getDay(day).count { it.medId == medId }
        val dose = Dose(
            medId = medId,
            dayEpochDay = day,
            indexInDay = index,
            plannedAt = now,
            status = DoseStatus.TAKEN,
            takenAt = now,
            amount = med.dosesPerIntake,
            medNameSnapshot = med.name,
        )
        val id = doses.insert(dose)
        decrementStock(medId, med.dosesPerIntake)
        // Родитель «по необходимости» тоже запускает связанные с ним таблетки.
        planLinkedChildren(dose.copy(id = id), now)
        // Отметка меняет ограничения («разносить с другими») и виджет — пересобираем.
        rescheduleAlarmsLocked()
        id
    }

    /** Убрать запись приёма «по необходимости» (снекбар «Вернуть» после «Выпил сейчас»). */
    suspend fun deleteIntake(doseId: Long) = lock.withLock {
        val dose = doses.getById(doseId) ?: return@withLock
        if (dose.status == DoseStatus.TAKEN) restoreStock(dose)
        doses.delete(dose)
        Notifications.dismiss(context, dose.id)
        rescheduleAlarmsLocked()
    }

    /**
     * Часовой пояс или время системы изменились: приёмы «по часам» держат абсолютное время,
     * поэтому их надо пересобрать от нового локального полудня.
     */
    suspend fun resyncAfterTimeChange() = lock.withLock {
        val days = fixedDays()
        for (med in meds.getActive().filter { it.byClock }) {
            dropPending(doses.pendingForMedOnDays(med.id, days))
        }
        rescheduleAlarmsLocked()
    }

    /**
     * «Ложусь спать»: день закрывается сразу, кнопка «Я проснулся» возвращается на главную.
     * Оставшиеся интервальные приёмы этого дня снимаются вместе с будильниками — экран говорит
     * «день закончен», и телефон не должен звонить вопреки ему. «По часам» живут своей жизнью.
     */
    suspend fun goToBed(now: Long = System.currentTimeMillis()) = lock.withLock {
        val cycle = wakes.latest() ?: return@withLock
        wakes.upsert(cycle.copy(bedAt = now))
        rescheduleAlarmsLocked()
    }

    /** «Я поел»: фиксируем время и подвигаем приёмы, которые нельзя пить сразу после еды. */
    suspend fun recordMeal(now: Long = System.currentTimeMillis()) = lock.withLock {
        // Историю еды не чистим: она видна в журнале, на схеме дня и уходит в бэкап.
        mealDao.insert(MealEvent(atMillis = now))
        rescheduleAlarmsLocked()
    }

    /** Завершить курс вручную (или удалить таблетку с главной): снять с планирования. */
    suspend fun finishCourse(medId: Long) = lock.withLock {
        val med = meds.getById(medId) ?: return@withLock
        deactivateLocked(med)
        rescheduleAlarmsLocked()
    }

    /** Отменить отметку — вернуть приём в ожидание (для истории/опечаток). */
    suspend fun undo(doseId: Long) = lock.withLock {
        val dose = doses.getById(doseId) ?: return@withLock
        // Возврат «выпитого» возвращает и списанный остаток.
        if (dose.status == DoseStatus.TAKEN) restoreStock(dose)
        val med = meds.getById(dose.medId)
        if (med?.asNeeded == true) {
            // У «по необходимости» нет расписания: ожидающий приём для неё — будильник ни о чём.
            doses.delete(dose)
        } else {
            doses.update(dose.copy(status = DoseStatus.PENDING, takenAt = null, remindAt = null, attempt = 0))
        }
        rescheduleAlarmsLocked()
    }

    /**
     * «Выпить всё, что пора»: отметить все ожидающие приёмы, чьё время наступило и которые не ждут
     * кнопку «Еда». Возвращает id отмеченных — для снекбара с отменой.
     */
    suspend fun takeAllDue(now: Long = System.currentTimeMillis()): List<Long> = lock.withLock {
        val day = cycleDay(now)
        val all = doses.getDay(day)
        val medsById = meds.getAllIncludingInactive().associateBy { it.id }
        val meals = mealDao.getAll().map { it.atMillis }
        val wakeAt = wakes.getDay(day)?.wakeAt
        val due = all.filter { dose ->
            val med = medsById[dose.medId]
            dose.status == DoseStatus.PENDING && dose.plannedAt <= now &&
                med != null && med.active && mealSatisfied(med, dose, all, meals, wakeAt)
        }
        due.forEach { markTakenLocked(it.id, now) }
        due.map { it.id }
    }

    /** Кнопка «Отложить» из шторки или полноэкранного будильника: момент запоминается в приёме. */
    suspend fun snooze(doseId: Long, minutes: Int, now: Long = System.currentTimeMillis()) = lock.withLock {
        val dose = doses.getById(doseId) ?: return@withLock
        if (dose.status != DoseStatus.PENDING) return@withLock
        val at = now + minutes * MINUTE_MS
        doses.update(dose.copy(remindAt = at, attempt = 0))
        AlarmScheduler(context).schedule(dose, at)
        Notifications.dismiss(context, doseId)
    }

    /**
     * Цепочка повторов из ReminderReceiver: следующий повтор записывается в приём, чтобы пересборка
     * будильников продолжила её, а не начала заново. `nextAt == null` — цепочка исчерпана.
     */
    suspend fun rememberRepeat(doseId: Long, nextAt: Long?, attempt: Int) = lock.withLock {
        val dose = doses.getById(doseId) ?: return@withLock
        if (dose.status != DoseStatus.PENDING) return@withLock
        doses.update(dose.copy(remindAt = nextAt, attempt = attempt))
        if (nextAt != null) AlarmScheduler(context).schedule(dose, nextAt, attempt)
    }

    /** Единая точка пересборки: будильники, виджет, напоминание «Подъём». */
    suspend fun rescheduleAlarms() = lock.withLock { rescheduleAlarmsLocked() }

    // ---------- Внутренние варианты под уже взятым замком ----------

    private suspend fun wakeUpLocked(now: Long): Long {
        val day = epochDayOf(now)
        // Ожидающие приёмы предыдущего цикла (он мог начаться вчера вечером) снимаем вместе с
        // будильниками: иначе они звонят дублями и при этом невидимы на главном экране.
        wakes.latest()?.takeIf { it.dayEpochDay != day }?.let { dropPending(doses.pendingOnDay(it.dayEpochDay)) }
        wakes.upsert(WakeEvent(dayEpochDay = day, wakeAt = now))
        // Новый день — про «всё выпито» можно будет сказать снова.
        Settings(context).dayDoneNotifiedFor = -1L
        dropPending(doses.pendingOnDay(day))

        // Дети без родителя (удалён или курс завершён) становятся обычными таблетками «от подъёма».
        for (child in meds.getActive().filter { it.linkedToMedId != null }) {
            val parent = meds.getById(child.linkedToMedId!!)
            if (parent == null || !parent.active) meds.update(child.copy(linkedToMedId = null))
        }

        // Снимок дня уже без ожидающих: остались только отмеченные приёмы.
        val dayDoses = doses.getDay(day).groupBy { it.medId }
        val takenIndexes = dayDoses.mapValues { (_, list) -> list.count { it.status != DoseStatus.PENDING } }
        val lastIndexes = dayDoses.mapValues { (_, list) -> list.maxOf { it.indexInDay } }

        val planned = mutableListOf<Dose>()
        val active = meds.getActive()
        for (med in active) {
            // Курс закончился — таблетка сама уходит в неактивные.
            if (med.isExpiredOn(day)) {
                deactivateLocked(med)
                continue
            }
            // Связанные планируются от приёма родителя, «по часам» не зависят от пробуждения.
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

        // Ручной сброс дня стёр ожидающие приёмы связанных таблеток: если родитель уже выпил
        // стартовый приём набора в этом цикле, дети планируются от него, а не ждут следующего.
        val todays = doses.getDay(day)
        for (parent in meds.getActive()) {
            val anchor = todays
                .filter { it.medId == parent.id && it.status != DoseStatus.PENDING && isSetStart(it, parent) }
                .filter { (it.takenAt ?: it.plannedAt) >= now - CYCLE_MAX_MS }
                .maxByOrNull { it.indexInDay } ?: continue
            planLinkedChildren(anchor, anchor.takenAt ?: anchor.plannedAt)
        }
        rescheduleAlarmsLocked()
        return now
    }

    private suspend fun refreshMedTodayLocked(medId: Long) {
        // День плавающий: цикл мог начаться вчера вечером; «по часам» живут по календарю.
        val day = cycleDay()
        val med = meds.getById(medId) ?: return
        dropPending(doses.pendingForMedOnDays(medId, (listOf(day, day + 1) + fixedDays()).distinct()))
        if (!med.active || med.byClock) {
            // Расписание по часам дособерёт syncFixedSchedule внутри пересборки.
            rescheduleAlarmsLocked()
            return
        }
        val wake = currentCycle() ?: run {
            rescheduleAlarmsLocked()
            return
        }
        if (med.asNeeded || med.isExpiredOn(day) || !isDueOn(med, day) || med.timesPerDay <= 0) {
            rescheduleAlarmsLocked()
            return
        }
        if (med.linkedToMedId != null) {
            // Якорь — последний отмеченный стартовый приём набора родителя в этом цикле, а не
            // обязательно первый за день: во втором цикле суток родитель начинает набор снова.
            val parent = meds.getById(med.linkedToMedId)
            val anchor = doses.getDay(day)
                .filter { it.medId == med.linkedToMedId && it.status != DoseStatus.PENDING && parent != null && isSetStart(it, parent) }
                .filter { (it.takenAt ?: it.plannedAt) >= wake.wakeAt }
                .maxByOrNull { it.indexInDay }
            if (anchor != null) planLinkedChildren(anchor, anchor.takenAt ?: anchor.plannedAt)
            rescheduleAlarmsLocked()
            return
        }
        val mine = doses.getDay(day).filter { it.medId == medId }
        val done = mine.filter { it.status != DoseStatus.PENDING }
        val alreadyTaken = done.size
        val takenInSet = alreadyTaken % med.timesPerDay
        val lastDone = done.maxByOrNull { it.indexInDay }
        val lastDoneAt = lastDone?.let { it.takenAt ?: it.plannedAt } ?: 0L
        // Набор уже закрыт в этом цикле — новый не открываем: иначе правка комментария рожала бы
        // фантомные просроченные приёмы «от подъёма».
        if (alreadyTaken > 0 && takenInSet == 0 && lastDoneAt >= wake.wakeAt) {
            rescheduleAlarmsLocked()
            return
        }
        val count = med.timesPerDay - takenInSet
        val startIndex = maxOf((mine.maxOfOrNull { it.indexInDay } ?: -1) + 1, alreadyTaken)
        // Внутри начатого набора отсчитываем от последнего фактического приёма, новый набор — от подъёма.
        val base = if (takenInSet > 0) lastDoneAt + med.intervalMinutes * MINUTE_MS else wake.wakeAt + med.firstDoseOffsetMinutes * MINUTE_MS
        doses.insertAll(
            (0 until count).map { i ->
                Dose(
                    medId = med.id,
                    dayEpochDay = day,
                    indexInDay = startIndex + i,
                    plannedAt = base + i * med.intervalMinutes * MINUTE_MS,
                    baseAt = base + i * med.intervalMinutes * MINUTE_MS,
                    amount = med.dosesPerIntake,
                    medNameSnapshot = med.name,
                )
            },
        )
        rescheduleAlarmsLocked()
    }

    private suspend fun markTakenLocked(doseId: Long, now: Long) {
        val dose = doses.getById(doseId) ?: return
        if (dose.status == DoseStatus.TAKEN) return
        doses.update(dose.copy(status = DoseStatus.TAKEN, takenAt = now, remindAt = null))
        // Уведомление о нём больше не актуально, откуда бы ни пришла отметка.
        Notifications.dismiss(context, dose.id)
        shiftFollowing(dose, from = now)
        // Родитель запускает связанные таблетки на КАЖДОМ первом приёме своего набора,
        // иначе во втором цикле тех же суток они не появятся.
        if (isSetStart(dose)) planLinkedChildren(dose, now)
        decrementStock(dose.medId, dose.amount)
        rescheduleAlarmsLocked()
        notifyDayDoneIfNeeded(dose.dayEpochDay)
    }

    /** Приём открывает набор родителя: от него отсчитываются связанные таблетки. */
    private suspend fun isSetStart(dose: Dose): Boolean {
        val med = meds.getById(dose.medId) ?: return false
        return isSetStart(dose, med)
    }

    private fun isSetStart(dose: Dose, med: Medication): Boolean =
        dose.indexInDay % med.timesPerDay.coerceAtLeast(1) == 0

    /**
     * Запланировать связанные таблетки от якорного приёма родителя. Уже отмеченные приёмы ребёнка
     * после якоря не планируются заново — иначе после ручного сброса дня появились бы дубли.
     */
    private suspend fun planLinkedChildren(parentDose: Dose, anchor: Long) {
        val day = parentDose.dayEpochDay
        val todays = doses.getDay(day)
        for (child in meds.childrenOf(parentDose.medId)) {
            if (child.asNeeded || !isDueOn(child, day) || child.timesPerDay <= 0) continue
            if (child.isExpiredOn(day)) {
                meds.deactivate(child.id)
                continue
            }
            val mine = todays.filter { it.medId == child.id }
            // Незакрытый набор уже стоит — второй раз не плодим.
            if (mine.any { it.status == DoseStatus.PENDING }) continue
            val done = mine.count { it.status != DoseStatus.PENDING && it.plannedAt >= anchor }
            val count = child.timesPerDay - done
            if (count <= 0) continue
            val startIndex = (mine.maxOfOrNull { it.indexInDay } ?: -1) + 1
            val first = anchor + child.linkedDelayMinutes * MINUTE_MS + done * child.intervalMinutes * MINUTE_MS
            doses.insertAll(
                (0 until count).map { k ->
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

    /**
     * Списать остаток и, если запас пересёк порог, напомнить о покупке. Ниже нуля не обрезаем:
     * иначе «Вернуть» вернул бы больше, чем списали. Отрицательный остаток экран показывает как 0.
     */
    private suspend fun decrementStock(medId: Long, amount: Double) {
        val med = meds.getById(medId) ?: return
        val stock = med.stockCount ?: return
        val newStock = stock - amount
        meds.update(med.copy(stockCount = newStock))
        val threshold = Settings(context).lowStockThreshold.toDouble()
        if (stock > threshold && newStock <= threshold) {
            Notifications.showLowStock(context, med.id, med.name, newStock.coerceAtLeast(0.0), med.form)
        }
    }

    private suspend fun restoreStock(dose: Dose) {
        meds.getById(dose.medId)?.let { med ->
            med.stockCount?.let { meds.update(med.copy(stockCount = it + dose.amount)) }
        }
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
            // План поменялся — отложенное напоминание и счёт повторов начинаются заново.
            d.copy(plannedAt = t, baseAt = t, remindAt = null, attempt = 0).also { t += med.intervalMinutes * MINUTE_MS }
        }
        doses.updateAll(updated)
    }

    /**
     * Снять таблетку с планирования: деактивировать, убрать её ожидающие приёмы (с будильниками)
     * и отвязать детей — они становятся обычными таблетками «от подъёма» и планируются сразу.
     */
    private suspend fun deactivateLocked(med: Medication) {
        meds.deactivate(med.id)
        val day = cycleDay()
        dropPending(doses.pendingForMedOnDays(med.id, (listOf(day, day + 1) + fixedDays()).distinct()))
        for (child in meds.childrenOf(med.id)) {
            meds.update(child.copy(linkedToMedId = null))
            refreshMedTodayLocked(child.id)
        }
    }

    /** Удалить ожидающие приёмы вместе с будильниками и уведомлениями: кнопка в шторке не должна бить по удалённому id. */
    private suspend fun dropPending(list: List<Dose>) {
        if (list.isEmpty()) return
        AlarmScheduler(context).cancelAll(list.map { it.id })
        list.forEach {
            Notifications.dismiss(context, it.id)
            doses.delete(it)
        }
    }

    /** Дни, на которые держится расписание «по часам»: сегодня и завтра по календарю. */
    private fun fixedDays(): List<Long> = listOf(today(), today() + 1)

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
            // План поменялся — отложенное напоминание и счёт повторов начинаются заново.
            if (at != dose.plannedAt) dose.copy(plannedAt = at, remindAt = null, attempt = 0) else null
        }
        if (moved.isNotEmpty()) doses.updateAll(moved)
    }

    /** Все запланированные приёмы дня отмечены — сообщаем, что день можно закрывать. «По необходимости» не в счёт. */
    private suspend fun notifyDayDoneIfNeeded(day: Long) {
        val settings = Settings(context)
        if (settings.dayDoneNotifiedFor == day) return
        val asNeeded = meds.getAllIncludingInactive().filter { it.asNeeded }.map { it.id }.toSet()
        val list = doses.getDay(day).filter { it.medId !in asNeeded }
        if (list.isEmpty() || list.any { it.status == DoseStatus.PENDING }) return
        settings.dayDoneNotifiedFor = day
        Notifications.showDayDone(context)
    }

    /**
     * Приёмы «по часам» не зависят от кнопки «я проснулся»: держим их в базе на сегодня
     * и на завтра, чтобы будильник стоял, даже если приложение не открывали.
     * Уже существующие приёмы (в том числе отмеченные) не трогаем. Истёкший курс деактивируется здесь же.
     */
    private suspend fun syncFixedSchedule(days: List<Long>) {
        val fixed = meds.getActive().filter { it.byClock && !it.asNeeded }
        if (fixed.isEmpty()) return
        for (day in days) {
            val existing = doses.getDay(day)
            val planned = mutableListOf<Dose>()
            val dayStart = LocalDate.ofEpochDay(day)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            for (med in fixed) {
                if (med.isExpiredOn(day)) {
                    meds.deactivate(med.id)
                    continue
                }
                if (!isDueOn(med, day)) continue
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

    private suspend fun rescheduleAlarmsLocked() {
        val scheduler = AlarmScheduler(context)
        val settings = Settings(context)
        val now = System.currentTimeMillis()
        val calendarDay = today()
        // «День» плавающий: приёмы могут относиться ко вчерашнему циклу, начатому вечером;
        // день последнего пробуждения нужен, чтобы снять и его устаревшие будильники.
        val day = cycleDay(now)
        val days = (listOf(day, calendarDay, calendarDay + 1) + listOfNotNull(wakes.latest()?.dayEpochDay)).distinct()
        // Сначала дособираем расписание «по часам», потом уже ставим будильники.
        syncFixedSchedule(fixedDays())
        applyIntakeConstraints(day)
        val relevant = days.flatMap { doses.getDay(it) }
        scheduler.cancelAll(relevant.map { it.id })

        val medsById = meds.getAllIncludingInactive().associateBy { it.id }
        val meals = mealDao.getAll().map { it.atMillis }
        val wakeByDay = days.associateWith { wakes.getDay(it) }
        val stale = mutableListOf<Dose>()
        for (dose in relevant.filter { it.status == DoseStatus.PENDING }) {
            val med = medsById[dose.medId]
            val wake = wakeByDay[dose.dayEpochDay]
            // Приёмы неактивных таблеток и интервальные приёмы дня, закрытого кнопкой «Сон», не звонят и удаляются.
            if (med == null || !med.active || (!med.byClock && wake?.bedAt != null)) {
                stale += dose
                continue
            }
            // Приём «после еды» без отметки «Еда» ждёт еду и не звонит; страховка — напомнить
            // всё же через MEAL_WAIT_MAX_MS, иначе забытая кнопка стоила бы пропущенного приёма.
            val gated = !mealSatisfied(med, dose, relevant, meals, wake?.wakeAt)
            val due = if (gated) dose.plannedAt + MEAL_WAIT_MAX_MS else dose.plannedAt
            // План ушёл в будущее («Еда» подвинула приём) — старое уведомление в шторке больше не правда.
            if (due > now) Notifications.dismiss(context, dose.id)
            val remindAt = dose.remindAt
            val at = when {
                due > now -> due
                // «Отложить» или цепочка повторов уже назначили момент — уважаем его.
                remindAt != null && remindAt > now -> remindAt
                // Цепочка повторов исчерпана — не начинаем заново при каждой пересборке.
                dose.attempt > 0 && (!settings.repeatEnabled || dose.attempt >= settings.repeatCount) -> continue
                // Приём просрочен давно (телефон был выключен) — молчим, чтобы не звонить
                // среди дня о пропущенном утреннем приёме; он остаётся в списке дня.
                now - due > OVERDUE_GRACE_MS -> continue
                // Приём просрочен (перезагрузка, смена времени) — не звоним сразу,
                // а подхватываем цепочку повторов через обычный интервал.
                settings.repeatEnabled -> now + settings.repeatIntervalMinutes * MINUTE_MS
                else -> continue
            }
            scheduler.schedule(dose, at, dose.attempt)
        }
        dropPending(stale)
        PillWidgetProvider.refresh(context)
        WakeReminder.schedule(context)
    }
}
