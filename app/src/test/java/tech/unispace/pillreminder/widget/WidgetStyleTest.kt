package tech.unispace.pillreminder.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.unispace.pillreminder.ui.EN
import tech.unispace.pillreminder.ui.RU

/** Цвет текста по яркости фона, палитра и перевод процентов прозрачности в альфу. */
class WidgetStyleTest {

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    @Test
    fun whiteBackgroundNeedsDarkText() {
        assertEquals(WidgetStyle.TEXT_DARK, WidgetStyle.textModeFor(white))
        assertFalse(WidgetStyle.lightText(white, WidgetStyle.TEXT_AUTO))
    }

    @Test
    fun tealBackgroundNeedsLightText() {
        assertEquals(WidgetStyle.TEXT_LIGHT, WidgetStyle.textModeFor(WidgetStyle.DEFAULT_COLOR))
        assertEquals(WidgetStyle.TEXT_LIGHT, WidgetStyle.textModeFor(black))
    }

    @Test
    fun explicitModeWinsOverLuminance() {
        assertTrue(WidgetStyle.lightText(white, WidgetStyle.TEXT_LIGHT))
        assertFalse(WidgetStyle.lightText(black, WidgetStyle.TEXT_DARK))
    }

    @Test
    fun alphaMapsPercentToByte() {
        assertEquals(255, WidgetStyle.alpha(100))
        assertEquals(0, WidgetStyle.alpha(0))
        assertEquals(127, WidgetStyle.alpha(50))
        assertEquals(255, WidgetStyle.alpha(140))
        assertEquals(0, WidgetStyle.alpha(-5))
    }

    @Test
    fun textColorsAreOpaqueForPrimaryText() {
        assertEquals(0xFFFFFFFF.toInt(), WidgetStyle.textColor(primary = true, light = true))
        assertEquals(0xFF1A201E.toInt(), WidgetStyle.textColor(primary = true, light = false))
    }

    @Test
    fun paletteNamesMatchPresetsInBothLanguages() {
        assertEquals(WidgetStyle.presets.size, RU.widgetColorNames.size)
        assertEquals(WidgetStyle.presets.size, EN.widgetColorNames.size)
        assertEquals(WidgetStyle.DEFAULT_COLOR, WidgetStyle.presets.first())
        // Палитра без дублей: два одинаковых кружка выглядели бы как ошибка.
        assertEquals(WidgetStyle.presets.size, WidgetStyle.presets.distinct().size)
    }
}
