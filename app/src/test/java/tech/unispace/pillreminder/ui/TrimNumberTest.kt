package tech.unispace.pillreminder.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Остаток в упаковке считается вычитанием долей, поэтому число обязано печататься округлённым:
 * «9,4», а не «9.400000000000002».
 */
class TrimNumberTest {

    @After
    fun reset() {
        Lang.code = "ru"
    }

    private fun after(start: Double, dose: Double, times: Int): Double {
        var left = start
        repeat(times) { left -= dose }
        return left
    }

    @Test
    fun wholeNumbersHaveNoDecimals() {
        Lang.code = "ru"
        assertEquals("10", trimNumber(10.0))
        assertEquals("0", trimNumber(0.0))
    }

    @Test
    fun floatDriftIsRounded() {
        Lang.code = "ru"
        // 10 − 6 × 0,1 в double даёт 9.400000000000002.
        assertEquals("9,4", trimNumber(after(10.0, 0.1, 6)))
        assertEquals("27,9", trimNumber(after(30.0, 0.7, 3)))
    }

    @Test
    fun quartersSurvive() {
        Lang.code = "ru"
        assertEquals("19,25", trimNumber(after(20.0, 0.25, 3)))
        assertEquals("9,5", trimNumber(9.5))
    }

    @Test
    fun englishUsesDot() {
        Lang.code = "en"
        assertEquals("9.5", trimNumber(9.5))
        assertEquals("12", trimNumber(12.0))
    }

    @Test
    fun pillsUseTheSameFormat() {
        Lang.code = "ru"
        assertEquals("9,4 таблетки", Lang.s.pills(after(10.0, 0.1, 6), "Таблетка"))
        Lang.code = "en"
        assertEquals("9.4 pills", Lang.s.pills(after(10.0, 0.1, 6), "Таблетка"))
    }
}
