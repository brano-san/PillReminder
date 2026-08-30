package tech.unispace.pillreminder.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Группа таблеток. Пока обычно одна ("Мои таблетки"), но модель уже позволяет
 * разложить приёмы по нескольким наборам.
 */
@Entity(tableName = "groups")
data class MedGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
)

/** Предустановленные формы выпуска; в [Medication.form] может лежать и своя строка. */
val MED_FORMS = listOf("Таблетка", "Инъекция", "Раствор", "Капли", "Ингалятор", "Порошок", "Свечи")

/**
 * Таблетка и правило её приёма.
 *
 * Всё время в приложении отсчитывается от кнопки «я проснулся»:
 * первый приём = момент пробуждения + [firstDoseOffsetMinutes],
 * каждый следующий = предыдущий + [intervalMinutes].
 */
@Entity(
    tableName = "medications",
    indices = [Index("groupId")],
)
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val name: String,
    /** Личная пометка: «за 15 мин до еды» и т.п. Показывается на главном экране. */
    val comment: String = "",
    /** Сколько таблеток за один приём (0.5 тоже допустимо). */
    val dosesPerIntake: Double = 1.0,
    /** Сколько раз за день принимать. */
    val timesPerDay: Int = 1,
    /** Через сколько минут после предыдущего приёма следующий. */
    val intervalMinutes: Int = 240,
    /** 1 = каждый день, 2 = через день, N = раз в N дней. */
    val everyNDays: Int = 1,
    /** Смещение первого приёма от момента пробуждения, в минутах. Может быть большим. */
    val firstDoseOffsetMinutes: Int = 0,
    /** День, от которого отсчитывается цикл [everyNDays] и продолжительность курса. */
    val cycleStartEpochDay: Long,
    val active: Boolean = true,
    val sortOrder: Int = 0,
    /** Форма выпуска: одна из [MED_FORMS] или свой текст. */
    val form: String = "Таблетка",
    /** Дозировка для справки: «500 мг». На расписание не влияет. */
    val doseInfo: String = "",
    /** Приём по необходимости: не планируется и не напоминается, только фиксируется. */
    val asNeeded: Boolean = false,
    /** Продолжительность курса в днях от [cycleStartEpochDay]; 0 = бессрочно. */
    val durationDays: Int = 0,
    /** Если задано — первый приём отсчитывается не от пробуждения, а от приёма этой таблетки. */
    val linkedToMedId: Long? = null,
    /** Через сколько минут после связанной таблетки пить эту. */
    val linkedDelayMinutes: Int = 120,
    /** Остаток таблеток в упаковке; null — не отслеживать. */
    val stockCount: Double? = null,
)

/** Курс закончился? */
fun Medication.isExpiredOn(day: Long): Boolean =
    durationDays > 0 && day >= cycleStartEpochDay + durationDays

enum class DoseStatus { PENDING, TAKEN, SKIPPED }

/**
 * Один конкретный приём: запланированный на сегодня или уже совершённый.
 * Одна таблица служит и расписанием на день, и историей.
 */
@Entity(
    tableName = "doses",
    indices = [Index("medId"), Index("dayEpochDay"), Index("plannedAt")],
)
data class Dose(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medId: Long,
    val dayEpochDay: Long,
    /** Порядковый номер приёма внутри дня, начиная с 0. */
    val indexInDay: Int,
    val plannedAt: Long,
    val status: DoseStatus = DoseStatus.PENDING,
    val takenAt: Long? = null,
    val amount: Double,
    /** Снимок названия — чтобы история не ломалась после переименования/удаления. */
    val medNameSnapshot: String = "",
)

/** Нажатие кнопки «я проснулся» за конкретный день. */
@Entity(tableName = "wake_events")
data class WakeEvent(
    @PrimaryKey val dayEpochDay: Long,
    val wakeAt: Long,
)

/** Свободная заметка с привязкой ко времени. */
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val body: String = "",
    val atMillis: Long,
)

/** Визит к врачу — прошедший или будущий. */
@Entity(tableName = "visits")
data class DoctorVisit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val comment: String = "",
    val atMillis: Long,
)

/** Запись каталога лекарств: что пил раньше, на что влияло, как переносилось. */
@Entity(tableName = "med_library")
data class MedLibraryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Период приёма: «март–май 2026». */
    val period: String = "",
    /** На что влияет. */
    val effect: String = "",
    /** Самочувствие, побочки. */
    val feeling: String = "",
    /** URI фото упаковки (SAF, с persistable-разрешением). */
    val photoUri: String? = null,
)

/** Типы трекеров. */
object TrackerType {
    const val WEIGHT = "weight"
    const val MOOD = "mood"
    const val SLEEP = "sleep"
}

/** Трекер: что отслеживаем и как часто спрашивать. */
@Entity(tableName = "trackers")
data class Tracker(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Один из [TrackerType]. */
    val type: String,
    /** Спрашивать раз в N дней. */
    val everyNDays: Int = 1,
    /** Во сколько спрашивать, минут от полуночи. */
    val askAtMinutes: Int = 600,
    /** День создания — якорь для периодичности. */
    val startEpochDay: Long,
    val remindEnabled: Boolean = true,
    /** Для веса: рост в см (нужен для ИМТ). */
    val heightCm: Int = 0,
    /** Для веса: пол "m"/"f". */
    val sex: String = "",
)

/** Одна запись трекера. Для сна дополнительно интервал и пометки. */
@Entity(
    tableName = "tracker_entries",
    indices = [Index("trackerId"), Index("atMillis")],
)
data class TrackerEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackerId: Long,
    val atMillis: Long,
    /** Вес в кг / настроение 1–5 / оценка сна 1–5. */
    val value: Double,
    val note: String = "",
    /** Сон: когда лёг. */
    val sleepStart: Long? = null,
    /** Сон: когда встал. */
    val sleepEnd: Long? = null,
    /** Сон: сколько раз просыпался. */
    val awakenings: Int = 0,
    /** Сон: пометки через запятую («кофе, маска»). */
    val tags: String = "",
)
