package tech.unispace.pillreminder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tech.unispace.pillreminder.data.TrackerType
import tech.unispace.pillreminder.data.Settings
import tech.unispace.pillreminder.data.MINUTE_MS
import tech.unispace.pillreminder.data.CYCLE_MAX_MS
import tech.unispace.pillreminder.alarm.TrackerAlarms
import tech.unispace.pillreminder.alarm.VisitAlarms
import tech.unispace.pillreminder.alarm.WakeReminder
import tech.unispace.pillreminder.container
import tech.unispace.pillreminder.data.Backup
import tech.unispace.pillreminder.data.Dose
import tech.unispace.pillreminder.data.DoseStatus
import tech.unispace.pillreminder.data.Medication
import tech.unispace.pillreminder.data.DoctorVisit
import tech.unispace.pillreminder.data.MedLibraryEntry
import tech.unispace.pillreminder.data.Note
import tech.unispace.pillreminder.data.Report
import tech.unispace.pillreminder.data.Tracker
import tech.unispace.pillreminder.data.TrackerEntry
import tech.unispace.pillreminder.data.WakeEvent
import tech.unispace.pillreminder.data.epochDayOf
import tech.unispace.pillreminder.data.today
import java.time.LocalDate

data class MedRow(
    val med: Medication,
    val dueToday: Boolean,
    val nextDose: Dose?,
    val takenToday: Int,
    val totalToday: Int,
    val linkedParentName: String? = null,
)

data class HomeState(
    val now: Long = System.currentTimeMillis(),
    val wokeUpAt: Long? = null,
    /** Все приёмы дня отмечены — можно начинать новый день. */
    val allDone: Boolean = false,
    val rows: List<MedRow> = emptyList(),
    val loaded: Boolean = false,
)

/** Серия дней без пропусков и дисциплина по каждой таблетке. */
data class AdherenceState(
    val streak: Int = 0,
    /** Название таблетки → процент вовремя отмеченных приёмов. */
    val perMed: List<Pair<String, Int>> = emptyList(),
)

/** День журнала: приёмы + время подъёма. */
data class JournalState(
    val day: Long = today(),
    val doses: List<Dose> = emptyList(),
    val wakeAt: Long? = null,
    /** Когда легли спать в этот день; null — кнопку не нажимали. */
    val bedAt: Long? = null,
    /** Время приёмов пищи за этот день. */
    val meals: List<Long> = emptyList(),
)

/** Сводка одного дня для тепловой карты. */
data class DayHeat(val taken: Int, val planned: Int)

/** Трекер и все его записи (новые первыми). */
data class TrackerRow(
    val tracker: Tracker,
    val entries: List<TrackerEntry>,
)

data class HeatmapState(
    /** Первое число показанного месяца. */
    val monthStart: LocalDate = LocalDate.now().withDayOfMonth(1),
    val days: Map<Long, DayHeat> = emptyMap(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val planner = app.container.planner
    private val db = app.container.db

    /** Для расширений в ui-слое (серии корреляций). */
    fun dbAccess() = db

    /** Тикер: обновляет обратный отсчёт на экране раз в секунду. */
    private val ticker: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    private val calendarDay: Flow<Long> = ticker.map { today() }.distinctUntilChanged()

    /**
     * Текущий незакрытый цикл: «день» держится на последнем пробуждении и живёт до
     * [CYCLE_MAX_MS], а не до полуночи — график сна у человека плавающий.
     */
    private val cycleFlow: Flow<WakeEvent?> =
        combine(db.wakeDao().observeLatest(), ticker) { wake, now ->
            wake?.takeIf { it.bedAt == null && now - it.wakeAt in 0 until CYCLE_MAX_MS }
        }.distinctUntilChanged()

    private val dayFlow: Flow<Long> =
        combine(cycleFlow, calendarDay) { cycle, day -> cycle?.dayEpochDay ?: day }.distinctUntilChanged()

    init {
        // Ð¡Ð¼ÐµÐ½Ð° ÑÑÑÐ¾Ðº Ð¿ÑÐ¸ Ð¾ÑÐºÑÑÑÐ¾Ð¼ Ð¿ÑÐ¸Ð»Ð¾Ð¶ÐµÐ½Ð¸Ð¸: Ð´Ð¾ÑÐ¾Ð·Ð´Ð°ÑÑ Ð¿ÑÐ¸ÑÐ¼Ñ Â«Ð¿Ð¾ ÑÐ°ÑÐ°Ð¼Â» Ð¸ Ð¿ÐµÑÐµÑÑÐ°Ð²Ð¸ÑÑ Ð±ÑÐ´Ð¸Ð»ÑÐ½Ð¸ÐºÐ¸.
        viewModelScope.launch { dayFlow.collect { planner.rescheduleAlarms() } }
    }

    val home: StateFlow<HomeState> =
        combine(cycleFlow, calendarDay) { cycle, day -> cycle to (cycle?.dayEpochDay ?: day) }
            .distinctUntilChanged()
            .flatMapLatest { (cycle, day) ->
            combine(
                db.medicationDao().observeActive(),
                db.doseDao().observeDay(day),
                ticker,
            ) { meds, doses, now ->
                val byMed = doses.groupBy { it.medId }
                val nameById = meds.associate { it.id to it.name }
                val rows = meds.map { med ->
                    val list = byMed[med.id].orEmpty()
                    MedRow(
                        med = med,
                        dueToday = planner.isDueOn(med, day),
                        nextDose = list.filter { it.status == DoseStatus.PENDING }.minByOrNull { it.plannedAt },
                        // Считаем внутри текущего набора: во втором цикле суток «4 из 3» выглядело бы дико.
                        takenToday = list.count { it.status == DoseStatus.TAKEN } % med.timesPerDay.coerceAtLeast(1),
                        totalToday = med.timesPerDay.coerceAtLeast(1),
                        linkedParentName = med.linkedToMedId?.let { nameById[it] },
                    )
                }
                HomeState(
                    now = now,
                    wokeUpAt = cycle?.wakeAt,
                    allDone = doses.isNotEmpty() && doses.none { it.status == DoseStatus.PENDING },
                    rows = rows,
                    loaded = true,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    // ---------- Статистика ----------

    val selectedDay = MutableStateFlow(today())

    val journal: StateFlow<JournalState> = selectedDay.flatMapLatest { day ->
        combine(
            db.doseDao().observeDay(day),
            db.wakeDao().observeDay(day),
            db.mealDao().observeLast(),
        ) { doses, wake, _ ->
            // Еда хранится отдельной таблицей — берём приёмы пищи этого дня.
            val meals = db.mealDao().getAll()
                .map { it.atMillis }
                .filter { epochDayOf(it) == day }
                .sorted()
            JournalState(
                day = day,
                doses = doses.sortedBy { it.plannedAt },
                wakeAt = wake?.wakeAt,
                bedAt = wake?.bedAt,
                meals = meals,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalState())

    val heatMonthStart = MutableStateFlow(LocalDate.now().withDayOfMonth(1))

    val heatmap: StateFlow<HeatmapState> = heatMonthStart.flatMapLatest { start ->
        val from = start.toEpochDay()
        val to = start.plusMonths(1).minusDays(1).toEpochDay()
        db.doseDao().observeBetween(from, to).map { doses ->
            val days = doses.groupBy { it.dayEpochDay }.mapValues { (_, list) ->
                DayHeat(
                    taken = list.count { it.status == DoseStatus.TAKEN },
                    planned = list.size,
                )
            }
            HeatmapState(monthStart = start, days = days)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HeatmapState())

    // ---------- Заметки ----------

    val notes: StateFlow<List<Note>> = db.noteDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun loadNote(id: Long): Note? = db.noteDao().getById(id)

    fun saveNote(note: Note, onDone: () -> Unit = {}) = viewModelScope.launch {
        db.noteDao().upsert(note)
        onDone()
    }

    fun deleteNote(id: Long, onDone: () -> Unit = {}) = viewModelScope.launch {
        db.noteDao().delete(id)
        onDone()
    }

    // ---------- Визиты к врачу ----------

    val visits: StateFlow<List<DoctorVisit>> = db.visitDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun loadVisit(id: Long): DoctorVisit? = db.visitDao().getById(id)

    fun saveVisit(visit: DoctorVisit, onDone: () -> Unit = {}) = viewModelScope.launch {
        db.visitDao().upsert(visit)
        VisitAlarms.reschedule(getApplication(), db)
        onDone()
    }

    fun deleteVisit(id: Long, onDone: () -> Unit = {}) = viewModelScope.launch {
        db.visitDao().delete(id)
        VisitAlarms.reschedule(getApplication(), db)
        onDone()
    }

    fun rescheduleVisitAlarms() = viewModelScope.launch {
        VisitAlarms.reschedule(getApplication(), db)
    }

    fun rescheduleWakeReminder() = viewModelScope.launch {
        WakeReminder.schedule(getApplication())
    }

    /** Активные таблетки — для выбора «после какой» в мастере. */
    suspend fun activeMeds(): List<Medication> = db.medicationDao().getActive()

    // ---------- Каталог лекарств ----------

    val library: StateFlow<List<MedLibraryEntry>> = db.libraryDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun libraryEntries(): List<MedLibraryEntry> = db.libraryDao().getAll()

    suspend fun loadLibraryEntry(id: Long): MedLibraryEntry? = db.libraryDao().getById(id)

    fun saveLibraryEntry(entry: MedLibraryEntry, onDone: () -> Unit = {}) = viewModelScope.launch {
        db.libraryDao().upsert(entry)
        onDone()
    }

    fun deleteLibraryEntry(id: Long, onDone: () -> Unit = {}) = viewModelScope.launch {
        db.libraryDao().delete(id)
        onDone()
    }

    // ---------- Трекеры ----------

    val trackerRows: StateFlow<List<TrackerRow>> = combine(
        db.trackerDao().observeAll(),
        db.trackerDao().observeEntries(),
    ) { trackers, entries ->
        trackers.map { tracker ->
            TrackerRow(
                tracker = tracker,
                entries = entries.filter { it.trackerId == tracker.id },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun loadTracker(id: Long): Tracker? = db.trackerDao().getById(id)

    fun saveTracker(tracker: Tracker, onDone: (Long) -> Unit = {}) = viewModelScope.launch {
        val id = db.trackerDao().upsert(tracker)
        TrackerAlarms.reschedule(getApplication(), db)
        onDone(if (tracker.id == 0L) id else tracker.id)
    }

    fun deleteTracker(id: Long, onDone: () -> Unit = {}) = viewModelScope.launch {
        db.trackerDao().deleteEntriesOf(id)
        db.trackerDao().delete(id)
        TrackerAlarms.reschedule(getApplication(), db)
        onDone()
    }

    fun addTrackerEntry(entry: TrackerEntry, onDone: () -> Unit = {}) = viewModelScope.launch {
        db.trackerDao().upsertEntry(entry)
        TrackerAlarms.reschedule(getApplication(), db)
        onDone()
    }

    fun deleteTrackerEntry(id: Long) = viewModelScope.launch {
        db.trackerDao().deleteEntry(id)
    }

    // ---------- Бэкап и отчёт ----------

    suspend fun exportJson(): String = Backup.exportJson(db)

    suspend fun exportDosesCsv(): String = Backup.dosesCsv(db)

    suspend fun exportTrackersCsv(): String = Backup.trackersCsv(db)

    /** Восстановление из JSON: стирает всё и пересобирает будильники. */
    fun importBackup(json: String, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = try {
            Backup.importJson(db, json)
            true
        } catch (_: Exception) {
            false
        }
        if (ok) {
            planner.rescheduleAlarms()
            VisitAlarms.reschedule(getApplication(), db)
            TrackerAlarms.reschedule(getApplication(), db)
        }
        onDone(ok)
    }

    suspend fun buildReport(days: Int, sections: Set<String>): String = Report.build(db, days, Lang.s, sections)

    // ---------- Действия ----------

    /** Запись сна, собранная кнопками: её надо предложить оценить. */
    val sleepToRate = MutableStateFlow<TrackerEntry?>(null)

    /**
     * Дисциплина за последние [days] дней и текущая серия дней без пропусков.
     * День считается «чистым», если в нём были приёмы и все они отмечены как выпитые.
     */
    suspend fun adherence(days: Int = 30): AdherenceState {
        val to = today()
        val from = to - days + 1
        val all = db.doseDao().getAll().filter { it.dayEpochDay in from..to }
        val byDay = all.groupBy { it.dayEpochDay }

        var streak = 0
        var day = to
        while (day >= from) {
            val list = byDay[day].orEmpty()
            // Пустой день (ничего не назначено) серию не рвёт и не удлиняет.
            if (list.isNotEmpty()) {
                if (list.all { it.status == DoseStatus.TAKEN }) streak++ else break
            } else if (day != to) {
                break
            }
            day--
        }

        val perMed = all.groupBy { it.medNameSnapshot }
            .mapNotNull { (name, list) ->
                val counted = list.filter { it.status != DoseStatus.PENDING || it.plannedAt < System.currentTimeMillis() }
                if (counted.isEmpty()) return@mapNotNull null
                name to (counted.count { it.status == DoseStatus.TAKEN } * 100 / counted.size)
            }
            .sortedBy { it.second }

        return AdherenceState(streak = streak, perMed = perMed)
    }

    fun wakeUp() = viewModelScope.launch {
        val now = System.currentTimeMillis()
        planner.wakeUp(now)
        logSleepIfPending(now)
    }

    /** «Ложусь спать»: запоминаем момент, сама запись появится при пробуждении. */
    fun goToBed(now: Long = System.currentTimeMillis()) = viewModelScope.launch {
        // Момент нужен и трекеру сна (при пробуждении), и истории дня.
        Settings(getApplication()).pendingSleepStart = now
        planner.goToBed(now)
    }

    /** «Поел»: фиксируем еду и двигаем приёмы, которые нельзя пить сразу после неё. */
    fun recordMeal() = viewModelScope.launch { planner.recordMeal() }

    /** Оценка сна и пробуждения для записи, собранной кнопками. */
    fun rateSleep(entry: TrackerEntry, sleep: Int, wake: Int) = viewModelScope.launch {
        db.trackerDao().upsertEntry(
            entry.copy(
                value = sleep.toDouble(),
                wakeValue = if (wake > 0) wake.toDouble() else null,
            ),
        )
        sleepToRate.value = null
    }

    fun dismissSleepRating() {
        sleepToRate.value = null
    }

    /**
     * Пробуждение после нажатой кнопки «Ложусь спать» — создаём запись сна и просим оценить.
     * Слишком короткий промежуток (меньше часа) считаем ошибкой нажатия, а не сном.
     */
    private suspend fun logSleepIfPending(now: Long) {
        val settings = Settings(getApplication())
        val start = settings.pendingSleepStart
        settings.pendingSleepStart = 0L
        if (!settings.askSleepOnWake) return
        val tracker = db.trackerDao().getAll().firstOrNull { it.type == TrackerType.SLEEP } ?: return
        // Спрашиваем при каждом пробуждении: без кнопки «Ложусь спать» просто не знаем,
        // когда человек лёг, — запись будет только с оценками.
        val known = start > 0L && now - start >= 60 * MINUTE_MS
        val entry = TrackerEntry(
            trackerId = tracker.id,
            atMillis = now,
            value = 0.0,
            sleepStart = if (known) start else null,
            sleepEnd = if (known) now else null,
            auto = true,
        )
        val id = db.trackerDao().upsertEntry(entry)
        sleepToRate.value = entry.copy(id = id)
    }

    /** Ночи из истории («лёг» + «проснулся»), которых ещё нет в трекере сна. */
    suspend fun sleepHistoryCandidates(): List<Pair<Long, Long>> {
        val tracker = db.trackerDao().getAll().firstOrNull { it.type == TrackerType.SLEEP }
        val existing = tracker?.let { t ->
            db.trackerDao().getAllEntries().filter { it.trackerId == t.id }.mapNotNull { it.sleepStart }.toSet()
        }.orEmpty()
        return db.wakeDao().getAll()
            .mapNotNull { w -> w.bedAt?.let { bed -> bed to w.wakeAt } }
            .filter { (bed, wake) -> wake > bed && wake - bed >= 60 * MINUTE_MS && bed !in existing }
            .sortedBy { it.first }
    }

    /** Перенести ночи из истории в трекер сна — без оценок, их можно проставить позже. */
    fun importSleepHistory(trackerId: Long, nights: List<Pair<Long, Long>>) = viewModelScope.launch {
        nights.forEach { (bed, wake) ->
            db.trackerDao().upsertEntry(
                TrackerEntry(
                    trackerId = trackerId,
                    atMillis = wake,
                    value = 0.0,
                    sleepStart = bed,
                    sleepEnd = wake,
                    auto = true,
                ),
            )
        }
    }


    fun take(doseId: Long) = viewModelScope.launch { planner.markTaken(doseId) }

    fun skip(doseId: Long) = viewModelScope.launch { planner.markSkipped(doseId) }

    fun undo(doseId: Long) = viewModelScope.launch { planner.undo(doseId) }

    fun takeNow(medId: Long) = viewModelScope.launch { planner.takeNow(medId) }

    fun takeAllDue() = viewModelScope.launch { planner.takeAllDue() }

    /** Сохранить новый порядок таблеток после перетаскивания. */
    fun saveMedOrder(orderedIds: List<Long>) = viewModelScope.launch {
        orderedIds.forEachIndexed { index, id ->
            db.medicationDao().setSortOrder(id, index)
        }
    }

    /** Копия таблетки: у людей часто 2–3 препарата по одной схеме. */
    fun duplicateMed(medId: Long, onDone: (Long) -> Unit = {}) = viewModelScope.launch {
        val med = db.medicationDao().getById(medId) ?: return@launch
        val order = db.medicationDao().getActive().maxOfOrNull { it.sortOrder } ?: 0
        val id = db.medicationDao().insert(
            med.copy(
                id = 0,
                name = med.name + " (" + Lang.s.copySuffix + ")",
                sortOrder = order + 1,
                stockCount = null,
            ),
        )
        planner.refreshMedToday(id)
        onDone(id)
    }

    fun finishCourse(medId: Long, onDone: () -> Unit = {}) = viewModelScope.launch {
        planner.finishCourse(medId)
        onDone()
    }

    suspend fun load(medId: Long): Medication? = db.medicationDao().getById(medId)

    fun save(med: Medication, onDone: (Long) -> Unit = {}) = viewModelScope.launch {
        val groupId = if (med.groupId > 0) med.groupId else planner.ensureDefaultGroup()
        val toSave = med.copy(groupId = groupId)
        val id = if (toSave.id == 0L) {
            db.medicationDao().insert(toSave)
        } else {
            db.medicationDao().update(toSave)
            toSave.id
        }
        planner.refreshMedToday(id)
        // Новая таблетка сразу попадает в каталог, чтобы не заводить её там руками.
        if (toSave.id == 0L && db.libraryDao().getAll().none { it.name.equals(toSave.name, ignoreCase = true) }) {
            db.libraryDao().upsert(
                MedLibraryEntry(
                    name = toSave.name,
                    form = toSave.form,
                    doseInfo = toSave.doseInfo,
                    startEpochDay = toSave.cycleStartEpochDay,
                    endEpochDay = if (toSave.durationDays > 0) toSave.cycleStartEpochDay + toSave.durationDays else null,
                ),
            )
        }
        onDone(id)
    }

    fun delete(medId: Long, onDone: () -> Unit = {}) = viewModelScope.launch {
        db.medicationDao().deactivate(medId)
        planner.refreshMedToday(medId)
        onDone()
    }
}
