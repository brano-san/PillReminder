package tech.unispace.pillreminder.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Сколько приёмов снимет «Сон»: считаем только ожидающие и только не «по часам». */
class BedtimeDropTest {

    private fun dose(id: Long, medId: Long, status: DoseStatus) =
        Dose(id = id, medId = medId, dayEpochDay = 1, indexInDay = 0, plannedAt = 0, status = status, amount = 1.0)

    private val doses = listOf(
        dose(1, 10, DoseStatus.PENDING),
        dose(2, 10, DoseStatus.TAKEN),
        dose(3, 11, DoseStatus.PENDING),
        dose(4, 12, DoseStatus.PENDING),
        dose(5, 12, DoseStatus.SKIPPED),
    )

    @Test
    fun countsOnlyPending() = assertEquals(3, bedtimeDropCount(doses, emptySet()))

    @Test
    fun byClockIntakesSurvive() = assertEquals(1, bedtimeDropCount(doses, setOf(11L, 12L)))

    @Test
    fun nothingToDrop() = assertEquals(0, bedtimeDropCount(doses.filter { it.status != DoseStatus.PENDING }, emptySet()))
}
