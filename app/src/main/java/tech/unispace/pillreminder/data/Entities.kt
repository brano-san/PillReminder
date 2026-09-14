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
 * «Сразу после еды» / «перед самой едой» в [Medication.afterMealMinutes] и [Medication.beforeMealMinutes]:
 * ноль занят значением «неважно», поэтому одна минута. Печатать её как «1 мин» нельзя —
 * только через `Lang.s.mealRelation()`.
 */
const val MEAL_NOW = 1

/** «Во время еды» в [Medication.afterMealMinutes]: ждёт кнопку «Еда», как «после еды», но словами — «во время еды». */
const val MEAL_WITH = 2

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
    /**
     * Приёмы «по часам»: CSV минут от полуночи («480,1200»). Пусто — расписание
     * отсчитывается от кнопки «я проснулся». Число приёмов в дне = число времён.
     */
    val fixedTimes: String = "",
    /** Пить не раньше чем через столько минут после еды; 0 — не важно. */
    val afterMealMinutes: Int = 0,
    /** Разносить с приёмом других таблеток минимум на столько минут; 0 — не важно. */
    val apartFromOthersMinutes: Int = 0,
    /**
     * С какими именно таблетками разносить (CSV из id). Пусто при
     * [apartFromOthersMinutes] > 0 — значит «с любыми другими».
     */
    val apartFromMedIds: String = "",
    /** Принимать не позже чем за столько минут до еды; 0 — не важно. */
    val beforeMealMinutes: Int = 0,
    /**
     * Минимум калорий в еде для правила «после еды»; 0 — не задано.
     * Подсказка человеку (виден на карточке, в шторке и на виджете), на расписание не влияет.
     */
    val mealCalories: Int = 0,
    /**
     * Дни недели приёма: CSV номеров ISO (1 — понедельник … 7 — воскресенье), «1,3,5». Пусто — по правилу
     * [everyNDays]. Метотрексат «пн, ср, пт» интервалом не описать, поэтому это отдельный режим.
     */
    val weekdays: String = "",
)

/** Дни недели приёма (ISO 1..7), отсортированные; пусто — правило «раз в N дней». */
fun Medication.weekdaysList(): List<Int> =
    weekdays.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.distinct().sorted()

/**
 * Принимается ли таблетка в этот день: по дням недели, если они заданы, иначе «раз в N дней» от начала курса.
 * Единственное определение — для планировщика, карточки и отчёта; чистая функция, с тестом.
 */
fun Medication.isDueOn(day: Long): Boolean {
    val days = weekdaysList()
    if (days.isNotEmpty()) return java.time.LocalDate.ofEpochDay(day).dayOfWeek.value in days
    if (everyNDays <= 1) return true
    val n = everyNDays.toLong()
    return ((day - cycleStartEpochDay) % n + n) % n == 0L
}

/** Список id таблеток, с которыми этот приём нужно разносить. */
fun Medication.apartFromList(): List<Long> =
    apartFromMedIds.split(',').mapNotNull { it.trim().toLongOrNull() }

/** Времена приёма «по часам», минут от полуночи; пусто — расписание от пробуждения. */
fun Medication.fixedTimesList(): List<Int> =
    fixedTimes.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 0 until 24 * 60 }.sorted()

/** Расписание привязано к часам, а не к пробуждению. */
val Medication.byClock: Boolean get() = fixedTimesList().isNotEmpty()

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
    /**
     * Исходное время до применения ограничений («после еды», «разносить»).
     * Нужно, чтобы повторный пересчёт не сдвигал приём каждый раз заново.
     */
    val baseAt: Long? = null,
    val status: DoseStatus = DoseStatus.PENDING,
    val takenAt: Long? = null,
    val amount: Double,
    /** Снимок названия — чтобы история не ломалась после переименования/удаления. */
    val medNameSnapshot: String = "",
    /**
     * Ближайший момент напоминания, назначенный кнопкой «Отложить» или цепочкой повторов; null — по плану.
     * Живёт в базе, чтобы `rescheduleAlarms()` (он пересобирает все будильники) не затирал отложенное.
     */
    val remindAt: Long? = null,
    /** Сколько повторов уже прозвучало; при `>= Settings.repeatCount` цепочка исчерпана и не возобновляется. */
    val attempt: Int = 0,
)

/** Нажатие кнопки «я проснулся» за конкретный день. */
@Entity(tableName = "wake_events")
data class WakeEvent(
    @PrimaryKey val dayEpochDay: Long,
    val wakeAt: Long,
    /** Когда нажали «Ложусь спать»; null — день ещё идёт. */
    val bedAt: Long? = null,
)

/**
 * Цикл ещё идёт: «Сон» не нажат и с подъёма прошло меньше [CYCLE_MAX_MS].
 * Единственное определение «дня» для планировщика, ViewModel, виджета и напоминания «Подъём».
 */
fun WakeEvent.isActive(now: Long): Boolean = bedAt == null && now - wakeAt in 0 until CYCLE_MAX_MS

/** Свободная заметка с привязкой ко времени. */
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val body: String = "",
    val atMillis: Long,
    /** К какой таблетке относится заметка («от этой тошнит»); null — общая. */
    val medId: Long? = null,
    /** Теги через запятую — по ним ищут и фильтруют. */
    val tags: String = "",
)

/** Визит к врачу — прошедший или будущий. */
@Entity(tableName = "visits")
data class DoctorVisit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val comment: String = "",
    val atMillis: Long,
    /** Адрес клиники или кабинет — виден на карточке и в уведомлении. */
    val place: String = "",
    /** Напоминать об этом визите (сроки — общие, из настроек); false — визит только в списке. */
    val remind: Boolean = true,
)

/** Запись каталога лекарств: что пил раньше, на что влияло, как переносилось. */
@Entity(tableName = "med_library")
data class MedLibraryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Начало приёма (epochDay); null — не указано. */
    val startEpochDay: Long? = null,
    /** Конец приёма (epochDay); null — ещё принимаю / не указано. */
    val endEpochDay: Long? = null,
    /** На что влияет. */
    val effect: String = "",
    /** Самочувствие, побочки. */
    val feeling: String = "",
    /** URI фото упаковки (SAF, с persistable-разрешением). */
    val photoUri: String? = null,
    /** Форма выпуска — подставляется в мастер при выборе из каталога. */
    val form: String = "",
    /** Дозировка «500 мг» — тоже подставляется в мастер. */
    val doseInfo: String = "",
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
    /** Во сколько спрашивать: минуты от полуночи через запятую («600,1200») — N раз в день. */
    val askTimes: String = "600",
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
    /** Сон: отдельная оценка пробуждения 1–5; null — не оценивали. */
    val wakeValue: Double? = null,
    /** Запись собрана кнопками «Ложусь спать»/«Я проснулся», а не заполнена руками. */
    val auto: Boolean = false,
)

/** Приём пищи: нужен для правила «пить через N минут после еды». */
@Entity(tableName = "meals")
data class MealEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atMillis: Long,
)

/** Разобрать [Tracker.askTimes] в отсортированный список минут. */
fun Tracker.askTimesList(): List<Int> =
    askTimes.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 0 until 24 * 60 }
        .distinct().sorted().ifEmpty { listOf(600) }
