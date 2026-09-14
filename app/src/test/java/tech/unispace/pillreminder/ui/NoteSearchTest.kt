package tech.unispace.pillreminder.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import tech.unispace.pillreminder.data.Note

class NoteSearchTest {

    private val notes = listOf(
        Note(id = 1, title = "Тошнота", description = "После Магния", body = "Началось вечером, прошло к утру", atMillis = 1L),
        Note(id = 2, title = "Давление", description = "", body = "130/85 ещё раз", atMillis = 2L, tags = "сердце"),
        Note(id = 3, title = "Сон", description = "плохо спал", body = "", atMillis = 3L),
    )

    @Test
    fun matchesTitleCaseInsensitive() = assertEquals(listOf(1L), filterNotes(notes, "ТОШНОТА").map { it.id })

    @Test
    fun matchesDescription() = assertEquals(listOf(1L), filterNotes(notes, "магния").map { it.id })

    @Test
    fun matchesBody() = assertEquals(listOf(1L), filterNotes(notes, "вечером").map { it.id })

    @Test
    fun matchesTags() = assertEquals(listOf(2L), filterNotes(notes, "Сердце").map { it.id })

    @Test
    fun yoEqualsYe() = assertEquals(listOf(2L), filterNotes(notes, "еще").map { it.id })

    @Test
    fun allWordsMustMatchAcrossFields() {
        assertEquals(listOf(1L), filterNotes(notes, "тошнота утру").map { it.id })
        assertEquals(emptyList<Long>(), filterNotes(notes, "тошнота давление").map { it.id })
    }

    @Test
    fun blankQueryReturnsAll() = assertEquals(3, filterNotes(notes, "   ").size)
}
