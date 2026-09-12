package tech.unispace.pillreminder.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Моменты напоминаний о визите: только будущие, по порядку, без дублей. */
class VisitMomentsTest {

    private val hour = 3_600_000L
    private val now = 100 * hour
    private val visit = now + 48 * hour

    @Test
    fun pastMomentsAreDropped() {
        // «За 3 дня» уже прошло, «за 2 дня» — ровно сейчас (не впереди), «за день» и «за 3 часа» — впереди.
        val moments = visitReminderMoments(visit, listOf(72 * 60, 48 * 60, 24 * 60, 180), now)
        assertEquals(listOf(visit - 24 * hour, visit - 3 * hour), moments)
    }

    @Test
    fun sortedAscendingAndDistinct() {
        val moments = visitReminderMoments(visit, listOf(60, 180, 60), now)
        assertEquals(listOf(visit - 3 * hour, visit - hour), moments)
    }

    @Test
    fun visitInThePastHasNoMoments() {
        assertTrue(visitReminderMoments(now - hour, listOf(60, 180), now).isEmpty())
    }
}
