package tech.unispace.pillreminder.data

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import tech.unispace.pillreminder.ui.RU
import tech.unispace.pillreminder.ui.formatClock

/** Журнал приёмов по дням: дни по порядку, внутри дня — по плановому времени, статус у каждого приёма. */
class ReportJournalTest {

    private val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT)
    private val day1 = LocalDate.of(2026, 9, 15).toEpochDay()
    private val day2 = day1 + 1

    private fun at(day: Long, hour: Int, minute: Int = 0): Long =
        LocalDate.ofEpochDay(day).atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun dose(
        id: Long,
        day: Long,
        hour: Int,
        medId: Long,
        status: DoseStatus,
        takenAt: Long? = null,
    ) = Dose(
        id = id,
        medId = medId,
        dayEpochDay = day,
        indexInDay = 0,
        plannedAt = at(day, hour),
        status = status,
        takenAt = takenAt,
        amount = 1.0,
        medNameSnapshot = "Снимок",
    )

    private val names = mapOf(1L to "Магний", 2L to "Витамин D")

    @Test
    fun groupsByDayAndCountsTaken() {
        val doses = listOf(
            dose(2, day1, 20, 2, DoseStatus.SKIPPED),
            dose(1, day1, 8, 1, DoseStatus.TAKEN, takenAt = at(day1, 8, 12)),
            dose(3, day2, 8, 1, DoseStatus.TAKEN, takenAt = at(day2, 8, 5)),
        )
        val lines = Report.journalLines(doses, names, RU, fmt)
        assertEquals("• 15.09.2026 — 1/2", lines[0])
        assertEquals("  " + formatClock(at(day1, 8)) + " · Магний — выпито " + formatClock(at(day1, 8, 12)), lines[1])
        assertEquals("  " + formatClock(at(day1, 20)) + " · Витамин D — пропущено", lines[2])
        assertEquals("• 16.09.2026 — 1/1", lines[3])
    }

    @Test
    fun pendingDoseIsMarkedAsNotDecided() {
        val lines = Report.journalLines(listOf(dose(1, day1, 9, 1, DoseStatus.PENDING)), names, RU, fmt)
        assertEquals("  " + formatClock(at(day1, 9)) + " · Магний — не отмечено", lines[1])
    }

    @Test
    fun deletedMedicationFallsBackToSnapshot() {
        val lines = Report.journalLines(listOf(dose(1, day1, 9, 99, DoseStatus.TAKEN)), emptyMap(), RU, fmt)
        assertEquals("  " + formatClock(at(day1, 9)) + " · Снимок — выпито", lines[1])
    }

    @Test
    fun emptyPeriodGivesNoLines() = assertEquals(emptyList<String>(), Report.journalLines(emptyList(), names, RU, fmt))
}
