package tech.unispace.pillreminder.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.unispace.pillreminder.data.Dose
import tech.unispace.pillreminder.data.DoseStatus

/** Текущий набор приёмов — срез по номерам, а не остаток от деления выпитых. */
class CurrentSetTest {

    private fun dose(index: Int, status: DoseStatus) =
        Dose(id = index + 1L, medId = 1, dayEpochDay = 1, indexInDay = index, plannedAt = index * 1000L, status = status, amount = 1.0)

    @Test
    fun emptyDayHasNoSet() {
        assertTrue(currentSet(emptyList(), 3).isEmpty())
    }

    @Test
    fun firstSetIsWholeDay() {
        val doses = listOf(dose(0, DoseStatus.TAKEN), dose(1, DoseStatus.SKIPPED), dose(2, DoseStatus.PENDING))
        assertEquals(listOf(0, 1, 2), currentSet(doses, 3).map { it.indexInDay })
    }

    @Test
    fun secondCycleOfTheDayCountsOnlyItsOwnSet() {
        // Первый набор: выпито, пропущено, выпито. Второй («Начать новый день»): один выпит, два ждут.
        val doses = listOf(
            dose(0, DoseStatus.TAKEN), dose(1, DoseStatus.SKIPPED), dose(2, DoseStatus.TAKEN),
            dose(3, DoseStatus.TAKEN), dose(4, DoseStatus.PENDING), dose(5, DoseStatus.PENDING),
        )
        val set = currentSet(doses, 3)
        assertEquals(listOf(3, 4, 5), set.map { it.indexInDay })
        assertEquals(1, set.count { it.status == DoseStatus.TAKEN })
        assertEquals(0, set.count { it.status == DoseStatus.SKIPPED })
    }

    @Test
    fun closedSetWithSkipShowsRealCounts() {
        val doses = listOf(dose(0, DoseStatus.TAKEN), dose(1, DoseStatus.SKIPPED))
        val set = currentSet(doses, 2)
        assertEquals(1, set.count { it.status == DoseStatus.TAKEN })
        assertEquals(1, set.count { it.status == DoseStatus.SKIPPED })
    }

    @Test
    fun nextIndexInSetRestartsEachCycle() {
        val doses = listOf(dose(0, DoseStatus.TAKEN), dose(1, DoseStatus.TAKEN), dose(2, DoseStatus.PENDING), dose(3, DoseStatus.PENDING))
        val size = 2
        val next = doses.first { it.status == DoseStatus.PENDING }
        assertEquals(1, next.indexInDay % size + 1)
    }

    @Test
    fun zeroSizeIsTreatedAsOne() {
        val doses = listOf(dose(0, DoseStatus.TAKEN), dose(1, DoseStatus.TAKEN))
        assertEquals(listOf(1), currentSet(doses, 0).map { it.indexInDay })
    }
}
