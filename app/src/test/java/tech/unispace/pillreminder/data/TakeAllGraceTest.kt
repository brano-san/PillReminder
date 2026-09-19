package tech.unispace.pillreminder.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** «Выпить всё, что пора» не имеет права скопом отметить приём, забытый несколько часов назад. */
class TakeAllGraceTest {

    private val hour = 60 * 60_000L
    private val now = 1_000 * hour

    private fun dose(plannedAt: Long, status: DoseStatus = DoseStatus.PENDING) =
        Dose(id = 1, medId = 1, dayEpochDay = 1, indexInDay = 0, plannedAt = plannedAt, status = status, amount = 1.0)

    @Test
    fun justDueIsTaken() = assertTrue(isTakeAllCandidate(dose(now - 10 * 60_000L), now))

    @Test
    fun longOverdueIsNotTakenInBulk() = assertFalse(isTakeAllCandidate(dose(now - 6 * hour), now))

    @Test
    fun borderIsTheGrace() {
        assertTrue(isTakeAllCandidate(dose(now - OVERDUE_GRACE_MS), now))
        assertFalse(isTakeAllCandidate(dose(now - OVERDUE_GRACE_MS - 1), now))
    }

    @Test
    fun longOverdueIsReportedAsMissed() {
        assertTrue(isMissed(dose(now - 6 * hour), now))
        assertFalse(isMissed(dose(now - 10 * 60_000L), now))
        assertFalse(isMissed(dose(now - 6 * hour, DoseStatus.TAKEN), now))
    }

    @Test
    fun futureIntakeIsNeitherDueNorMissed() {
        assertFalse(isTakeAllCandidate(dose(now + hour), now))
        assertFalse(isMissed(dose(now + hour), now))
    }
}
