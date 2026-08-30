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
    val rows: List<MedRow> = emptyList(),
    val loaded: Boolean = false,
)

/** День журнала: приёмы + время подъёма. */
data class JournalState(
    val day: Long = today(),
    val doses: List<Dose> = emptyList(),
    val wakeAt: Long? = null,
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

    private val dayFlow: Flow<Long> = ticker.map { today() }.distinctUntilChanged()

    val home: StateFlow<HomeState> =
        dayFlow.flatMapLatest { day ->
            combine(
                db.medicationDao().observeActive(),
                db.doseDao().observeDay(day),
                db.wakeDao().observeDay(day),
                ticker,
            ) { meds, doses, wake, now ->
                val byMed = doses.groupBy { it.medId }
                val nameById = meds.associate { it.id to it.name }
                val rows = meds.map { med ->
                    val list = byMed[med.id].orEmpty()
                    MedRow(
                        med = med,
                        dueToday = planner.isDueOn(med, day),
                        nextDose = list.filter { it.status == DoseStatus.PENDING }.minByOrNull { it.plannedAt },
                        takenToday = list.count { it.status == DoseStatus.TAKEN },
                        totalToday = if (list.isEmpty()) med.timesPerDay else list.size,
                        linkedParentName = med.linkedToMedId?.let { nameById[it] },
                    )
                }
                HomeState(now = now, wokeUpAt = wake?.wakeAt, rows = rows, loaded = true)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    // ---------- Статистика ----------

    val selectedDay = MutableStateFlow(today())

    val journal: StateFlow<JournalState> = selectedDay.flatMapLatest { day ->
        combine(
            db.doseDao().observeDay(day),
            db.wakeDao().observeDay(day),
        ) { doses, wake ->
            JournalState(day = day, doses = doses.sortedBy { it.plannedAt }, wakeAt = wake?.wakeAt)
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

    fun wakeUp() = viewModelScope.launch { planner.wakeUp() }

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
        onDone(id)
    }

    fun delete(medId: Long, onDone: () -> Unit = {}) = viewModelScope.launch {
        db.medicationDao().deactivate(medId)
        planner.refreshMedToday(medId)
        onDone()
    }
}
