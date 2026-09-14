package tech.unispace.pillreminder.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Ступени цвета тепловой карты: по 20 % выпитого, от красного (0) к зелёному (5). */
class HeatStepTest {

    @Test
    fun boundariesFallIntoSteps() {
        assertEquals(0, heatStep(0f))
        assertEquals(0, heatStep(0.19f))
        assertEquals(1, heatStep(0.2f))
        assertEquals(2, heatStep(0.5f))
        assertEquals(4, heatStep(0.99f))
        assertEquals(5, heatStep(1f))
    }

    @Test
    fun outOfRangeIsClamped() {
        assertEquals(0, heatStep(-1f))
        assertEquals(5, heatStep(3f))
    }
}
