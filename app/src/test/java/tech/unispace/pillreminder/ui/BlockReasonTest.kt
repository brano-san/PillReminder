package tech.unispace.pillreminder.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Заблокированная кнопка мастера обязана называть причину — и всегда первую по порядку экранов. */
class BlockReasonTest {

    private fun reason(
        name: Boolean = false,
        amount: Boolean = false,
        times: Boolean = false,
        interval: Boolean = false,
        weekdays: Boolean = false,
        period: Boolean = false,
    ) = blockReason(name, amount, times, interval, weekdays, period)

    @Test
    fun nothingWrong() = assertNull(reason())

    @Test
    fun nameFirst() = assertEquals(BlockReason.NAME, reason(name = true, amount = true, times = true))

    @Test
    fun amountBeforeSchedule() = assertEquals(BlockReason.AMOUNT, reason(amount = true, interval = true))

    @Test
    fun eachReasonIsReported() {
        assertEquals(BlockReason.TIMES, reason(times = true))
        assertEquals(BlockReason.INTERVAL, reason(interval = true))
        assertEquals(BlockReason.WEEKDAYS, reason(weekdays = true))
        assertEquals(BlockReason.PERIOD, reason(period = true))
    }
}
