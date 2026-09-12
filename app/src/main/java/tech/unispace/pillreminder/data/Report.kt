package tech.unispace.pillreminder.data

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import tech.unispace.pillreminder.ui.S
import tech.unispace.pillreminder.ui.formatClock
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Текстовый отчёт за период — сводка для приёма у врача. */
object Report {

    const val SEC_INTAKES = "intakes"
    const val SEC_MEDS = "meds"
    const val SEC_TRACKERS = "trackers"
    const val SEC_NOTES = "notes"
    const val SEC_VISITS = "visits"
    const val SEC_LINKS = "links"
    val ALL_SECTIONS = setOf(SEC_INTAKES, SEC_MEDS, SEC_TRACKERS, SEC_NOTES, SEC_VISITS, SEC_LINKS)

    private fun header(sb: StringBuilder, title: String) {
        sb.appendLine()
        sb.appendLine(title.uppercase())
        sb.appendLine("─".repeat(title.length.coerceAtLeast(12)))
    }

    suspend fun build(db: AppDatabase, days: Int, s: S, sections: Set<String> = ALL_SECTIONS): String {
        val toDay = today()
        val fromDay = toDay - days + 1
        val zone = java.time.ZoneId.systemDefault()
        val fromMillis = LocalDate.ofEpochDay(fromDay).atStartOfDay(zone).toInstant().toEpochMilli()
        val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT)

        // Сегодняшние ещё не наступившие приёмы — не пропуски: считаем так же, как экран истории.
        val now = System.currentTimeMillis()
        val doses = db.doseDao().getAll().filter {
            it.dayEpochDay in fromDay..toDay && (it.status != DoseStatus.PENDING || it.plannedAt < now)
        }
        val meds = db.medicationDao().getAllIncludingInactive().associateBy { it.id }
        val planned = doses.size
        val taken = doses.count { it.status == DoseStatus.TAKEN }
        val skipped = doses.count { it.status == DoseStatus.SKIPPED }

        return buildString {
            appendLine(s.reportTitle.uppercase())
            appendLine(
                LocalDate.ofEpochDay(fromDay).format(dateFmt) + " — " +
                    LocalDate.ofEpochDay(toDay).format(dateFmt) + " · " + s.periodDays(days),
            )

            if (SEC_INTAKES in sections) {
                header(this, s.repAdherence)
                if (planned == 0) {
                    appendLine(s.repNoData)
                } else {
                    val pct = taken * 100 / planned
                    appendLine("$pct%")
                    appendLine("  $planned ${s.repPlanned} · $taken ${s.repTaken} · $skipped ${s.repSkipped}")
                }
            }

            if (SEC_MEDS in sections) {
                header(this, s.repMeds)
                val byMed = doses.groupBy { it.medId }
                if (byMed.isEmpty()) appendLine(s.repNoData)
                byMed.forEach { (medId, list) ->
                    val med = meds[medId]
                    val name = med?.name ?: list.first().medNameSnapshot.ifBlank { "?" }
                    appendLine("• $name")
                    if (med != null) {
                        val dose = listOf(s.formName(med.form), med.doseInfo).filter { it.isNotBlank() }.joinToString(" ")
                        val schedule = when {
                            med.asNeeded -> s.asNeededShort
                            med.byClock -> s.byClockShort + " " + med.fixedTimesList().joinToString(", ") { "%02d:%02d".format(it / 60, it % 60) }
                            med.linkedToMedId != null -> s.afterMed(meds[med.linkedToMedId]?.name ?: "?", s.duration(med.linkedDelayMinutes))
                            else -> s.schedule(med.timesPerDay, med.intervalMinutes, med.everyNDays)
                        }
                        appendLine("  $dose · ${s.perIntake(s.pills(med.dosesPerIntake, med.form))}")
                        appendLine("  ${s.repSchedule}: $schedule")
                    }
                    val t = list.count { it.status == DoseStatus.TAKEN }
                    val sk = list.count { it.status == DoseStatus.SKIPPED }
                    val pct = if (list.isEmpty()) 0 else t * 100 / list.size
                    appendLine("  $t ${s.repTaken} · $sk ${s.repSkipped} · ${list.size} ${s.repPlanned} · $pct%")
                }
            }

            if (SEC_TRACKERS in sections) {
                val trackers = db.trackerDao().getAll()
                val entries = db.trackerDao().getAllEntries().filter { it.atMillis >= fromMillis }
                for (tracker in trackers) {
                    val mine = entries.filter { it.trackerId == tracker.id }
                    val title = when (tracker.type) {
                        TrackerType.WEIGHT -> s.trackerWeight
                        TrackerType.MOOD -> s.trackerMood
                        else -> s.trackerSleep
                    }
                    header(this, title)
                    // Неоценённые автозаписи сна (значение 0) в статистику оценок не идут — только в часы сна.
                    val values = mine.map { it.value }.filter { tracker.type == TrackerType.WEIGHT || it > 0 }
                    if (mine.isEmpty()) {
                        appendLine(s.repNoData)
                    } else {
                        if (values.isEmpty()) {
                            appendLine(s.repNoData)
                        } else {
                            appendLine(
                                "  ${s.minLabel} ${fmt(values.min())} · ${s.maxLabel} ${fmt(values.max())} · " +
                                    "${s.avgLabel} ${fmt(values.average())} · n=${values.size}",
                            )
                        }
                        if (tracker.type == TrackerType.SLEEP) {
                            val durations = mine.mapNotNull { e ->
                                if (e.sleepStart != null && e.sleepEnd != null) (e.sleepEnd - e.sleepStart) / 3_600_000.0 else null
                            }
                            if (durations.isNotEmpty()) {
                                appendLine("  ${s.seriesSleepHours}: ${s.avgLabel} ${fmt(durations.average())}")
                            }
                        }
                    }
                }
            }

            if (SEC_NOTES in sections) {
                header(this, s.repNotes)
                val notes = db.noteDao().observeAllOnce().filter { it.atMillis >= fromMillis }
                if (notes.isEmpty()) appendLine(s.repNoData)
                notes.sortedBy { it.atMillis }.forEach { n ->
                    val date = java.time.Instant.ofEpochMilli(n.atMillis).atZone(zone).toLocalDate().format(dateFmt)
                    appendLine("• $date ${formatClock(n.atMillis)} — ${n.title}")
                    if (n.description.isNotBlank()) appendLine("  ${n.description}")
                }
            }

            if (SEC_LINKS in sections) {
                header(this, s.corrReportSection)
                val lines = correlationLines(db, fromDay, toDay, s)
                if (lines.isEmpty()) appendLine(s.repNoData) else lines.forEach { appendLine(it) }
            }

            if (SEC_VISITS in sections) {
                header(this, s.repVisits)
                val visits = db.visitDao().getAll().filter { it.atMillis >= fromMillis }
                if (visits.isEmpty()) appendLine(s.repNoData)
                visits.sortedBy { it.atMillis }.forEach { v ->
                    val date = java.time.Instant.ofEpochMilli(v.atMillis).atZone(zone).toLocalDate().format(dateFmt)
                    appendLine("• $date ${formatClock(v.atMillis)} — ${v.title}")
                    if (v.comment.isNotBlank()) appendLine("  ${v.comment}")
                }
            }
        }
    }

    /**
     * Парные корреляции между сериями (вес, настроение, качество сна, часы сна, дисциплина).
     * Считаем по дням, где есть обе величины; меньше трёх общих дней — связь не показываем.
     */
    private suspend fun correlationLines(db: AppDatabase, fromDay: Long, toDay: Long, s: S): List<String> {
        val days = (fromDay..toDay).toList()
        val trackers = db.trackerDao().getAll()
        val entries = db.trackerDao().getAllEntries()

        fun trackerSeries(type: String, sleepHours: Boolean = false): List<Double?> {
            val tracker = trackers.firstOrNull { it.type == type } ?: return days.map { null }
            val byDay = entries.filter { it.trackerId == tracker.id }.groupBy { epochDayOf(it.atMillis) }
            return days.map { day ->
                val list = byDay[day].orEmpty()
                when {
                    list.isEmpty() -> null
                    sleepHours -> list.mapNotNull { e ->
                        if (e.sleepStart != null && e.sleepEnd != null) (e.sleepEnd - e.sleepStart) / 3_600_000.0 else null
                    }.takeIf { it.isNotEmpty() }?.average()
                    // Оценки 1–5: неоценённые автозаписи (0) в корреляцию не попадают.
                    else -> list.map { it.value }.filter { type == TrackerType.WEIGHT || it > 0 }
                        .takeIf { it.isNotEmpty() }?.average()
                }
            }
        }

        val now = System.currentTimeMillis()
        val doses = db.doseDao().getAll()
            .filter { it.dayEpochDay in fromDay..toDay && (it.status != DoseStatus.PENDING || it.plannedAt < now) }
            .groupBy { it.dayEpochDay }
        val adherence = days.map { day ->
            val list = doses[day].orEmpty()
            if (list.isEmpty()) null else list.count { it.status == DoseStatus.TAKEN } * 100.0 / list.size
        }

        val series = listOf(
            s.trackerWeight to trackerSeries(TrackerType.WEIGHT),
            s.trackerMood to trackerSeries(TrackerType.MOOD),
            s.trackerSleep to trackerSeries(TrackerType.SLEEP),
            s.seriesSleepHours to trackerSeries(TrackerType.SLEEP, sleepHours = true),
            s.seriesAdherence to adherence,
        ).filter { (_, values) -> values.count { it != null } >= 3 }

        val out = mutableListOf<String>()
        for (i in series.indices) {
            for (j in i + 1 until series.size) {
                val r = pearson(series[i].second, series[j].second) ?: continue
                out += "• " + series[i].first + " × " + series[j].first + ": r = " + String.format(Locale.ROOT, "%.2f", r)
            }
        }
        return out
    }

    /** Коэффициент Пирсона по дням, где есть обе величины. */
    private fun pearson(a: List<Double?>, b: List<Double?>): Double? {
        val pairs = a.zip(b).mapNotNull { (x, y) -> if (x != null && y != null) x to y else null }
        if (pairs.size < 3) return null
        val n = pairs.size
        val mx = pairs.sumOf { it.first } / n
        val my = pairs.sumOf { it.second } / n
        var cov = 0.0
        var vx = 0.0
        var vy = 0.0
        for ((x, y) in pairs) {
            cov += (x - mx) * (y - my)
            vx += (x - mx) * (x - mx)
            vy += (y - my) * (y - my)
        }
        if (vx == 0.0 || vy == 0.0) return null
        return cov / kotlin.math.sqrt(vx * vy)
    }

    /** Тот же текст в PDF: А4, переносы строк, разбивка на страницы, заголовки жирным. */
    fun toPdf(text: String): ByteArray {
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40
        val contentWidth = pageWidth - margin * 2
        val bodyPaint = TextPaint().apply { isAntiAlias = true; textSize = 11f; color = android.graphics.Color.BLACK }
        val headPaint = TextPaint(bodyPaint).apply { textSize = 13f; isFakeBoldText = true }

        // Строки, набранные ЗАГЛАВНЫМИ (заголовки разделов), выводим жирным.
        data class Block(val layout: StaticLayout)
        val blocks = text.lines().map { line ->
            val isHeader = line.isNotBlank() && line == line.uppercase() && line.any { it.isLetter() }
            val paint = if (isHeader) headPaint else bodyPaint
            Block(StaticLayout.Builder.obtain(line.ifBlank { " " }, 0, line.ifBlank { " " }.length, paint, contentWidth).build())
        }

        val document = PdfDocument()
        var page: PdfDocument.Page? = null
        var y = 0
        var pageIndex = 0
        fun newPage() {
            page?.let { p ->
                val footer = Paint().apply { textSize = 9f; color = android.graphics.Color.GRAY }
                p.canvas.drawText(pageIndex.toString(), pageWidth / 2f, pageHeight - 20f, footer)
                document.finishPage(p)
            }
            pageIndex++
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex).create())
            y = margin
        }
        newPage()
        for (b in blocks) {
            if (y + b.layout.height > pageHeight - margin) newPage()
            val canvas = page!!.canvas
            canvas.save()
            canvas.translate(margin.toFloat(), y.toFloat())
            b.layout.draw(canvas)
            canvas.restore()
            y += b.layout.height + 2
        }
        page?.let { p ->
            val footer = Paint().apply { textSize = 9f; color = android.graphics.Color.GRAY }
            p.canvas.drawText(pageIndex.toString(), pageWidth / 2f, pageHeight - 20f, footer)
            document.finishPage(p)
        }

        val out = ByteArrayOutputStream()
        document.writeTo(out)
        document.close()
        return out.toByteArray()
    }

    private fun fmt(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.ROOT, "%.1f", v)
}
