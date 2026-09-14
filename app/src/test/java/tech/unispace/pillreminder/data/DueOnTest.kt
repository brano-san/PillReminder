package tech.unispace.pillreminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.unispace.pillreminder.ui.EN
import tech.unispace.pillreminder.ui.RU
import java.time.LocalDate

/** «Принимается ли сегодня»: дни недели важнее «раз в N дней»; слова о частоте. */
class DueOnTest {

    // Понедельник 14.09.2026.
    private val monday = LocalDate.of(2026, 9, 14).toEpochDay()

    private fun med(everyN: Int = 1, weekdays: String = "", start: Long = monday) =
        Medication(groupId = 1, name = "A", everyNDays = everyN, weekdays = weekdays, cycleStartEpochDay = start)

    @Test
    fun everyDayIsAlwaysDue() {
        assertTrue(med().isDueOn(monday))
        assertTrue(med().isDueOn(monday + 5))
    }

    @Test
    fun everyOtherDayCountsFromCourseStart() {
        val m = med(everyN = 2)
        assertTrue(m.isDueOn(monday))
        assertFalse(m.isDueOn(monday + 1))
        assertTrue(m.isDueOn(monday + 2))
        assertTrue(m.isDueOn(monday - 2))
    }

    @Test
    fun weekdaysWinOverInterval() {
        val m = med(everyN = 3, weekdays = "1,3,5")
        assertTrue(m.isDueOn(monday))
        assertFalse(m.isDueOn(monday + 1))
        assertTrue(m.isDueOn(monday + 2))
        assertTrue(m.isDueOn(monday + 4))
        assertFalse(m.isDueOn(monday + 6))
    }

    @Test
    fun weekdaysListIsSortedAndClean() {
        assertEquals(listOf(1, 5, 7), med(weekdays = "7, 1,5,9,1").weekdaysList())
        assertTrue(med().weekdaysList().isEmpty())
    }

    @Test
    fun periodWordsInBothLanguages() {
        assertEquals("каждый день", RU.periodWords(1, emptyList()))
        assertEquals("через день", RU.periodWords(2, emptyList()))
        assertEquals("раз в 3 дня", RU.periodWords(3, emptyList()))
        assertEquals("по будням", RU.periodWords(1, listOf(1, 2, 3, 4, 5)))
        assertEquals("по выходным", RU.periodWords(1, listOf(6, 7)))
        assertEquals("по пн, ср, пт", RU.periodWords(1, listOf(1, 3, 5)))
        assertEquals("on weekdays", EN.periodWords(1, listOf(1, 2, 3, 4, 5)))
        assertEquals("on Mon, Wed, Fri", EN.periodWords(1, listOf(1, 3, 5)))
        assertEquals("1 раз в день по пн, ср, пт", RU.schedule(1, 240, 1, listOf(1, 3, 5)))
    }
}
