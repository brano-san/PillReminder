package tech.unispace.pillreminder.data

import org.json.JSONArray
import org.json.JSONObject
import tech.unispace.pillreminder.ui.formatClock
import java.time.LocalDate

/**
 * Полный бэкап базы в JSON и выгрузки в CSV.
 * Формат JSON версионирован — при изменении схемы поднимать "version" и учить импорт.
 */
object Backup {

    const val JSON_VERSION = 1

    suspend fun exportJson(db: AppDatabase): String {
        val root = JSONObject()
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
                            .put("stockCount", m.stockCount ?: JSONObject.NULL),
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
                            .put("plannedAt", d.plannedAt)
                            .put("status", d.status.name)
                            .put("takenAt", d.takenAt ?: JSONObject.NULL)
                            .put("amount", d.amount)
                            .put("medNameSnapshot", d.medNameSnapshot),
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
                            .put("atMillis", n.atMillis),
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
                            .put("atMillis", v.atMillis),
                    )
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
                            .put("period", e.period)
                            .put("effect", e.effect)
                            .put("feeling", e.feeling)
                            .put("photoUri", e.photoUri ?: JSONObject.NULL),
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
                            .put("everyNDays", t.everyNDays)
                            .put("askAtMinutes", t.askAtMinutes)
                            .put("startEpochDay", t.startEpochDay)
                            .put("remindEnabled", t.remindEnabled)
                            .put("heightCm", t.heightCm)
                            .put("sex", t.sex),
                    )
                }
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
                            .put("tags", e.tags),
                    )
                }
            },
        )

        root.put(
            "wakeEvents",
            JSONArray().apply {
                wakesAll(db).forEach { w ->
                    put(JSONObject().put("dayEpochDay", w.dayEpochDay).put("wakeAt", w.wakeAt))
                }
            },
        )

        return root.toString(2)
    }

    /** Полное восстановление: текущие данные стираются. Id пересоздаются с сохранением связей. */
    suspend fun importJson(db: AppDatabase, json: String) {
        val root = JSONObject(json)
        db.clearAllTables()

        val groupId = db.groupDao().insert(MedGroup(name = "Мои таблетки"))

        // Пересоздаём таблетки, запоминая соответствие старых id новым — для связей и приёмов.
        val medIdMap = mutableMapOf<Long, Long>()
        val medsArr = root.optJSONArray("medications") ?: JSONArray()
        val parsed = (0 until medsArr.length()).map { medsArr.getJSONObject(it) }
        for (o in parsed) {
            val newId = db.medicationDao().insert(
                Medication(
                    groupId = groupId,
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
                ),
            )
            medIdMap[o.getLong("id")] = newId
        }
        // Второй проход: связи «после другой таблетки».
        for (o in parsed) {
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
                    status = DoseStatus.valueOf(o.optString("status", "PENDING")),
                    takenAt = if (o.isNull("takenAt")) null else o.getLong("takenAt"),
                    amount = o.optDouble("amount", 1.0),
                    medNameSnapshot = o.optString("medNameSnapshot"),
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
                ),
            )
        }

        val libArr = root.optJSONArray("library") ?: JSONArray()
        for (i in 0 until libArr.length()) {
            val o = libArr.getJSONObject(i)
            db.libraryDao().upsert(
                MedLibraryEntry(
                    name = o.getString("name"),
                    period = o.optString("period"),
                    effect = o.optString("effect"),
                    feeling = o.optString("feeling"),
                    photoUri = if (o.isNull("photoUri")) null else o.getString("photoUri"),
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
                    everyNDays = o.optInt("everyNDays", 1),
                    askAtMinutes = o.optInt("askAtMinutes", 600),
                    startEpochDay = o.optLong("startEpochDay", today()),
                    remindEnabled = o.optBoolean("remindEnabled", true),
                    heightCm = o.optInt("heightCm", 0),
                    sex = o.optString("sex"),
                ),
            )
            trackerIdMap[o.getLong("id")] = newId
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
                ),
            )
        }

        val wakesArr = root.optJSONArray("wakeEvents") ?: JSONArray()
        for (i in 0 until wakesArr.length()) {
            val o = wakesArr.getJSONObject(i)
            db.wakeDao().upsert(WakeEvent(o.getLong("dayEpochDay"), o.getLong("wakeAt")))
        }
    }

    suspend fun dosesCsv(db: AppDatabase): String = buildString {
        appendLine("date,time,medication,status,amount,planned_time")
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
                ).joinToString(","),
            )
        }
    }

    suspend fun trackersCsv(db: AppDatabase): String = buildString {
        val types = db.trackerDao().getAll().associate { it.id to it.type }
        appendLine("tracker,timestamp,value,note,sleep_start,sleep_end,awakenings,tags")
        trackerEntriesAll(db).sortedBy { it.atMillis }.forEach { e ->
            appendLine(
                listOf(
                    types[e.trackerId] ?: "?",
                    java.time.Instant.ofEpochMilli(e.atMillis).toString(),
                    e.value.toString(),
                    csv(e.note),
                    e.sleepStart?.let { java.time.Instant.ofEpochMilli(it).toString() } ?: "",
                    e.sleepEnd?.let { java.time.Instant.ofEpochMilli(it).toString() } ?: "",
                    e.awakenings.toString(),
                    csv(e.tags),
                ).joinToString(","),
            )
        }
    }

    private fun csv(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    private suspend fun medsAll(db: AppDatabase) = db.medicationDao().getAllIncludingInactive()
    private suspend fun dosesAll(db: AppDatabase) = db.doseDao().getAll()
    private suspend fun trackerEntriesAll(db: AppDatabase) = db.trackerDao().getAllEntries()
    private suspend fun wakesAll(db: AppDatabase) = db.wakeDao().getAll()
}
