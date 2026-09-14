package tech.unispace.pillreminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Сборка текста отчёта: в файле разделы без данных пропускаются, в предпросмотре — печатаются как «нет данных». */
class ReportRenderTest {

    private val head = listOf("ОТЧЁТ", "01.09.2026 — 30.09.2026")
    private val sections = listOf(
        Report.Section("Дисциплина", listOf("80%")),
        Report.Section("Настроение", emptyList()),
        Report.Section("Визиты", emptyList()),
    )

    @Test
    fun previewKeepsEmptySectionsWithNoDataLine() {
        val text = Report.render(head, sections, "нет данных", hideEmpty = false)
        assertTrue(text.contains("НАСТРОЕНИЕ"))
        assertTrue(text.contains("ВИЗИТЫ"))
        assertEquals(2, text.split("нет данных").size - 1)
    }

    @Test
    fun exportDropsEmptySectionsEntirely() {
        val text = Report.render(head, sections, "нет данных", hideEmpty = true)
        assertTrue(text.contains("ДИСЦИПЛИНА"))
        assertTrue(text.contains("80%"))
        assertFalse(text.contains("НАСТРОЕНИЕ"))
        assertFalse(text.contains("ВИЗИТЫ"))
        assertFalse(text.contains("нет данных"))
    }

    @Test
    fun headerAlwaysPrinted() {
        val text = Report.render(head, emptyList(), "нет данных", hideEmpty = true)
        assertTrue(text.startsWith("ОТЧЁТ"))
        assertTrue(text.contains("01.09.2026"))
    }
}
