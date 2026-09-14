package tech.unispace.pillreminder.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Кому из трекеров пора сейчас: совпадение времени и отсутствие записи после предыдущего слота. */
class TrackerDueTest {

    private val dayStart = 1_000_000_000L
    private val times = listOf(8 * 60, 20 * 60)

    @Test
    fun firstSlotCountsFromMidnight() {
        assertTrue(trackerNeedsEntry(times, 0, null, dayStart))
        assertTrue(trackerNeedsEntry(times, 0, dayStart - 1, dayStart))
        assertFalse(trackerNeedsEntry(times, 0, dayStart + 60_000L, dayStart))
    }

    @Test
    fun laterSlotCountsFromPreviousSlot() {
        val morningEntry = dayStart + 9 * 60 * 60_000L
        assertFalse(trackerNeedsEntry(times, 1, morningEntry, dayStart))
        assertTrue(trackerNeedsEntry(times, 1, dayStart + 7 * 60 * 60_000L, dayStart))
    }

    @Test
    fun slotMatchesWithinOneMinute() {
        assertEquals(1, trackerSlotAt(times, 20 * 60 + 1))
        assertEquals(0, trackerSlotAt(times, 8 * 60))
        assertNull(trackerSlotAt(times, 12 * 60))
    }
}
