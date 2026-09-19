package tech.unispace.pillreminder.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import tech.unispace.pillreminder.ui.Lang
import tech.unispace.pillreminder.ui.formatClock
import java.time.Instant
import java.time.LocalDate

/** Файл создан более новой версией приложения — читать его текущий импорт не умеет. */
class BackupTooNewException : Exception()

/** Файл разобрался как JSON, но это не бэкап приложения — импорт не должен стирать базу. */
class NotABackupException : Exception()

/**
 * Полный бэкап базы в JSON и выгрузки в CSV.
 * Формат JSON версионирован — при изменении схемы поднимать "version" и учить импорт.
 */
object Backup {

    const val JSON_VERSION = 7

    /** Маркер своего файла: по нему импорт отличает бэкап от чужого JSON. */
    const val APP_MARKER = "doseday"

    /**
     * Настройки в JSON. Наборы строк (сроки визитов) становятся массивами, остальное пишется как есть.
     */
    fun settingsToJson(values: Map<String, Any?>): JSONObject {
        val obj = JSONObject()
        for ((key, value) in values) {
            when (value) {
                null -> Unit
                is Set<*> -> obj.put(key, JSONArray(value.map { it.toString() }))
                else -> obj.put(key, value)
            }
        }
        return obj
    }

    /**
     * Настройки обратно. Числа возвращаем как Int: в бэкап уходят только целочисленные настройки,
     * а `getInt` по значению Long падает с ClassCastException.
     */
    fun settingsFromJson(obj: JSONObject): Map<String, Any?> = buildMap {
        for (key in obj.keys()) {
            when (val value = obj.get(key)) {
                is JSONArray -> put(key, (0 until value.length()).map { value.getString(it) }.toSet())
                is Number -> put(key, value.toInt())
                else -> put(key, value)
            }
        }
    }

    suspend fun exportJson(db: AppDatabase, settings: Map<String, Any?> = emptyMap()): String {
        val root = JSONObject()
        root.put("app", APP_MARKER)
        root.put("version", JSON_VERSION)

        root.put(
            "medications",
            JSONArray().apply {
                medsAll(db).forEach { m ->
                    put(
                        JSONObject()
                            .put("id", m.id)
                            .put("groupId", m.groupId)
                            .put("name", m.name)
                            .put("comment", m.comment)
                            .put("dosesPerIntake", m.dosesPerIntake)
                            .put("timesPerDay", m.timesPerDay)
                            .put("intervalMinutes", m.intervalMinutes)
                            .put("everyNDays", m.everyNDays)
                            .put("firstDoseOffsetMinutes", m.firstDoseOffsetMinutes)
                            .put("cycleStartEpochDay", m.cycleStartEpochDay)
                            .put("active", m.active)
                            .put("sortOrder", m.sortOrder)
                            .put("form", m.form)
                            .put("doseInfo", m.doseInfo)
                            .put("asNeeded", m.asNeeded)
                            .put("durationDays", m.durationDays)
                            .put("linkedToMedId", m.linkedToMedId ?: JSONObject.NULL)
                            .put("linkedDelayMinutes", m.linkedDelayMinutes)
                            .put("stockCount", m.stockCount ?: JSONObject.NULL)
                            .put("fixedTimes", m.fixedTimes)
                            .put("afterMealMinutes", m.afterMealMinutes)
                            .put("apartFromOthersMinutes", m.apartFromOthersMinutes)
                            .put("apartFromMedIds", m.apartFromMedIds)
                            .put("beforeMealMinutes", m.beforeMealMinutes)
                            .put("mealCalories", m.mealCalories)
                            .put("weekdays", m.weekdays),
                    )
                }
            },
        )

        root.put(
            "doses",
            JSONArray().apply {
                dosesAll(db).forEach { d ->
                    put(
                        JSONObject()
                            .put("medId", d.medId)
                            .put("dayEpochDay", d.dayEpochDay)
                            .put("indexInDay", d.indexInDay)
                            .put("plannedAt", d.plannedAt).put("baseAt", d.baseAt ?: JSONObject.NULL)
                            .put("status", d.status.name)
                            .put("takenAt", d.takenAt ?: JSONObject.NULL)
                            .put("amount", d.amount)
                            .put("medNameSnapshot", d.medNameSnapshot)
                            .put("remindAt", d.remindAt ?: JSONObject.NULL)
                            .put("attempt", d.attempt),
                    )
                }
            },
        )

        root.put(
            "notes",
            JSONArray().apply {
                db.noteDao().observeAllOnce().forEach { n ->
                    put(
                        JSONObject()
                            .put("title", n.title)
                            .put("description", n.description)
                            .put("body", n.body)
                            .put("atMillis", n.atMillis)
                            .put("tags", n.tags)
                            // Привязка к таблетке — по старому id, импорт переведёт через карту id.
                            .put("medId", n.medId ?: JSONObject.NULL),
                    )
                }
            },
        )

        root.put(
            "visits",
            JSONArray().apply {
                db.visitDao().getAll().forEach { v ->
                    put(
                        JSONObject()
                            .put("title", v.title)
                            .put("comment", v.comment)
                            .put("atMillis", v.atMillis)
                            .put("place", v.place)
                            .put("remind", v.remind),
                    )
                }
            },
        )

        root.put("settings", settingsToJson(settings))

        root.put(
            "groups",
            JSONArray().apply {
                db.groupDao().getAll().forEach { g ->
                    put(JSONObject().put("id", g.id).put("name", g.name).put("sortOrder", g.sortOrder))
                }
            },
        )

        root.put(
            "doctorPresets",
            JSONArray().apply {
                db.doctorPresetDao().getAll().forEach { p ->
                    put(JSONObject().put("name", p.name).put("medIds", p.medIds))
                }
            },
        )

        root.put(
            "library",
            JSONArray().apply {
                db.libraryDao().getAll().forEach { e ->
                    put(
                        JSONObject()
                            .put("name", e.name)
                            .put("startEpochDay", e.startEpochDay ?: JSONObject.NULL)
                            .put("endEpochDay", e.endEpochDay ?: JSONObject.NULL)
                            .put("effect", e.effect)
                            .put("feeling", e.feeling)
                            .put("photoUri", e.photoUri ?: JSONObject.NULL)
                            .put("form", e.form)
                            .put("doseInfo", e.doseInfo),
                    )
                }
            },
        )

        root.put(
            "trackers",
            JSONArray().apply {
                db.trackerDao().getAll().forEach { t ->
                    put(
                        JSONObject()
                            .put("id", t.id)
                            .put("type", t.type)
                            .put("askTimes", t.askTimes)
                            .put("startEpochDay", t.startEpochDay)
                            .put("remindEnabled", t.remindEnabled)
                            .put("heightCm", t.heightCm)
                            .put("sex", t.sex),
                    )
                }
            },
        )

        root.put(
            "meals",
            JSONArray().apply {
                db.mealDao().getAll().forEach { m -> put(JSONObject().put("atMillis", m.atMillis)) }
            },
        )

        root.put(
            "trackerEntries",
            JSONArray().apply {
                trackerEntriesAll(db).forEach { e ->
                    put(
                        JSONObject()
                            .put("trackerId", e.trackerId)
                            .put("atMillis", e.atMillis)
                            .put("value", e.value)
                            .put("note", e.note)
                            .put("sleepStart", e.sleepStart ?: JSONObject.NULL)
                            .put("sleepEnd", e.sleepEnd ?: JSONObject.NULL)
                            .put("awakenings", e.awakenings)
                            .put("tags", e.tags)
                            .put("wakeValue", e.wakeValue ?: JSONObject.NULL)
                            .put("auto", e.auto),
                    )
                }
            },
        )

        root.put(
            "wakeEvents",
            JSONArray().apply {
                wakesAll(db).forEach { w ->
                    put(JSONObject().put("dayEpochDay", w.dayEpochDay).put("wakeAt", w.wakeAt).put("bedAt", w.bedAt ?: JSONObject.NULL))
                }
            },
        )

        return root.toString(2)
    }

    /**
     * Полное восстановление: текущие данные стираются. Id пересоздаются с сохранением связей.
     * Файл разбирается и проверяется до очистки, а вся запись идёт одной транзакцией:
     * битый или чужой файл откатывается и не оставляет пользователя с пустой базой.
     */
    /**
     * Похож ли файл на бэкап приложения. Свой маркер появился в версии 7; файлы постарше опознаём
     * по обязательной паре «version + medications» — иначе любой валидный JSON стирал бы базу.
     */
    fun looksLikeBackup(root: JSONObject): Boolean =
        root.optString("app") == APP_MARKER || (root.has("version") && root.has("medications"))

    suspend fun importJson(db: AppDatabase, json: String, applySettings: (Map<String, Any?>) -> Unit = {}) {
        val root = JSONObject(json)
        if (!looksLikeBackup(root)) throw NotABackupException()
        if (root.optInt("version", 1) > JSON_VERSION) throw BackupTooNewException()
        db.withTransaction { importParsed(db, root) }
        // Настройки живут в SharedPreferences, а не в базе: применяем после успешной транзакции.
        root.optJSONObject("settings")?.let { applySettings(settingsFromJson(it)) }
    }

    private suspend fun importParsed(db: AppDatabase, root: JSONObject) {
        db.clearAllTables()

        // Группы из файла; старого бэкапа без них хватает одной группы по умолчанию.
        val groupsArr = root.optJSONArray("groups") ?: JSONArray()
        val groupIdMap = mutableMapOf<Long, Long>()
        for (i in 0 until groupsArr.length()) {
            val o = groupsArr.getJSONObject(i)
            val newId = db.groupDao().insert(MedGroup(name = o.getString("name"), sortOrder = o.optInt("sortOrder")))
            groupIdMap[o.optLong("id")] = newId
        }
        val groupId = groupIdMap.values.firstOrNull() ?: db.groupDao().insert(MedGroup(name = Lang.s.defaultGroupName))

        // Пересоздаём таблетки, запоминая соответствие старых id новым — для связей и приёмов.
        val medIdMap = mutableMapOf<Long, Long>()
        val medsArr = root.optJSONArray("medications") ?: JSONArray()
        val parsed = (0 until medsArr.length()).map { medsArr.getJSONObject(it) }
        for (o in parsed) {
            val newId = db.medicationDao().insert(
                Medication(
                    groupId = groupIdMap[o.optLong("groupId")] ?: groupId,
                    name = o.getString("name"),
                    comment = o.optString("comment"),
                    dosesPerIntake = o.optDouble("dosesPerIntake", 1.0),
                    timesPerDay = o.optInt("timesPerDay", 1),
                    intervalMinutes = o.optInt("intervalMinutes", 240),
                    everyNDays = o.optInt("everyNDays", 1),
                    firstDoseOffsetMinutes = o.optInt("firstDoseOffsetMinutes", 0),
                    cycleStartEpochDay = o.optLong("cycleStartEpochDay", today()),
                    active = o.optBoolean("active", true),
                    sortOrder = o.optInt("sortOrder", 0),
                    form = o.optString("form", "Таблетка"),
                    doseInfo = o.optString("doseInfo"),
                    asNeeded = o.optBoolean("asNeeded", false),
                    durationDays = o.optInt("durationDays", 0),
                    linkedDelayMinutes = o.optInt("linkedDelayMinutes", 120),
                    stockCount = if (o.isNull("stockCount")) null else o.getDouble("stockCount"),
                    fixedTimes = o.optString("fixedTimes"),
                    afterMealMinutes = o.optInt("afterMealMinutes", 0),
                    apartFromOthersMinutes = o.optInt("apartFromOthersMinutes", 0),
                    beforeMealMinutes = o.optInt("beforeMealMinutes", 0),
                    mealCalories = o.optInt("mealCalories", 0),
                    weekdays = o.optString("weekdays"),
                    // id таблеток переводим вторым проходом — на этом шаге карта ещё неполная.
                    apartFromMedIds = "",
                ),
            )
            medIdMap[o.getLong("id")] = newId
        }
        // Второй проход: связи «после другой таблетки» и список «разносить с этими».
        for (o in parsed) {
            val newId = medIdMap[o.getLong("id")]
            val apart = o.optString("apartFromMedIds")
                .split(',').mapNotNull { it.trim().toLongOrNull() }
                .mapNotNull { medIdMap[it] }
            if (newId != null && apart.isNotEmpty()) {
                db.medicationDao().getById(newId)?.let {
                    db.medicationDao().update(it.copy(apartFromMedIds = apart.joinToString(",")))
                }
            }
            if (!o.isNull("linkedToMedId")) {
                val newId = medIdMap[o.getLong("id")] ?: continue
                val parentNew = medIdMap[o.getLong("linkedToMedId")] ?: continue
                db.medicationDao().getById(newId)?.let {
                    db.medicationDao().update(it.copy(linkedToMedId = parentNew))
                }
            }
        }

        val dosesArr = root.optJSONArray("doses") ?: JSONArray()
        for (i in 0 until dosesArr.length()) {
            val o = dosesArr.getJSONObject(i)
            val medId = medIdMap[o.getLong("medId")] ?: continue
            db.doseDao().insert(
                Dose(
                    medId = medId,
                    dayEpochDay = o.getLong("dayEpochDay"),
                    indexInDay = o.optInt("indexInDay", 0),
                    plannedAt = o.getLong("plannedAt"),
                    baseAt = if (o.isNull("baseAt")) null else o.getLong("baseAt"),
                    status = DoseStatus.valueOf(o.optString("status", "PENDING")),
                    takenAt = if (o.isNull("takenAt")) null else o.getLong("takenAt"),
                    amount = o.optDouble("amount", 1.0),
                    medNameSnapshot = o.optString("medNameSnapshot"),
                    remindAt = if (o.isNull("remindAt")) null else o.getLong("remindAt"),
                    attempt = o.optInt("attempt", 0),
                ),
            )
        }

        val notesArr = root.optJSONArray("notes") ?: JSONArray()
        for (i in 0 until notesArr.length()) {
            val o = notesArr.getJSONObject(i)
            db.noteDao().upsert(
                Note(
                    title = o.getString("title"),
                    description = o.optString("description"),
                    body = o.optString("body"),
                    atMillis = o.getLong("atMillis"),
                    tags = o.optString("tags"),
                    // Таблетки нет в файле — заметка становится общей, а не теряется.
                    medId = if (o.isNull("medId")) null else medIdMap[o.getLong("medId")],
                ),
            )
        }

        val visitsArr = root.optJSONArray("visits") ?: JSONArray()
        for (i in 0 until visitsArr.length()) {
            val o = visitsArr.getJSONObject(i)
            db.visitDao().upsert(
                DoctorVisit(
                    title = o.getString("title"),
                    comment = o.optString("comment"),
                    atMillis = o.getLong("atMillis"),
                    place = o.optString("place"),
                    remind = o.optBoolean("remind", true),
                ),
            )
        }

        // Пресеты врачей ссылаются на таблетки по id — переводим их так же, как связи и приёмы.
        val presetsArr = root.optJSONArray("doctorPresets") ?: JSONArray()
        for (i in 0 until presetsArr.length()) {
            val o = presetsArr.getJSONObject(i)
            val ids = o.optString("medIds").split(',').mapNotNull { it.trim().toLongOrNull() }
                .mapNotNull { medIdMap[it] }
            db.doctorPresetDao().upsert(DoctorPreset(name = o.getString("name"), medIds = ids.joinToString(",")))
        }

        val libArr = root.optJSONArray("library") ?: JSONArray()
        for (i in 0 until libArr.length()) {
            val o = libArr.getJSONObject(i)
            db.libraryDao().upsert(
                MedLibraryEntry(
                    name = o.getString("name"),
                    startEpochDay = if (o.isNull("startEpochDay")) null else o.getLong("startEpochDay"),
                    endEpochDay = if (o.isNull("endEpochDay")) null else o.getLong("endEpochDay"),
                    effect = o.optString("effect"),
                    feeling = o.optString("feeling"),
                    photoUri = if (o.isNull("photoUri")) null else safePhotoUri(o.getString("photoUri")),
                    form = o.optString("form"),
                    doseInfo = o.optString("doseInfo"),
                ),
            )
        }

        val trackerIdMap = mutableMapOf<Long, Long>()
        val trackersArr = root.optJSONArray("trackers") ?: JSONArray()
        for (i in 0 until trackersArr.length()) {
            val o = trackersArr.getJSONObject(i)
            val newId = db.trackerDao().upsert(
                Tracker(
                    type = o.getString("type"),
                    askTimes = o.optString("askTimes", "600"),
                    startEpochDay = o.optLong("startEpochDay", today()),
                    remindEnabled = o.optBoolean("remindEnabled", true),
                    heightCm = o.optInt("heightCm", 0),
                    sex = o.optString("sex"),
                ),
            )
            trackerIdMap[o.getLong("id")] = newId
        }

        val mealsArr = root.optJSONArray("meals") ?: JSONArray()
        for (i in 0 until mealsArr.length()) {
            db.mealDao().insert(MealEvent(atMillis = mealsArr.getJSONObject(i).getLong("atMillis")))
        }

        val entriesArr = root.optJSONArray("trackerEntries") ?: JSONArray()
        for (i in 0 until entriesArr.length()) {
            val o = entriesArr.getJSONObject(i)
            val trackerId = trackerIdMap[o.getLong("trackerId")] ?: continue
            db.trackerDao().upsertEntry(
                TrackerEntry(
                    trackerId = trackerId,
                    atMillis = o.getLong("atMillis"),
                    value = o.getDouble("value"),
                    note = o.optString("note"),
                    sleepStart = if (o.isNull("sleepStart")) null else o.getLong("sleepStart"),
                    sleepEnd = if (o.isNull("sleepEnd")) null else o.getLong("sleepEnd"),
                    awakenings = o.optInt("awakenings", 0),
                    tags = o.optString("tags"),
                    wakeValue = if (o.isNull("wakeValue")) null else o.getDouble("wakeValue"),
                    auto = o.optBoolean("auto", false),
                ),
            )
        }

        val wakesArr = root.optJSONArray("wakeEvents") ?: JSONArray()
        for (i in 0 until wakesArr.length()) {
            val o = wakesArr.getJSONObject(i)
            db.wakeDao().upsert(
                WakeEvent(
                    dayEpochDay = o.getLong("dayEpochDay"),
                    wakeAt = o.getLong("wakeAt"),
                    bedAt = if (o.isNull("bedAt")) null else o.getLong("bedAt"),
                ),
            )
        }
    }

    /**
     * `cycle_day` — день цикла (плавающий, может начаться вчера вечером), поэтому рядом
     * абсолютные метки `planned_at`/`taken_at` в ISO: приём в 00:30 не выглядит на сутки раньше.
     */
    suspend fun dosesCsv(db: AppDatabase): String = buildString {
        appendLine("cycle_day,time,medication,status,amount,planned_time,planned_at,taken_at")
        dosesAll(db).sortedBy { it.plannedAt }.forEach { d ->
            val date = LocalDate.ofEpochDay(d.dayEpochDay)
            appendLine(
                listOf(
                    date.toString(),
                    d.takenAt?.let { formatClock(it) } ?: "",
                    csv(d.medNameSnapshot),
                    d.status.name,
                    d.amount.toString(),
                    formatClock(d.plannedAt),
                    Instant.ofEpochMilli(d.plannedAt).toString(),
                    d.takenAt?.let { Instant.ofEpochMilli(it).toString() } ?: "",
                ).joinToString(","),
            )
        }
    }

    suspend fun trackersCsv(db: AppDatabase): String = buildString {
        val types = db.trackerDao().getAll().associate { it.id to it.type }
        appendLine("tracker,timestamp,value,note,sleep_start,sleep_end,awakenings,tags,wake_value,auto")
        trackerEntriesAll(db).sortedBy { it.atMillis }.forEach { e ->
            appendLine(
                listOf(
                    types[e.trackerId] ?: "?",
                    Instant.ofEpochMilli(e.atMillis).toString(),
                    e.value.toString(),
                    csv(e.note),
                    e.sleepStart?.let { Instant.ofEpochMilli(it).toString() } ?: "",
                    e.sleepEnd?.let { Instant.ofEpochMilli(it).toString() } ?: "",
                    e.awakenings.toString(),
                    csv(e.tags),
                    e.wakeValue?.toString() ?: "",
                    e.auto.toString(),
                ).joinToString(","),
            )
        }
    }

    /**
     * Значение для CSV. Кроме запятых и кавычек обезвреживаем формулы: строка, начинающаяся с
     * `=`, `+`, `-` или `@`, в Excel и Sheets исполняется как формула — а названия таблеток и
     * заметки могли приехать из чужого бэкапа.
     */
    fun csv(value: String): String {
        val safe = if (value.firstOrNull() in FORMULA_STARTS) "'" + value else value
        return if (safe.contains(',') || safe.contains('"') || safe.contains('\n')) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else {
            safe
        }
    }

    private val FORMULA_STARTS = setOf('=', '+', '-', '@')

    /**
     * Ссылка на фото из файла бэкапа. Чужой файл мог подсунуть любой URI, который приложение
     * потом открыло бы своими правами, поэтому пускаем только свои схемы.
     */
    fun safePhotoUri(value: String?): String? =
        value?.takeIf { it.startsWith("content://") || it.startsWith("file:///data/") }

    private suspend fun medsAll(db: AppDatabase) = db.medicationDao().getAllIncludingInactive()
    private suspend fun dosesAll(db: AppDatabase) = db.doseDao().getAll()
    private suspend fun trackerEntriesAll(db: AppDatabase) = db.trackerDao().getAllEntries()
    private suspend fun wakesAll(db: AppDatabase) = db.wakeDao().getAll()
}
