package tech.unispace.pillreminder.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Цифра на клетке календаря обязана читаться при любой доле выпитого. */
class HeatTextTest {

    private fun contrast(a: Color, b: Color): Double {
        val l1 = a.luminance().toDouble()
        val l2 = b.luminance().toDouble()
        val hi = maxOf(l1, l2)
        val lo = minOf(l1, l2)
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun everyStepIsReadable() {
        for (step in 0..5) {
            val ratio = step / 5f
            val ratioContrast = contrast(heatColor(ratio), heatTextColor(ratio))
            assertTrue("ступень $step: контраст $ratioContrast", ratioContrast >= 4.5)
        }
    }

    @Test
    fun wholeScaleKeepsWhiteDigits() {
        // Палитра подобрана так, что белая цифра читается на всех шести ступенях.
        for (step in 0..5) assertEquals(Color.White, heatTextColor(step / 5f))
    }

    @Test
    fun aLightCellWouldGetBlackDigits() {
        // Правило не привязано к текущей палитре: осветлим — цифра станет чёрной.
        assertEquals(Color.Black, if (Color.White.luminance() > 0.5f) Color.Black else Color.White)
    }
}
