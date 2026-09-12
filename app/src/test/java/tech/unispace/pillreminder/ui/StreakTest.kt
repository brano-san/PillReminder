package tech.unispace.pillreminder.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import tech.unispace.pillreminder.data.Dose
import tech.unispace.pillreminder.data.DoseStatus

/** Серия дней без пропусков: сегодняшние будущие приёмы и пустые дни серию не рвут. */
class StreakTest {

    private val hour = 3_600_000L
    private val today = 100L
    private val now = today * 24 * hour + 10 * hour

    private fun dose(day: Long, status: DoseStatus, plannedHour: Int = 8) = Dose(
        id = day * 10 + plannedHour, medId = 1, dayEpochDay = day, indexInDay = 0,
        plannedAt = day * 24 * hour + plannedHour * hour, status = status, amount = 1.0,
    )

    @Test
    fun perfectPastDaysWithPendingTodayKeepTheStreak() {
        val doses = listOf(
            dose(today - 3, DoseStatus.TAKEN), dose(today - 2, DoseStatus.TAKEN), dose(today - 1, DoseStatus.TAKEN),
            dose(today, DoseStatus.TAKEN, plannedHour = 8), dose(today, DoseStatus.PENDING, plannedHour = 20),
        )
        assertEquals(3, streakDays(doses, today - 29, today, now))
    }

    @Test
    fun todayFullyTakenExtendsTheStreak() {
        val doses = listOf(dose(today - 1, DoseStatus.TAKEN), dose(today, DoseStatus.TAKEN))
        assertEquals(2, streakDays(doses, today - 29, today, now))
    }

    @Test
    fun overdueTodayBreaksImmediately() {
        val doses = listOf(dose(today - 1, DoseStatus.TAKEN), dose(today, DoseStatus.PENDING, plannedHour = 8))
        assertEquals(0, streakDays(doses, today - 29, today, now))
    }

    @Test
    fun skippedDayBreaksTheStreak() {
        val doses = listOf(dose(today - 2, DoseStatus.TAKEN), dose(today - 1, DoseStatus.SKIPPED), dose(today, DoseStatus.TAKEN))
        assertEquals(1, streakDays(doses, today - 29, today, now))
    }

    @Test
    fun emptyDaysAreSkippedNotBroken() {
        // Таблетка «через день»: приёмы только по чётным дням.
        val doses = listOf(dose(today - 4, DoseStatus.TAKEN), dose(today - 2, DoseStatus.TAKEN), dose(today, DoseStatus.TAKEN))
        assertEquals(3, streakDays(doses, today - 29, today, now))
    }

    @Test
    fun noDosesMeansZero() {
        assertEquals(0, streakDays(emptyList(), today - 29, today, now))
    }
}
