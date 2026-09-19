package tech.unispace.pillreminder.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Границы периода и приёмы, которые ждут «Еда»: и то и другое искажало отчёт для врача. */
class ReportRangeTest {

    private val hour = 60 * 60_000L
    private val today = 300L
    private val now = today * 24 * hour + 15 * hour
    private val from = today - 29

    private fun dose(id: Long, day: Long, plannedAt: Long, status: DoseStatus = DoseStatus.PENDING) =
        Dose(id = id, medId = 1, dayEpochDay = day, indexInDay = 0, plannedAt = plannedAt, status = status, amount = 1.0)

    @Test
    fun waitingForMealIsNotAMiss() {
        val waiting = dose(7, today, now - 2 * hour)
        assertFalse(Report.includeDose(waiting, from, today, now, null, setOf(7L)))
        assertTrue(Report.includeDose(waiting, from, today, now, null, emptySet()))
    }

    @Test
    fun takenIntakeCountsEvenIfItWaitedForMeal() {
        val taken = dose(7, today, now - 2 * hour, DoseStatus.TAKEN)
        assertTrue(Report.includeDose(taken, from, today, now, null, setOf(7L)))
    }

    @Test
    fun futureDayStaysOut() =
        assertFalse(Report.includeDose(dose(1, today + 1, now + 24 * hour), from, today, now))

    @Test
    fun periodEdgesAreInclusive() {
        assertTrue(Report.includeDose(dose(1, from, from * 24 * hour + 8 * hour, DoseStatus.TAKEN), from, today, now))
        assertFalse(Report.includeDose(dose(1, from - 1, 0, DoseStatus.TAKEN), from, today, now))
    }
}
