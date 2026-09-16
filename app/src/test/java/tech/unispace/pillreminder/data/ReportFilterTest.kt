package tech.unispace.pillreminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Какие приёмы попадают в отчёт: период, «будущее — не пропуск» и выбор таблеток врача. */
class ReportFilterTest {

    private val hour = 60 * 60_000L
    private val now = 100L * 24 * hour
    private val today = now / (24 * hour)
    private val from = today - 6
    private val to = today

    private fun dose(medId: Long, day: Long, plannedAt: Long, status: DoseStatus = DoseStatus.PENDING) =
        Dose(id = 1, medId = medId, dayEpochDay = day, indexInDay = 0, plannedAt = plannedAt, status = status, amount = 1.0)

    @Test
    fun takenInsidePeriodCounts() {
        assertTrue(Report.includeDose(dose(1, today - 2, now - 2 * 24 * hour, DoseStatus.TAKEN), from, to, now))
    }

    @Test
    fun beforePeriodIsOut() {
        assertFalse(Report.includeDose(dose(1, from - 1, now - 8 * 24 * hour, DoseStatus.TAKEN), from, to, now))
    }

    @Test
    fun todaysFutureIntakeIsNotAMiss() {
        assertFalse(Report.includeDose(dose(1, today, now + 3 * hour), from, to, now))
    }

    @Test
    fun overduePendingCounts() {
        assertTrue(Report.includeDose(dose(1, today, now - 3 * hour), from, to, now))
    }

    @Test
    fun nullFilterKeepsEveryPill() {
        assertTrue(Report.includeDose(dose(7, today - 1, now - 24 * hour, DoseStatus.TAKEN), from, to, now, null))
    }

    @Test
    fun onlyChosenPillsCount() {
        val d = dose(7, today - 1, now - 24 * hour, DoseStatus.TAKEN)
        assertTrue(Report.includeDose(d, from, to, now, setOf(7L, 9L)))
        assertFalse(Report.includeDose(d, from, to, now, setOf(9L)))
        assertFalse(Report.includeDose(d, from, to, now, emptySet()))
    }

    @Test
    fun presetKeepsOnlyRealIds() {
        assertEquals(listOf(1L, 2L, 30L), DoctorPreset(name = "Кардиолог", medIds = "1, 2,x,,30").medIdsList())
        assertEquals(emptyList<Long>(), DoctorPreset(name = "Пустой", medIds = "").medIdsList())
    }
}
