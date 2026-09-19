package tech.unispace.pillreminder.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import tech.unispace.pillreminder.data.Dose
import tech.unispace.pillreminder.data.DoseStatus
import tech.unispace.pillreminder.data.OVERDUE_GRACE_MS

/**
 * Точки под числом в календаре: порядок по времени, пропуск — только после двух часов просрочки,
 * иначе клетка «сегодня» краснела через минуту после планового времени.
 */
class HeatMarksTest {

    private val hour = 60 * 60_000L
    private val now = 100 * hour

    private fun dose(at: Long, status: DoseStatus) =
        Dose(id = at, medId = 1, dayEpochDay = 1, indexInDay = 0, plannedAt = at, status = status, amount = 1.0)

    @Test
    fun marksFollowPlannedOrderAndStatus() {
        val marks = heatMarks(
            listOf(
                dose(now + hour, DoseStatus.PENDING),
                dose(now - 10 * hour, DoseStatus.TAKEN),
                dose(now - 5 * hour, DoseStatus.PENDING),
                dose(now - 7 * hour, DoseStatus.SKIPPED),
            ),
            now,
        )
        assertEquals(listOf(HeatMark.TAKEN, HeatMark.MISSED, HeatMark.MISSED, HeatMark.PENDING), marks)
    }

    @Test
    fun freshlyOverdueIsNotAMissYet() {
        val marks = heatMarks(listOf(dose(now - 5 * 60_000L, DoseStatus.PENDING)), now)
        assertEquals(listOf(HeatMark.PENDING), marks)
    }

    @Test
    fun missAppearsAfterTheGrace() {
        assertEquals(listOf(HeatMark.PENDING), heatMarks(listOf(dose(now - OVERDUE_GRACE_MS, DoseStatus.PENDING)), now))
        assertEquals(listOf(HeatMark.MISSED), heatMarks(listOf(dose(now - OVERDUE_GRACE_MS - 1, DoseStatus.PENDING)), now))
    }

    @Test
    fun emptyDayHasNoMarks() {
        assertEquals(emptyList<HeatMark>(), heatMarks(emptyList(), 0L))
    }
}
