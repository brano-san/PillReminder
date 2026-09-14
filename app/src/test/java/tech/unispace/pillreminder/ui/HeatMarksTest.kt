package tech.unispace.pillreminder.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import tech.unispace.pillreminder.data.Dose
import tech.unispace.pillreminder.data.DoseStatus

/** Точки под числом в календаре: порядок по времени, ожидающий в прошлом — пропуск, в будущем — контур. */
class HeatMarksTest {

    private fun dose(at: Long, status: DoseStatus) =
        Dose(id = at, medId = 1, dayEpochDay = 1, indexInDay = 0, plannedAt = at, status = status, amount = 1.0)

    @Test
    fun marksFollowPlannedOrderAndStatus() {
        val now = 1000L
        val marks = heatMarks(
            listOf(dose(1500, DoseStatus.PENDING), dose(100, DoseStatus.TAKEN), dose(500, DoseStatus.PENDING), dose(300, DoseStatus.SKIPPED)),
            now,
        )
        assertEquals(listOf(HeatMark.TAKEN, HeatMark.MISSED, HeatMark.MISSED, HeatMark.PENDING), marks)
    }

    @Test
    fun emptyDayHasNoMarks() {
        assertEquals(emptyList<HeatMark>(), heatMarks(emptyList(), 0L))
    }
}
