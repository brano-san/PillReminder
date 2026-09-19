package tech.unispace.pillreminder.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Приём «по часам» в 08:00 обязан остаться в 08:00 и в день перевода стрелок. */
class FixedTimesDstTest {

    private val berlin = ZoneId.of("Europe/Berlin")

    private fun hourOf(millis: Long, zone: ZoneId) =
        java.time.Instant.ofEpochMilli(millis).atZone(zone).hour

    @Test
    fun morningStaysMorningOnSpringForward() {
        // 29 марта 2026 — перевод стрелок вперёд в Европе.
        val day = LocalDate.of(2026, 3, 29)
        assertEquals(8, hourOf(fixedTimeMillis(day, 8 * 60, berlin), berlin))
    }

    @Test
    fun eveningStaysEveningOnFallBack() {
        val day = LocalDate.of(2026, 10, 25)
        assertEquals(20, hourOf(fixedTimeMillis(day, 20 * 60, berlin), berlin))
    }

    @Test
    fun ordinaryDayIsExact() {
        val day = LocalDate.of(2026, 9, 16)
        val at = fixedTimeMillis(day, 9 * 60 + 30, berlin)
        val local = java.time.Instant.ofEpochMilli(at).atZone(berlin)
        assertEquals(9, local.hour)
        assertEquals(30, local.minute)
    }

    @Test
    fun midnightAndLastMinuteWork() {
        val day = LocalDate.of(2026, 9, 16)
        assertEquals(0, hourOf(fixedTimeMillis(day, 0, berlin), berlin))
        assertEquals(23, hourOf(fixedTimeMillis(day, 23 * 60 + 59, berlin), berlin))
    }
}
