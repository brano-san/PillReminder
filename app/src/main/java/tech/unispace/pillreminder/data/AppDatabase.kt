package tech.unispace.pillreminder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration

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
    ],
    version = 1,
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

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** Миграции между версиями схемы; для 1 → 2 добавить `object : Migration(1, 2) { ... }`. */
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "pills.db",
            )
                // Релиз 1.0: схема зафиксирована. Любое изменение сущностей = version++ и явная
                // Migration в MIGRATIONS, иначе Room упадёт при старте (данные пользователя терять нельзя).
                .addMigrations(*MIGRATIONS)
                .build()
                .also { instance = it }
        }
    }
}
