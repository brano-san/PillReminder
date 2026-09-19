package tech.unispace.pillreminder.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Курс «по часам»: последний день обязан отдать приёмы, архив — только со следующего дня. */
class FixedCourseTest {

    private val start = 100L
    private val med = Medication(id = 1, groupId = 0, name = "Амоксициллин", cycleStartEpochDay = start, durationDays = 7, fixedTimes = "480,1200")
    private val last = start + 6

    @Test
    fun lastDayStillPlans() = assertTrue(planFixedOn(med, last))

    @Test
    fun tomorrowOfLastDayPlansNothing() = assertFalse(planFixedOn(med, last + 1))

    @Test
    fun courseIsNotOverOnItsLastDay() = assertFalse(courseOver(med, last))

    @Test
    fun courseIsOverNextDay() = assertTrue(courseOver(med, last + 1))

    @Test
    fun endlessCourseNeverEnds() {
        val endless = med.copy(durationDays = 0)
        assertTrue(planFixedOn(endless, start + 3650))
        assertFalse(courseOver(endless, start + 3650))
    }

    @Test
    fun weekdaysStillRespected() {
        // «по пн, ср, пт»: во вторник приёмов нет, но и архивировать нечего.
        val weekly = med.copy(weekdays = "1,3,5", durationDays = 0)
        val monday = java.time.LocalDate.of(2026, 9, 14).toEpochDay()
        assertTrue(planFixedOn(weekly, monday))
        assertFalse(planFixedOn(weekly, monday + 1))
        assertFalse(courseOver(weekly, monday + 1))
    }
}
