package tech.unispace.pillreminder

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tech.unispace.pillreminder.data.AppDatabase

/**
 * Миграции базы на реальном SQLite. Схема меняется почти каждый релиз, а ошибка в миграции роняет
 * приложение при старте у всех, кто обновился, — поэтому проверяем путь с каждой версии до текущей.
 *
 * Запуск: `./gradlew.bat connectedDebugAndroidTest` (нужно устройство или эмулятор).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migratesFromVersionOneToLatest() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO groups (id, name, sortOrder) VALUES (1, 'Мои таблетки', 0)")
            execSQL(
                "INSERT INTO medications (id, groupId, name, comment, dosesPerIntake, timesPerDay, intervalMinutes, " +
                    "everyNDays, firstDoseOffsetMinutes, cycleStartEpochDay, active, sortOrder, form, doseInfo, " +
                    "asNeeded, durationDays, linkedDelayMinutes, afterMealMinutes, apartFromOthersMinutes, " +
                    "apartFromMedIds, beforeMealMinutes) " +
                    "VALUES (1, 1, 'Магний', '', 1.0, 2, 720, 1, 0, 100, 1, 0, 'Таблетка', '500 мг', 0, 0, 120, 0, 0, '', 0)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, AppDatabase.LATEST_VERSION, true, *AppDatabase.MIGRATIONS)
        db.query("SELECT name, weekdays FROM medications WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Магний", cursor.getString(0))
            // Поле добавлено миграцией 4 → 5 со значением по умолчанию.
            assertEquals("", cursor.getString(1))
        }
        db.query("SELECT COUNT(*) FROM doctor_presets").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    @Test
    fun migratesFromEachVersion() {
        for (from in 2 until AppDatabase.LATEST_VERSION) {
            val name = "$dbName-$from"
            helper.createDatabase(name, from).close()
            helper.runMigrationsAndValidate(name, AppDatabase.LATEST_VERSION, true, *AppDatabase.MIGRATIONS)
        }
    }
}
