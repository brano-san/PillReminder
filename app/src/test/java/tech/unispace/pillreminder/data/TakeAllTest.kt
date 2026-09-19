package tech.unispace.pillreminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Кнопка «Выпить всё, что пора» обязана отметить ровно то, что посчитала. */
class TakeAllTest {

    private val now = 1_000_000L
    private val minute = 60_000L

    private fun dose(
        status: DoseStatus = DoseStatus.PENDING,
        plannedAt: Long = now - minute,
        remindAt: Long? = null,
        attempt: Int = 0,
    ) = Dose(
        id = 1, medId = 1, dayEpochDay = 1, indexInDay = 0, plannedAt = plannedAt,
        status = status, amount = 1.0, remindAt = remindAt, attempt = attempt,
    )

    @Test
    fun dueIntakeCounts() = assertTrue(isDueNow(dose(), now))

    @Test
    fun futureIntakeDoesNot() = assertFalse(isDueNow(dose(plannedAt = now + minute), now))

    @Test
    fun takenIntakeDoesNot() = assertFalse(isDueNow(dose(status = DoseStatus.TAKEN), now))

    @Test
    fun snoozedIntakeIsLeftAlone() =
        assertFalse(isDueNow(dose(remindAt = now + 15 * minute), now))

    @Test
    fun repeatChainIsNotSnooze() {
        // attempt > 0 — это цепочка повторов, приём всё ещё «пора».
        assertTrue(isDueNow(dose(remindAt = now + 3 * minute, attempt = 2), now))
    }

    @Test
    fun snoozeMomentInThePastDoesNotHold() =
        assertTrue(isDueNow(dose(remindAt = now - minute), now))

    @Test
    fun snoozedUntilMatchesTheRule() {
        assertEquals(now + 15 * minute, dose(remindAt = now + 15 * minute).snoozedUntil(now))
        assertEquals(null, dose(remindAt = now + 15 * minute, attempt = 1).snoozedUntil(now))
    }
}
