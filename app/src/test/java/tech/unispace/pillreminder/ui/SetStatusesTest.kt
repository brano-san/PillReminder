package tech.unispace.pillreminder.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import tech.unispace.pillreminder.data.Dose
import tech.unispace.pillreminder.data.DoseStatus

/** Точки прогресса: статусы набора по позициям, а не по счётчикам. */
class SetStatusesTest {

    private fun dose(index: Int, status: DoseStatus) =
        Dose(id = index + 1L, medId = 1, dayEpochDay = 1, indexInDay = index, plannedAt = index * 1000L, status = status, amount = 1.0)

    @Test
    fun emptySetIsAllUnplanned() {
        assertEquals(listOf(null, null, null), setStatuses(emptyList(), 3))
    }

    @Test
    fun firstSetKeepsOrder() {
        val set = listOf(dose(0, DoseStatus.TAKEN), dose(1, DoseStatus.SKIPPED), dose(2, DoseStatus.PENDING))
        assertEquals(listOf(DoseStatus.TAKEN, DoseStatus.SKIPPED, DoseStatus.PENDING), setStatuses(set, 3))
    }

    @Test
    fun secondCycleStartsFromZeroAgain() {
        // Второй набор суток нумеруется 3..5 — точки всё равно идут слева направо с первой.
        val set = listOf(dose(3, DoseStatus.TAKEN), dose(4, DoseStatus.PENDING))
        assertEquals(listOf(DoseStatus.TAKEN, DoseStatus.PENDING, null), setStatuses(set, 3))
    }

    @Test
    fun linkedChildPlannedLaterFillsOnlyItsSlots() {
        val set = listOf(dose(1, DoseStatus.PENDING))
        assertEquals(listOf(null, DoseStatus.PENDING), setStatuses(set, 2))
    }
}
