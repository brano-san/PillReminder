package tech.unispace.pillreminder.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Журнал предлагает только те действия, которые меняют состояние приёма. */
class JournalActionsTest {

    @Test
    fun takenOffersSkipAndUndoButNotTake() =
        assertEquals(listOf(JournalAction.SKIP, JournalAction.UNDO), journalActions(DoseStatus.TAKEN, editable = true, pastDay = true))

    @Test
    fun skippedOffersTakeAndUndoButNotSkip() =
        assertEquals(listOf(JournalAction.TAKE, JournalAction.UNDO), journalActions(DoseStatus.SKIPPED, editable = true, pastDay = true))

    @Test
    fun pendingPastDayOffersBoth() =
        assertEquals(listOf(JournalAction.TAKE, JournalAction.SKIP), journalActions(DoseStatus.PENDING, editable = true, pastDay = true))

    @Test
    fun pendingTodayIsMarkedOnTheCard() =
        assertEquals(emptyList<JournalAction>(), journalActions(DoseStatus.PENDING, editable = true, pastDay = false))

    @Test
    fun nothingWithoutEditMode() {
        assertEquals(emptyList<JournalAction>(), journalActions(DoseStatus.TAKEN, editable = false, pastDay = true))
        assertEquals(emptyList<JournalAction>(), journalActions(DoseStatus.PENDING, editable = false, pastDay = true))
    }
}
