package tech.unispace.pillreminder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

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

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "pills.db",
            )
                // До первого релиза схема нестабильна: при любом несовпадении версия/схема
                // база пересоздаётся с нуля. После релиза заменить на явные миграции.
                .fallbackToDestructiveMigration()
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                .also { instance = it }
        }
    }
}
