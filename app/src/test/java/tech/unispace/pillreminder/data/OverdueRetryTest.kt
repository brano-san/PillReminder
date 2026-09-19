package tech.unispace.pillreminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Просроченный приём должен получить ровно одно догоняющее напоминание, а не отодвигаться вечно. */
class OverdueRetryTest {

    private val minute = 60_000L
    private val now = 10_000_000L
    private val grace = 2 * 60 * minute
    private val interval = 30 * minute

    private fun plan(due: Long, remindAt: Long? = null, attempt: Int = 0, repeatEnabled: Boolean = true, count: Int = 20) =
        alarmPlan(due, now, remindAt, attempt, repeatEnabled, count, interval, grace)

    @Test
    fun futureIntakeRingsAtItsTime() {
        val at = now + 45 * minute
        assertEquals(AlarmPlan(at), plan(at))
    }

    @Test
    fun overdueGetsOneCatchUpThatIsRemembered() {
        val result = plan(now - 20 * minute)
        assertEquals(now + interval, result.at)
        assertTrue(result.remember)
    }

    @Test
    fun rememberedMomentIsNotPushedAgain() {
        // Так выглядит следующая пересборка: момент уже записан в приёме.
        val remembered = now + interval
        val result = plan(now - 20 * minute, remindAt = remembered)
        assertEquals(remembered, result.at)
        assertTrue(!result.remember)
    }

    @Test
    fun longOverdueStaysSilent() = assertNull(plan(now - grace - minute).at)

    @Test
    fun exhaustedChainDoesNotRestart() =
        assertNull(plan(now - 10 * minute, attempt = 20, count = 20).at)

    @Test
    fun withoutRepeatsThereIsNoCatchUp() =
        assertNull(plan(now - 10 * minute, repeatEnabled = false).at)

    @Test
    fun snoozeMomentWins() {
        val snoozed = now + 15 * minute
        assertEquals(snoozed, plan(now - minute, remindAt = snoozed).at)
    }
}
