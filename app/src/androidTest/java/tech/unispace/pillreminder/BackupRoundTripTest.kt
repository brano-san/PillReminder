package tech.unispace.pillreminder

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tech.unispace.pillreminder.data.AppDatabase
import tech.unispace.pillreminder.data.Backup
import tech.unispace.pillreminder.data.DoctorPreset
import tech.unispace.pillreminder.data.MedGroup
import tech.unispace.pillreminder.data.Medication
import tech.unispace.pillreminder.data.Note
import tech.unispace.pillreminder.data.apartFromList
import tech.unispace.pillreminder.data.medIdsList

/**
 * Круговорот бэкапа: что записали — то и прочитали, включая ссылки между таблетками.
 * Именно здесь ловится потеря «разносить с этими таблетками» при восстановлении.
 *
 * Запуск: `./gradlew.bat connectedDebugAndroidTest` (нужно устройство или эмулятор).
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun linksBetweenPillsSurviveRestore() = runBlocking {
        val groupId = db.groupDao().insert(MedGroup(name = "Мои таблетки"))
        val calcium = db.medicationDao().insert(Medication(groupId = groupId, name = "Кальций", cycleStartEpochDay = 100))
        val iron = db.medicationDao().insert(Medication(groupId = groupId, name = "Железо", cycleStartEpochDay = 100))
        val thyroxine = db.medicationDao().insert(
            Medication(
                groupId = groupId,
                name = "Левотироксин",
                cycleStartEpochDay = 100,
                apartFromOthersMinutes = 120,
                apartFromMedIds = "$calcium,$iron",
                linkedToMedId = calcium,
            ),
        )
        db.doctorPresetDao().upsert(DoctorPreset(name = "Эндокринолог", medIds = "$thyroxine,$calcium"))
        db.noteDao().upsert(Note(title = "Тошнота", atMillis = 1_000, medId = thyroxine))

        val json = Backup.exportJson(db, mapOf("language" to "en"))
        var restoredSettings: Map<String, Any?> = emptyMap()
        Backup.importJson(db, json) { restoredSettings = it }

        val meds = db.medicationDao().getAllIncludingInactive()
        assertEquals(3, meds.size)
        val restored = meds.first { it.name == "Левотироксин" }
        val calciumId = meds.first { it.name == "Кальций" }.id
        val ironId = meds.first { it.name == "Железо" }.id
        // Ссылки переведены на новые id, а не оставлены как есть.
        assertEquals(setOf(calciumId, ironId), restored.apartFromList().toSet())
        assertEquals(calciumId, restored.linkedToMedId)

        val preset = db.doctorPresetDao().getAll().single()
        assertEquals(setOf(restored.id, calciumId), preset.medIdsList().toSet())

        val note = db.noteDao().observeAllOnce().single()
        assertEquals(restored.id, note.medId)

        assertEquals("en", restoredSettings["language"])
        assertNotNull(db.groupDao().getAll().firstOrNull())
    }

    @Test
    fun foreignFileDoesNotWipeTheDatabase() = runBlocking {
        val groupId = db.groupDao().insert(MedGroup(name = "Мои таблетки"))
        db.medicationDao().insert(Medication(groupId = groupId, name = "Магний", cycleStartEpochDay = 100))

        runCatching { Backup.importJson(db, """{"name":"other app","items":[]}""") }

        assertEquals(1, db.medicationDao().getAllIncludingInactive().size)
    }
}
