package tech.unispace.pillreminder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun toStatus(value: String): DoseStatus = DoseStatus.valueOf(value)

    @TypeConverter
    fun fromStatus(status: DoseStatus): String = status.name
}

@Database(
    entities = [
        MedGroup::class,
        Medication::class,
        Dose::class,
        WakeEvent::class,
        Note::class,
        DoctorVisit::class,
        MedLibraryEntry::class,
        Tracker::class,
        TrackerEntry::class,
        MealEvent::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun medicationDao(): MedicationDao
    abstract fun doseDao(): DoseDao
    abstract fun wakeDao(): WakeDao
    abstract fun noteDao(): NoteDao
    abstract fun visitDao(): VisitDao
    abstract fun libraryDao(): LibraryDao
    abstract fun trackerDao(): TrackerDao
    abstract fun mealDao(): MealDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** Миграции между версиями схемы; для 1 → 2 добавить `object : Migration(1, 2) { ... }`. */
        /**
         * 1 → 2: релиз 1.1. Всё, что появилось после 1.0, одной миграцией — расписание «по часам»,
         * еда, раздельная оценка сна, автозаписи сна, теги и привязка заметок, поля каталога,
         * отход ко сну и исходное время приёма. МИГРАЦИЯ ЗАФИКСИРОВАНА: 1.1 выпущена,
         * дальнейшие изменения схемы — только новой Migration(2, 3) и version = 3.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medications ADD COLUMN fixedTimes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE medications ADD COLUMN afterMealMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE medications ADD COLUMN apartFromOthersMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE medications ADD COLUMN apartFromMedIds TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE medications ADD COLUMN beforeMealMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE wake_events ADD COLUMN bedAt INTEGER")
                db.execSQL("ALTER TABLE doses ADD COLUMN baseAt INTEGER")
                db.execSQL("ALTER TABLE tracker_entries ADD COLUMN wakeValue REAL")
                db.execSQL("ALTER TABLE tracker_entries ADD COLUMN auto INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN medId INTEGER")
                db.execSQL("ALTER TABLE notes ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE med_library ADD COLUMN form TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE med_library ADD COLUMN doseInfo TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `meals` " +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `atMillis` INTEGER NOT NULL)",
                )
            }
        }

        /**
         * 2 → 3: релиз 1.2 — минимум калорий в еде для правила «после еды», а у приёма — момент
         * отложенного напоминания и счётчик повторов (чтобы пересборка будильников их не затирала).
         * МИГРАЦИЯ ЗАФИКСИРОВАНА: 1.2 выпущена, дальнейшие изменения схемы — только Migration(3, 4) и version = 4.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medications ADD COLUMN mealCalories INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE doses ADD COLUMN remindAt INTEGER")
                db.execSQL("ALTER TABLE doses ADD COLUMN attempt INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * 3 → 4: релиз 1.2.1 — у визита к врачу адрес/кабинет и выключатель напоминания.
         * МИГРАЦИЯ ЗАФИКСИРОВАНА: 1.2.1 выпущена, дальнейшие изменения схемы — только Migration(4, 5) и version = 5.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE visits ADD COLUMN place TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE visits ADD COLUMN remind INTEGER NOT NULL DEFAULT 1")
            }
        }

        /** 4 → 5: версия 1.2.3 (выпущена 15.09.2026) — приём по дням недели. Зафиксирована: новые поля — в 5 → 6. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medications ADD COLUMN weekdays TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "pills.db",
            )
                // Релиз 1.0: схема зафиксирована. Любое изменение сущностей = version++ и явная
                // Migration в MIGRATIONS, иначе Room упадёт при старте (данные пользователя терять нельзя).
                .addMigrations(*MIGRATIONS)
                // На тестовых сборках схема успела побывать «выше» — откат не должен ронять приложение.
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                .also { instance = it }
        }
    }
}
