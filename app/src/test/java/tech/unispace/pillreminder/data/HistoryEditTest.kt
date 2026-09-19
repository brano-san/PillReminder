package tech.unispace.pillreminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Правка истории прошлого дня не должна двигать расписание и не должна писать сегодняшнее время во вчера. */
class HistoryEditTest {

    private val hour = 60 * 60_000L
    private val today = 200L
    private val now = today * 24 * hour + 14 * hour

    private fun dose(day: Long, plannedAt: Long, status: DoseStatus = DoseStatus.PENDING) =
        Dose(id = 1, medId = 1, dayEpochDay = day, indexInDay = 0, plannedAt = plannedAt, status = status, amount = 1.0)

    @Test
    fun pastDoseKeepsItsPlannedTime() {
        val yesterday = dose(today - 1, (today - 1) * 24 * hour + 8 * hour)
        assertEquals(yesterday.plannedAt, markMoment(yesterday, now, today))
    }

    @Test
    fun todaysDoseIsMarkedNow() {
        val morning = dose(today, today * 24 * hour + 8 * hour)
        assertEquals(now, markMoment(morning, now, today))
    }

    @Test
    fun pastDayDoesNotTouchSchedule() =
        assertFalse(affectsSchedule(dose(today - 1, 0), today))

    @Test
    fun todayAndFutureTouchSchedule() {
        assertTrue(affectsSchedule(dose(today, 0), today))
        assertTrue(affectsSchedule(dose(today + 1, 0), today))
    }
}
