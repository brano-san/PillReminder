package tech.unispace.pillreminder.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<MedGroup>>

    @Query("SELECT * FROM groups ORDER BY sortOrder, id")
    suspend fun getAll(): List<MedGroup>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: MedGroup): Long

    @Update
    suspend fun update(group: MedGroup)

    @Delete
    suspend fun delete(group: MedGroup)
}

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications WHERE active = 1 ORDER BY sortOrder, firstDoseOffsetMinutes, id")
    fun observeActive(): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE active = 1 ORDER BY sortOrder, firstDoseOffsetMinutes, id")
    suspend fun getActive(): List<Medication>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getById(id: Long): Medication?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(med: Medication): Long

    @Update
    suspend fun update(med: Medication)

    @Query("SELECT * FROM medications WHERE active = 1 AND linkedToMedId = :parentId")
    suspend fun childrenOf(parentId: Long): List<Medication>

    @Query("SELECT * FROM medications")
    suspend fun getAllIncludingInactive(): List<Medication>

    @Query("UPDATE medications SET sortOrder = :order WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Int)

    @Query("UPDATE medications SET active = 0 WHERE id = :id")
    suspend fun deactivate(id: Long)
}

@Dao
interface DoseDao {
    @Query("SELECT * FROM doses")
    suspend fun getAll(): List<Dose>

    @Query("SELECT * FROM doses WHERE dayEpochDay = :day ORDER BY plannedAt")
    fun observeDay(day: Long): Flow<List<Dose>>

    @Query("SELECT * FROM doses WHERE dayEpochDay = :day ORDER BY plannedAt")
    suspend fun getDay(day: Long): List<Dose>

    @Query("SELECT * FROM doses WHERE id = :id")
    suspend fun getById(id: Long): Dose?

    @Query(
        "SELECT * FROM doses WHERE medId = :medId AND dayEpochDay = :day " +
            "AND status = 'PENDING' ORDER BY plannedAt",
    )
    suspend fun pendingForMedOnDay(medId: Long, day: Long): List<Dose>

    /** Ожидающие приёмы дня — перед удалением их будильники и уведомления надо снять (Planner.dropPending). */
    @Query("SELECT * FROM doses WHERE dayEpochDay = :day AND status = 'PENDING'")
    suspend fun pendingOnDay(day: Long): List<Dose>

    @Query("SELECT * FROM doses WHERE medId = :medId AND dayEpochDay IN (:days) AND status = 'PENDING'")
    suspend fun pendingForMedOnDays(medId: Long, days: List<Long>): List<Dose>

    @Query("SELECT * FROM doses WHERE dayEpochDay BETWEEN :from AND :to")
    fun observeBetween(from: Long, to: Long): Flow<List<Dose>>

    @Insert
    suspend fun insert(dose: Dose): Long

    @Insert
    suspend fun insertAll(doses: List<Dose>): List<Long>

    @Update
    suspend fun update(dose: Dose)

    @Update
    suspend fun updateAll(doses: List<Dose>)

    @Delete
    suspend fun delete(dose: Dose)
}

@Dao
interface WakeDao {
    @Query("SELECT * FROM wake_events")
    suspend fun getAll(): List<WakeEvent>

    @Query("SELECT * FROM wake_events WHERE dayEpochDay = :day")
    fun observeDay(day: Long): Flow<WakeEvent?>

    @Query("SELECT * FROM wake_events WHERE dayEpochDay = :day")
    suspend fun getDay(day: Long): WakeEvent?

    /** Последнее пробуждение: «день» плавающий и не совпадает с календарным. */
    @Query("SELECT * FROM wake_events ORDER BY wakeAt DESC LIMIT 1")
    suspend fun latest(): WakeEvent?

    @Query("SELECT * FROM wake_events ORDER BY wakeAt DESC LIMIT 1")
    fun observeLatest(): Flow<WakeEvent?>

    @Upsert
    suspend fun upsert(event: WakeEvent)
}

@Dao
interface MealDao {
    @Query("SELECT * FROM meals ORDER BY atMillis DESC LIMIT 1")
    suspend fun last(): MealEvent?

    @Query("SELECT * FROM meals ORDER BY atMillis DESC")
    suspend fun getAll(): List<MealEvent>

    @Query("SELECT * FROM meals ORDER BY atMillis DESC LIMIT 1")
    fun observeLast(): Flow<MealEvent?>

    /** Все приёмы пищи по времени — для правила «ждёт «Еда»» и схемы дня на главной. */
    @Query("SELECT atMillis FROM meals ORDER BY atMillis")
    fun observeAllTimes(): Flow<List<Long>>

    /** Ошибочное нажатие «Еда» убирается из журнала долгим нажатием. */
    @Query("DELETE FROM meals WHERE atMillis = :at")
    suspend fun deleteAt(at: Long)

    @Insert
    suspend fun insert(meal: MealEvent): Long

    @Query("DELETE FROM meals WHERE atMillis < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes")
    suspend fun observeAllOnce(): List<Note>

    @Query("SELECT * FROM notes ORDER BY atMillis DESC")
    fun observeAll(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): Note?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: Note): Long

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface DoctorPresetDao {
    @Query("SELECT * FROM doctor_presets ORDER BY name")
    suspend fun getAll(): List<DoctorPreset>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preset: DoctorPreset): Long

    @Query("DELETE FROM doctor_presets WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface VisitDao {
    @Query("SELECT * FROM visits ORDER BY atMillis")
    fun observeAll(): Flow<List<DoctorVisit>>

    @Query("SELECT * FROM visits WHERE atMillis > :now")
    suspend fun future(now: Long): List<DoctorVisit>

    @Query("SELECT * FROM visits")
    suspend fun getAll(): List<DoctorVisit>

    @Query("SELECT * FROM visits WHERE id = :id")
    suspend fun getById(id: Long): DoctorVisit?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(visit: DoctorVisit): Long

    @Query("DELETE FROM visits WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM med_library ORDER BY name")
    fun observeAll(): Flow<List<MedLibraryEntry>>

    @Query("SELECT * FROM med_library ORDER BY name")
    suspend fun getAll(): List<MedLibraryEntry>

    @Query("SELECT * FROM med_library WHERE id = :id")
    suspend fun getById(id: Long): MedLibraryEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: MedLibraryEntry): Long

    @Query("DELETE FROM med_library WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TrackerDao {
    @Query("SELECT * FROM trackers ORDER BY id")
    fun observeAll(): Flow<List<Tracker>>

    @Query("SELECT * FROM trackers")
    suspend fun getAll(): List<Tracker>

    @Query("SELECT * FROM trackers WHERE id = :id")
    suspend fun getById(id: Long): Tracker?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tracker: Tracker): Long

    @Query("DELETE FROM trackers WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM tracker_entries ORDER BY atMillis DESC")
    fun observeEntries(): Flow<List<TrackerEntry>>

    @Query("SELECT * FROM tracker_entries")
    suspend fun getAllEntries(): List<TrackerEntry>

    @Query("SELECT * FROM tracker_entries WHERE trackerId = :trackerId ORDER BY atMillis DESC LIMIT 1")
    suspend fun lastEntry(trackerId: Long): TrackerEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: TrackerEntry): Long

    @Query("DELETE FROM tracker_entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("DELETE FROM tracker_entries WHERE trackerId = :trackerId")
    suspend fun deleteEntriesOf(trackerId: Long)
}
