package tech.unispace.pillreminder.data

import tech.unispace.pillreminder.ui.S
import tech.unispace.pillreminder.ui.formatClock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Текстовый отчёт за период — сводка для приёма у врача. */
object Report {

    suspend fun build(db: AppDatabase, days: Int, s: S): String {
        val toDay = today()
        val fromDay = toDay - days + 1
        val fromMillis = LocalDate.ofEpochDay(fromDay)
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT)

        val doses = db.doseDao().getAll().filter { it.dayEpochDay in fromDay..toDay }
        val planned = doses.size
        val taken = doses.count { it.status == DoseStatus.TAKEN }
        val skipped = doses.count { it.status == DoseStatus.SKIPPED }

        return buildString {
            appendLine(s.reportTitle)
            appendLine(
                LocalDate.ofEpochDay(fromDay).format(dateFmt) + " — " +
                    LocalDate.ofEpochDay(toDay).format(dateFmt),
            )
            appendLine()

            // Дисциплина
            appendLine("== " + s.repAdherence + " ==")
            if (planned == 0) {
                appendLine(s.repNoData)
            } else {
                val pct = taken * 100 / planned
                appendLine(
                    "$pct% · $planned ${s.repPlanned}, $taken ${s.repTaken}, " +
                        "$skipped ${s.repSkipped}",
                )
            }
            appendLine()

            // По лекарствам
            appendLine("== " + s.repMeds + " ==")
            val byName = doses.groupBy { it.medNameSnapshot.ifBlank { "?" } }
            if (byName.isEmpty()) appendLine(s.repNoData)
            byName.forEach { (name, list) ->
                appendLine(
                    "- $name: ${list.count { it.status == DoseStatus.TAKEN }} ${s.repTaken}, " +
                        "${list.count { it.status == DoseStatus.SKIPPED }} ${s.repSkipped} " +
                        "(${list.size} ${s.repPlanned})",
                )
            }
            appendLine()

            // Трекеры
            val trackers = db.trackerDao().getAll()
            val entries = db.trackerDao().getAllEntries().filter { it.atMillis >= fromMillis }
            for (tracker in trackers) {
                val mine = entries.filter { it.trackerId == tracker.id }
                val title = when (tracker.type) {
                    TrackerType.WEIGHT -> s.trackerWeight
                    TrackerType.MOOD -> s.trackerMood
                    else -> s.trackerSleep
                }
                appendLine("== $title ==")
                if (mine.isEmpty()) {
                    appendLine(s.repNoData)
                } else {
                    val values = mine.map { it.value }
                    appendLine(
                        s.minLabel + " " + fmt(values.min()) + " · " +
                            s.maxLabel + " " + fmt(values.max()) + " · " +
                            s.avgLabel + " " + fmt(values.average()),
                    )
                    if (tracker.type == TrackerType.SLEEP) {
                        val durations = mine.mapNotNull { e ->
                            if (e.sleepStart != null && e.sleepEnd != null) {
                                (e.sleepEnd - e.sleepStart) / 3_600_000.0
                            } else {
                                null
                            }
                        }
                        if (durations.isNotEmpty()) {
                            appendLine(s.seriesSleepHours + ": " + s.avgLabel + " " + fmt(durations.average()))
                        }
                    }
                }
                appendLine()
            }

            // Заметки
            appendLine("== " + s.repNotes + " ==")
            val notes = db.noteDao().observeAllOnce().filter { it.atMillis >= fromMillis }
            if (notes.isEmpty()) appendLine(s.repNoData)
            notes.sortedBy { it.atMillis }.forEach { n ->
                val date = java.time.Instant.ofEpochMilli(n.atMillis)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(dateFmt)
                appendLine("- [$date ${formatClock(n.atMillis)}] ${n.title}" +
                    if (n.description.isNotBlank()) " — ${n.description}" else "")
            }
            appendLine()

            // Визиты
            appendLine("== " + s.repVisits + " ==")
            val visits = db.visitDao().getAll().filter { it.atMillis >= fromMillis }
            if (visits.isEmpty()) appendLine(s.repNoData)
            visits.sortedBy { it.atMillis }.forEach { v ->
                val date = java.time.Instant.ofEpochMilli(v.atMillis)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(dateFmt)
                appendLine("- [$date ${formatClock(v.atMillis)}] ${v.title}" +
                    if (v.comment.isNotBlank()) " — ${v.comment}" else "")
            }
        }
    }

    private fun fmt(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.ROOT, "%.1f", v)
}
