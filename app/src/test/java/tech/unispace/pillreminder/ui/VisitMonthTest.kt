package tech.unispace.pillreminder.ui

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class VisitMonthTest {

    private fun day(text: String) = LocalDate.parse(text).toEpochDay()

    private val today = day("2026-09-17")

    @Test
    fun opensOnNearestUpcomingVisit() {
        val days = setOf(day("2026-11-05"), day("2026-10-02"), day("2026-08-01"))
        assertEquals(day("2026-10-01"), visitStartMonth(days, today))
    }

    @Test
    fun todayVisitCountsAsUpcoming() {
        assertEquals(day("2026-09-01"), visitStartMonth(setOf(today, day("2026-12-01")), today))
    }

    @Test
    fun withoutUpcomingFallsBackToLastPast() {
        val days = setOf(day("2026-03-11"), day("2026-07-30"))
        assertEquals(day("2026-07-01"), visitStartMonth(days, today))
    }

    @Test
    fun emptyListKeepsCurrentMonth() =
        assertEquals(day("2026-09-01"), visitStartMonth(emptySet(), today))
}
