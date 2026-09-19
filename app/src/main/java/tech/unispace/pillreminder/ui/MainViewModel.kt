package tech.unispace.pillreminder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import tech.unispace.pillreminder.data.BackupTooNewException
import tech.unispace.pillreminder.data.NotABackupException
import tech.unispace.pillreminder.data.Dose
import tech.unispace.pillreminder.data.DoseStatus
import tech.unispace.pillreminder.data.Medication
import tech.unispace.pillreminder.data.DoctorVisit
import tech.unispace.pillreminder.data.DoctorPreset
import tech.unispace.pillreminder.data.MedLibraryEntry
import tech.unispace.pillreminder.data.MealEvent
import tech.unispace.pillreminder.data.Note
import tech.unispace.pillreminder.data.Report
import tech.unispace.pillreminder.data.Tracker
import tech.unispace.pillreminder.data.TrackerEntry
import tech.unispace.pillreminder.data.WakeEvent
import tech.unispace.pillreminder.data.courseEndDay
import tech.unispace.pillreminder.data.OVERDUE_GRACE_MS
import tech.unispace.pillreminder.data.isMissed
import tech.unispace.pillreminder.data.isTakeAllCandidate
import tech.unispace.pillreminder.data.snoozedUntil
import tech.unispace.pillreminder.data.byClock
import tech.unispace.pillreminder.data.epochDayOf
import tech.unispace.pillreminder.data.isActive
import tech.unispace.pillreminder.data.mealSatisfied
import tech.unispace.pillreminder.data.today
import java.time.LocalDate

data class MedRow(
    val med: Medication,
    val dueToday: Boolean,
    val nextDose: Dose?,
    /** Выпито в текущем наборе (для «по необходимости» — за день). */
    val takenToday: Int,
    val totalToday: Int,
    /** Пропущено в текущем наборе — чтобы закрытый набор с пропуском не звался «всё выпито». */
    val skippedToday: Int = 0,
    /** Номер ближайшего приёма внутри набора, с 1; во втором цикле суток не «4 из 3». */
    val nextIndexInSet: Int = 0,
    val linkedParentName: String? = null,
    /** Ближайший приём «после еды» ждёт кнопку «Еда»: будильник не звонит, карточка не краснеет. */
    val waitsMeal: Boolean = false,
    /** Ближайший приём отложен кнопкой «Отложить» до этого момента; карточка пишет «отложено до …» и не краснеет. */
    val snoozedUntil: Long? = null,
    /** Статусы приёмов текущего набора по порядку (null — приём ещё не создан) — для точек прогресса. */
    val setStatuses: List<DoseStatus?> = emptyList(),
    /** Приёмы, просроченные больше двух часов: они не «следующие», их разбирают отдельной строкой. */
    val missed: List<Dose> = emptyList(),
)

data class HomeState(
    /** Сколько приёмов отметит кнопка «Выпить всё, что пора» — считается тем же правилом, что и действие. */
    val dueNowCount: Int = 0,
    val now: Long = System.currentTimeMillis(),
    val wokeUpAt: Long? = null,
    /** Когда нажали «Сон» в этом цикле; null — день идёт. */
    val bedAt: Long? = null,
    /** Все запланированные приёмы дня отмечены («по необходимости» не в счёт) — можно начинать новый день. */
    val allDone: Boolean = false,
    val rows: List<MedRow> = emptyList(),
    val loaded: Boolean = false,
    /** Приёмы дня для схемы: цикл плюс сегодняшние «по часам», если календарный день другой. */
    val doses: List<Dose> = emptyList(),
    /** Отметки «Еда» — все известные, по времени. */
    val meals: List<Long> = emptyList(),
)

/**
 * Текущий набор приёмов таблетки: срез по номерам с начала последнего набора. Planner нумерует приёмы
 * сквозь наборы (0..N-1, N..2N-1 во втором цикле суток), поэтому «сколько выпито» считается только
 * в последнем срезе, а не по модулю всех выпитых — иначе после пропуска счётчик ехал.
 */
fun currentSet(doses: List<Dose>, size: Int): List<Dose> {
    val n = size.coerceAtLeast(1)
    val maxIndex = doses.maxOfOrNull { it.indexInDay } ?: return emptyList()
    val start = maxIndex / n * n
    return doses.filter { it.indexInDay >= start }
}

/**
 * Статусы текущего набора по позициям 0..size-1 (null — приёма с таким номером ещё нет): точки прогресса
 * на карточке рисуются по нему, а не по счётчикам «выпито/пропущено» — иначе порядок точек не совпадал бы с порядком приёмов.
 */
fun setStatuses(set: List<Dose>, size: Int): List<DoseStatus?> {
    val n = size.coerceAtLeast(1)
    val start = set.minOfOrNull { it.indexInDay }?.let { it / n * n } ?: 0
    return (0 until n).map { i -> set.firstOrNull { it.indexInDay - start == i }?.status }
}

/**
 * Серия дней без пропусков, считая назад от [to]. День без назначений серию не рвёт и не удлиняет;
 * день, где приёмы ещё впереди (в том числе сегодня), тоже — иначе серия обнулялась бы каждое утро.
 * Любой пропуск или просроченный приём серию обрывает.
 */
fun streakDays(doses: List<Dose>, from: Long, to: Long, now: Long): Int {
    val byDay = doses.groupBy { it.dayEpochDay }
    var streak = 0
    var day = to
    while (day >= from) {
        val list = byDay[day].orEmpty()
        val decided = list.filter { it.status != DoseStatus.PENDING || it.plannedAt < now }
        val stillOpen = list.any { it.status == DoseStatus.PENDING && it.plannedAt >= now }
        when {
            list.isEmpty() -> Unit
            decided.any { it.status != DoseStatus.TAKEN } -> return streak
            stillOpen -> Unit
            else -> streak++
        }
        day--
    }
    return streak
}

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
    /** Форма выпуска по id таблетки — чтобы журнал писал «3 капли», а не «3 таблетки». */
    val formById: Map<Long, String> = emptyMap(),
    /** Дозировка по id таблетки — для коротких подписей схемы дня («Эсц 10мг»). */
    val doseInfoById: Map<Long, String> = emptyMap(),
    /** День текущего цикла: «прошлый день» считается по нему, а не по календарю — цикл живёт и после полуночи. */
    val cycleDay: Long = today(),
)

/** Точка приёма под числом в календаре: выпит, пропущен или просрочен, ещё впереди. */
enum class HeatMark { TAKEN, MISSED, PENDING }

/**
 * Сводка одного дня для тепловой карты: [taken]/[planned] — по приёмам, чьё время уже прошло (заливка),
 * [pending] — ещё впереди, [marks] — все приёмы дня по порядку для точек под числом.
 */
data class DayHeat(val taken: Int, val planned: Int, val pending: Int = 0, val marks: List<HeatMark> = emptyList())

/**
 * Точки календаря по приёмам дня. Ожидающий приём становится пропуском не сразу, а через [graceMs]:
 * карточка и схема дня краснеют по тому же правилу, а раньше клетка «сегодня» краснела через минуту
 * после планового времени. Чистая, с тестом.
 */
fun heatMarks(doses: List<Dose>, now: Long, graceMs: Long = OVERDUE_GRACE_MS): List<HeatMark> =
    doses.sortedBy { it.plannedAt }.map { d ->
        when {
            d.status == DoseStatus.TAKEN -> HeatMark.TAKEN
            d.status == DoseStatus.SKIPPED || now - d.plannedAt > graceMs -> HeatMark.MISSED
            else -> HeatMark.PENDING
        }
    }

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

/** Итог восстановления из бэкапа — экран показывает разные сообщения. */
enum class ImportOutcome { OK, TOO_NEW, NOT_BACKUP, FAILED }

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    /**
     * Мутации и чтения базы — не на главном потоке. Планировщик ставит будильники через AlarmManager
     * (IPC на каждый приём) и обновляет виджет; на Main это отдавалось задержкой после «Подъёма».
     */
    private val io = Dispatchers.Default

    /**
     * Колбэк экрана — всегда на главном потоке. Сама работа идёт в фоне, но `onDone` обычно трогает
     * навигацию, а NavController не потокобезопасен.
     */
    private suspend fun ui(block: () -> Unit) = withContext(Dispatchers.Main) { block() }

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
        combine(db.wakeDao().observeLatest(), ticker) { wake, now -> wake?.takeIf { it.isActive(now) } }
            .distinctUntilChanged()

    private val dayFlow: Flow<Long> =
        combine(cycleFlow, calendarDay) { cycle, day -> cycle?.dayEpochDay ?: day }.distinctUntilChanged()

    /** День, который журнал выбрал сам (следует за циклом); ручной выбор пользователя его не трогает. */
    private var autoJournalDay = today()

    init {
        // Смена суток при открытом приложении: досоздать приёмы «по часам» и переставить будильники.
        viewModelScope.launch(io) {
            dayFlow.collect { day ->
                planner.rescheduleAlarms()
                // Журнал открывается на дне цикла (он мог начаться вчера вечером), пока пользователь сам не выбрал другой.
                if (selectedDay.value == autoJournalDay) selectedDay.value = day
                autoJournalDay = day
            }
        }
    }

    val home: StateFlow<HomeState> =
        combine(cycleFlow, calendarDay) { cycle, cal -> Triple(cycle, cycle?.dayEpochDay ?: cal, cal) }
            .distinctUntilChanged()
            .flatMapLatest { (cycle, day, cal) ->
            combine(
                db.medicationDao().observeActive(),
                // Цикл мог начаться вчера: приёмы цикла лежат под вчерашним днём, а «по часам» — под сегодняшним.
                db.doseDao().observeBetween(minOf(day, cal), maxOf(day, cal)),
                db.mealDao().observeAllTimes(),
                // Подъём именно этого дня, а не «текущий цикл»: после «Сон» цикла нет, а карточка и будильник
                // должны судить об ожидании еды одинаково (Planner берёт wakes.getDay).
                db.wakeDao().observeDay(day),
                ticker,
            ) { meds, allDoses, meals, wake, now ->
                val cycleDoses = allDoses.filter { it.dayEpochDay == day }
                val clockDoses = if (day == cal) cycleDoses else allDoses.filter { it.dayEpochDay == cal }
                val nameById = meds.associate { it.id to it.name }
                val rows = meds.map { med ->
                    val list = (if (med.byClock) clockDoses else cycleDoses).filter { it.medId == med.id }
                    val pending = list.filter { it.status == DoseStatus.PENDING }
                    // Давно просроченные приёмы не становятся «следующим»: иначе карточка весь день
                    // показывает утреннее время, а про наступивший дневной приём не говорит ничего.
                    val missed = pending.filter { isMissed(it, now) }
                    val next = (pending - missed.toSet()).minByOrNull { it.plannedAt } ?: missed.minByOrNull { it.plannedAt }
                    val size = med.timesPerDay.coerceAtLeast(1)
                    val set = currentSet(list, size)
                    MedRow(
                        med = med,
                        dueToday = planner.isDueOn(med, day),
                        nextDose = next,
                        // «По необходимости» набора не имеет: считаем всё, что принято за день.
                        takenToday = if (med.asNeeded) list.count { it.status == DoseStatus.TAKEN } else set.count { it.status == DoseStatus.TAKEN },
                        totalToday = size,
                        skippedToday = set.count { it.status == DoseStatus.SKIPPED },
                        nextIndexInSet = next?.let { it.indexInDay % size + 1 } ?: 0,
                        // Родитель мог быть удалён — тогда честно пишем «удалённая таблетка», а не «всё выпито».
                        linkedParentName = med.linkedToMedId?.let { nameById[it] ?: Lang.s.deletedPill },
                        // То же правило, что ставит будильник в Planner: карточка и звонок не должны расходиться.
                        waitsMeal = next != null && !mealSatisfied(med, next, list, meals, wake?.wakeAt),
                        snoozedUntil = next?.snoozedUntil(now),
                        setStatuses = if (med.asNeeded) emptyList() else setStatuses(set, size),
                        missed = if (next != null && next in missed) missed - next else missed,
                    )
                }
                val medsById = meds.associateBy { it.id }
                val shown = (cycleDoses + clockDoses.filter { medsById[it.medId]?.byClock == true }).distinctBy { it.id }
                // «День закрыт» — только про запланированные приёмы: разовый приём «по необходимости» дня не закрывает.
                val scheduled = shown.filter { medsById[it.medId]?.asNeeded != true }
                HomeState(
                    dueNowCount = shown.count { d ->
                        val m = medsById[d.medId]
                        isTakeAllCandidate(d, now) && m != null && m.active &&
                            mealSatisfied(m, d, shown, meals, wake?.wakeAt)
                    },
                    now = now,
                    wokeUpAt = cycle?.wakeAt,
                    bedAt = cycle?.bedAt,
                    allDone = scheduled.isNotEmpty() && scheduled.none { it.status == DoseStatus.PENDING },
                    rows = rows,
                    loaded = true,
                    doses = shown,
                    meals = meals,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    // ---------- Статистика ----------

    /** Название только что сохранённой таблетки — главный экран показывает снекбар «Сохранено». */
    val savedMedName = MutableStateFlow<String?>(null)

    val selectedDay = MutableStateFlow(today())

    val journal: StateFlow<JournalState> = selectedDay.flatMapLatest { day ->
        combine(
            db.doseDao().observeDay(day),
            db.wakeDao().observeDay(day),
            db.mealDao().observeAllTimes(),
            dayFlow,
        ) { doses, wake, allMeals, cycleDay ->
            // Еда — по окну цикла (подъём … отбой или +18 ч), а без отметки подъёма — по календарю:
            // «Еда» в 00:40 относится к дню, который начался вчера вечером.
            val meals = if (wake != null) {
                val end = wake.bedAt ?: (wake.wakeAt + CYCLE_MAX_MS)
                allMeals.filter { it in wake.wakeAt..end }
            } else {
                allMeals.filter { epochDayOf(it) == day }
            }
            val allMeds = db.medicationDao().getAllIncludingInactive()
            JournalState(
                day = day,
                doses = doses.sortedBy { it.plannedAt },
                wakeAt = wake?.wakeAt,
                bedAt = wake?.bedAt,
                meals = meals.sorted(),
                formById = allMeds.associate { it.id to it.form },
                doseInfoById = allMeds.associate { it.id to it.doseInfo },
                cycleDay = cycleDay,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalState())

    val heatMonthStart = MutableStateFlow(LocalDate.now().withDayOfMonth(1))

    val heatmap: StateFlow<HeatmapState> = heatMonthStart.flatMapLatest { start ->
        val from = start.toEpochDay()
        val to = start.plusMonths(1).minusDays(1).toEpochDay()
        db.doseDao().observeBetween(from, to).map { doses ->
            // Сегодняшние приёмы, время которых ещё не пришло, — не пропуски: утром день не должен краснеть.
            val now = System.currentTimeMillis()
            val days = doses.groupBy { it.dayEpochDay }.mapValues { (_, list) ->
                // Ожидающий приём попадает в знаменатель только когда просрочен по-настоящему:
                // иначе день краснел через минуту после планового времени.
                val decided = list.filter { it.status != DoseStatus.PENDING || now - it.plannedAt > OVERDUE_GRACE_MS }
                DayHeat(
                    taken = decided.count { it.status == DoseStatus.TAKEN },
                    planned = decided.size,
                    pending = list.size - decided.size,
                    marks = heatMarks(list, now),
                )
            }
            HeatmapState(monthStart = start, days = days)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HeatmapState())

    // ---------- Заметки ----------

    val notes: StateFlow<List<Note>> = db.noteDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun loadNote(id: Long): Note? = db.noteDao().getById(id)

    fun saveNote(note: Note, onDone: () -> Unit = {}) = viewModelScope.launch(io) {
        db.noteDao().upsert(note)
        ui { onDone() }
    }

    /**
     * Удаление с возвратом: снимок отдаётся экрану, чтобы снекбар «Вернуть» мог восстановить запись.
     * Без него длинная заметка о самочувствии исчезала от одного нажатия навсегда.
     */
    fun deleteNote(id: Long, onDone: (Note?) -> Unit = {}) = viewModelScope.launch(io) {
        val snapshot = db.noteDao().getById(id)
        db.noteDao().delete(id)
        ui { onDone(snapshot) }
    }

    fun restoreNote(note: Note) = viewModelScope.launch(io) { db.noteDao().upsert(note.copy(id = 0)) }

    /** Название таблетки (в том числе удалённой) — для карточки заметки. */
    suspend fun medName(id: Long): String? = db.medicationDao().getById(id)?.name

    // ---------- Визиты к врачу ----------

    val visits: StateFlow<List<DoctorVisit>> = db.visitDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun loadVisit(id: Long): DoctorVisit? = db.visitDao().getById(id)

    fun saveVisit(visit: DoctorVisit, onDone: () -> Unit = {}) = viewModelScope.launch(io) {
        db.visitDao().upsert(visit)
        VisitAlarms.reschedule(getApplication(), db)
        ui { onDone() }
    }

    fun deleteVisit(id: Long, onDone: (DoctorVisit?) -> Unit = {}) = viewModelScope.launch(io) {
        val snapshot = db.visitDao().getById(id)
        db.visitDao().delete(id)
        VisitAlarms.reschedule(getApplication(), db)
        ui { onDone(snapshot) }
    }

    fun restoreVisit(visit: DoctorVisit) = viewModelScope.launch(io) {
        db.visitDao().upsert(visit.copy(id = 0))
        VisitAlarms.reschedule(getApplication(), db)
    }

    fun rescheduleVisitAlarms() = viewModelScope.launch(io) {
        VisitAlarms.reschedule(getApplication(), db)
    }

    fun rescheduleWakeReminder() = viewModelScope.launch(io) {
        WakeReminder.schedule(getApplication())
    }

    /** Активные таблетки — для выбора «после какой» в мастере. */
    suspend fun activeMeds(): List<Medication> = db.medicationDao().getActive()

    // ---------- Каталог лекарств ----------

    val library: StateFlow<List<MedLibraryEntry>> = db.libraryDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun libraryEntries(): List<MedLibraryEntry> = db.libraryDao().getAll()

    suspend fun loadLibraryEntry(id: Long): MedLibraryEntry? = db.libraryDao().getById(id)

    fun saveLibraryEntry(entry: MedLibraryEntry, onDone: () -> Unit = {}) = viewModelScope.launch(io) {
        db.libraryDao().upsert(entry)
        ui { onDone() }
    }

    fun deleteLibraryEntry(id: Long, onDone: (MedLibraryEntry?) -> Unit = {}) = viewModelScope.launch(io) {
        val snapshot = db.libraryDao().getById(id)
        db.libraryDao().delete(id)
        ui { onDone(snapshot) }
    }

    fun restoreLibraryEntry(entry: MedLibraryEntry) = viewModelScope.launch(io) { db.libraryDao().upsert(entry.copy(id = 0)) }

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

    /** Трекер каждого типа один: новая запись того же типа обновляет существующий, а не плодит дубль с двойными напоминаниями. */
    fun saveTracker(tracker: Tracker, onDone: (Long) -> Unit = {}) = viewModelScope.launch(io) {
        val existing = if (tracker.id == 0L) db.trackerDao().getAll().firstOrNull { it.type == tracker.type } else null
        val toSave = if (existing != null) tracker.copy(id = existing.id) else tracker
        val id = db.trackerDao().upsert(toSave)
        TrackerAlarms.reschedule(getApplication(), db)
        ui { onDone(if (toSave.id == 0L) id else toSave.id) }
    }

    fun deleteTracker(id: Long, onDone: () -> Unit = {}) = viewModelScope.launch(io) {
        db.trackerDao().deleteEntriesOf(id)
        db.trackerDao().delete(id)
        TrackerAlarms.reschedule(getApplication(), db)
        ui { onDone() }
    }

    fun addTrackerEntry(entry: TrackerEntry, onDone: () -> Unit = {}) = viewModelScope.launch(io) {
        db.trackerDao().upsertEntry(entry)
        TrackerAlarms.reschedule(getApplication(), db)
        ui { onDone() }
    }

    fun deleteTrackerEntry(id: Long, onDone: (TrackerEntry?) -> Unit = {}) = viewModelScope.launch(io) {
        val snapshot = db.trackerDao().getAllEntries().firstOrNull { it.id == id }
        db.trackerDao().deleteEntry(id)
        ui { onDone(snapshot) }
    }

    fun restoreTrackerEntry(entry: TrackerEntry) = viewModelScope.launch(io) { db.trackerDao().upsertEntry(entry.copy(id = 0)) }

    // ---------- Бэкап и отчёт ----------

    suspend fun exportJson(): String = Backup.exportJson(db, Settings(getApplication()).exportMap())

    suspend fun exportDosesCsv(): String = Backup.dosesCsv(db)

    suspend fun exportTrackersCsv(): String = Backup.trackersCsv(db)

    /** Восстановление из JSON: стирает всё одной транзакцией и пересобирает будильники. */
    fun importBackup(json: String, onDone: (ImportOutcome) -> Unit) = viewModelScope.launch(io) {
        val settings = Settings(getApplication())
        val outcome = try {
            // Будильники старых приёмов снимаем до очистки базы: после импорта их id достанутся
            // другим приёмам, и звонок пришёл бы не о той таблетке.
            planner.cancelAllDoseAlarms()
            Backup.importJson(db, json) { values -> settings.importMap(values) }
            ImportOutcome.OK
        } catch (_: BackupTooNewException) {
            ImportOutcome.TOO_NEW
        } catch (_: NotABackupException) {
            ImportOutcome.NOT_BACKUP
        } catch (_: Exception) {
            ImportOutcome.FAILED
        }
        if (outcome == ImportOutcome.OK) {
            planner.rescheduleAlarms()
            VisitAlarms.reschedule(getApplication(), db)
            TrackerAlarms.reschedule(getApplication(), db)
        }
        ui { onDone(outcome) }
    }

    /**
     * [hideEmpty] — для файла и отправки: разделы «нет данных» не печатаются; предпросмотр их показывает.
     * [medIds] — таблетки, о которых говорят с этим врачом; null — все.
     */
    suspend fun buildReport(days: Int, sections: Set<String>, hideEmpty: Boolean = false, medIds: Set<Long>? = null): String =
        Report.build(db, days, Lang.s, sections, hideEmpty, medIds)

    /** Все таблетки для выбора в отчёте: сначала активные, архивные — следом (о них тоже спрашивают врача). */
    suspend fun medsForReport(): List<Medication> = db.medicationDao().getAllIncludingInactive()
        .sortedWith(compareByDescending<Medication> { it.active }.thenBy { it.sortOrder }.thenBy { it.id })

    suspend fun doctorPresets(): List<DoctorPreset> = db.doctorPresetDao().getAll()

    suspend fun saveDoctorPreset(preset: DoctorPreset) {
        db.doctorPresetDao().upsert(preset)
    }

    suspend fun deleteDoctorPreset(id: Long) = db.doctorPresetDao().delete(id)

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
        val now = System.currentTimeMillis()
        val all = db.doseDao().getAll().filter { it.dayEpochDay in from..to }
        val streak = streakDays(all, from, to, now)

        val perMed = all.groupBy { it.medNameSnapshot }
            .mapNotNull { (name, list) ->
                val counted = list.filter { it.status != DoseStatus.PENDING || it.plannedAt < now }
                if (counted.isEmpty()) return@mapNotNull null
                name to (counted.count { it.status == DoseStatus.TAKEN } * 100 / counted.size)
            }
            .sortedBy { it.second }

        return AdherenceState(streak = streak, perMed = perMed)
    }

    /** Кнопка «Подъём»: новый день плюс запись сна, если перед этим нажимали «Сон». */
    fun wakeUp() = viewModelScope.launch(io) {
        val now = System.currentTimeMillis()
        planner.wakeUp(now)
        logSleepIfPending(now)
    }

    /** «Начать новый день» и ручной сброс: только пересборка дня, без записи сна — человек не спал. */
    fun restartDay() = viewModelScope.launch(io) {
        Settings(getApplication()).pendingSleepStart = 0L
        planner.wakeUp(System.currentTimeMillis())
    }

    /** «Сон»: запоминаем момент, сама запись появится при пробуждении. */
    fun goToBed(now: Long = System.currentTimeMillis()) = viewModelScope.launch(io) {
        // Момент нужен и трекеру сна (при пробуждении), и истории дня.
        Settings(getApplication()).pendingSleepStart = now
        planner.goToBed(now)
    }

    /** «Еда»: фиксируем еду и запускаем приёмы, которые её ждали. */
    /** «Еда»: возвращает записанный момент — снекбар «Вернуть» должен знать, что убирать. */
    fun recordMeal(onDone: (Long) -> Unit = {}) = viewModelScope.launch(io) {
        val at = System.currentTimeMillis()
        planner.recordMeal(at)
        ui { onDone(at) }
    }

    /** Отмена «Сон»: день снова идёт. */
    fun undoBedtime() = viewModelScope.launch(io) { planner.undoBedtime() }

    /** Ошибочная отметка еды убирается из журнала. */
    fun deleteMeal(atMillis: Long) = viewModelScope.launch(io) {
        db.mealDao().deleteAt(atMillis)
        planner.rescheduleAlarms()
    }

    /** Вернуть ошибочно убранную отметку «Еда». */
    fun restoreMeal(atMillis: Long) = viewModelScope.launch(io) {
        db.mealDao().insert(MealEvent(atMillis = atMillis))
        planner.rescheduleAlarms()
    }

    /** Оценка сна и пробуждения для записи, собранной кнопками. */
    fun rateSleep(entry: TrackerEntry, sleep: Int, wake: Int) = viewModelScope.launch(io) {
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
     * Пробуждение после нажатой кнопки «Сон» — создаём запись сна и просим оценить.
     * Слишком короткий промежуток (меньше часа) считаем ошибкой нажатия, а не сном.
     */
    private suspend fun logSleepIfPending(now: Long) {
        val settings = Settings(getApplication())
        val start = settings.pendingSleepStart
        settings.pendingSleepStart = 0L
        if (!settings.askSleepOnWake) return
        val tracker = db.trackerDao().getAll().firstOrNull { it.type == TrackerType.SLEEP } ?: return
        // Спрашиваем при каждом пробуждении: без кнопки «Сон» просто не знаем,
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
        // Диалог поднимаем только если знаем ночь: иначе каждое утро экран встречает модальным
        // окном вместо списка таблеток. Неоценённую автозапись можно оценить кнопкой в трекере.
        if (known) sleepToRate.value = entry.copy(id = id)
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
    fun importSleepHistory(trackerId: Long, nights: List<Pair<Long, Long>>) = viewModelScope.launch(io) {
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

    fun take(doseId: Long) = viewModelScope.launch(io) { planner.markTaken(doseId) }

    /** Отметить приём прошлого дня из журнала: время — плановое, иначе история поедет. */
    fun takeAt(doseId: Long, at: Long) = viewModelScope.launch(io) { planner.markTaken(doseId, at) }

    fun skip(doseId: Long) = viewModelScope.launch(io) { planner.markSkipped(doseId) }

    fun undo(doseId: Long) = viewModelScope.launch(io) { planner.undo(doseId) }

    /** «Отложить» с карточки: момент живёт в приёме, как и у кнопки в шторке. */
    fun snooze(doseId: Long, minutes: Int) = viewModelScope.launch(io) { planner.snooze(doseId, minutes) }

    /** «Принять сейчас»: возвращает id записи для снекбара с отменой. */
    fun takeNow(medId: Long, onDone: (Long) -> Unit = {}) = viewModelScope.launch(io) {
        planner.takeNow(medId)?.let(onDone)
    }

    /** Отмена «Принять сейчас»: запись удаляется, остаток возвращается. */
    fun deleteIntake(doseId: Long) = viewModelScope.launch(io) { planner.deleteIntake(doseId) }

    /** «Выпить всё, что пора»: возвращает отмеченные id для снекбара с отменой. */
    fun takeAllDue(onDone: (List<Long>) -> Unit = {}) = viewModelScope.launch(io) {
        val marked = planner.takeAllDue()
        ui { onDone(marked) }
    }

    /** Сохранить новый порядок таблеток после перетаскивания. */
    fun saveMedOrder(orderedIds: List<Long>) = viewModelScope.launch(io) {
        orderedIds.forEachIndexed { index, id ->
            db.medicationDao().setSortOrder(id, index)
        }
    }

    /** Копия таблетки: у людей часто 2–3 препарата по одной схеме. */
    fun duplicateMed(medId: Long, onDone: (Long) -> Unit = {}) = viewModelScope.launch(io) {
        val med = db.medicationDao().getById(medId) ?: return@launch
        val order = db.medicationDao().getActive().maxOfOrNull { it.sortOrder } ?: 0
        val id = db.medicationDao().insert(
            med.copy(
                id = 0,
                name = med.name + " (" + Lang.s.copySuffix + ")",
                sortOrder = order + 1,
                // Остаток копируется вместе со схемой: заводить копию и сразу терять учёт упаковки бессмысленно.
                stockCount = med.stockCount,
            ),
        )
        planner.refreshMedToday(id)
        ui { onDone(id) }
    }

    /** Завершить курс: таблетка уходит с главной, её приёмы и будильники снимаются, дети отвязываются. */
    fun finishCourse(medId: Long, onDone: () -> Unit = {}) = viewModelScope.launch(io) {
        planner.finishCourse(medId)
        ui { onDone() }
    }

    suspend fun load(medId: Long): Medication? = db.medicationDao().getById(medId)

    fun save(med: Medication, onDone: (Long) -> Unit = {}) = viewModelScope.launch(io) {
        savedMedName.value = med.name
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
                    // Последний день курса, а не следующий за ним: isExpiredOn срабатывает уже на start + duration.
                    endEpochDay = courseEndDay(toSave.cycleStartEpochDay, toSave.durationDays),
                ),
            )
        }
        ui { onDone(id) }
    }

    /** Архив: таблетки, снятые с расписания. */
    val archived: StateFlow<List<Medication>> =
        db.medicationDao().observeArchived().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun restoreFromArchive(medId: Long) = viewModelScope.launch(io) { planner.restoreFromArchive(medId) }

    fun purgeFromArchive(medId: Long) = viewModelScope.launch(io) { planner.purgeFromArchive(medId) }

    /** Удаление с главной — то же, что завершение курса: деактивация с уборкой приёмов и связей. */
    fun delete(medId: Long, onDone: () -> Unit = {}) = viewModelScope.launch(io) {
        planner.finishCourse(medId)
        ui { onDone() }
    }
}
