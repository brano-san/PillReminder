package tech.unispace.pillreminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Счётчик «сколько дней курса осталось» на карточке: конец курса не должен наступать молча. */
class CourseLeftTest {

    private val start = 400L
    private val week = Medication(id = 1, groupId = 0, name = "Амоксициллин", cycleStartEpochDay = start, durationDays = 7)

    @Test
    fun firstDayShowsWholeCourse() = assertEquals(7, courseDaysLeft(week, start))

    @Test
    fun middleOfCourse() = assertEquals(4, courseDaysLeft(week, start + 3))

    @Test
    fun lastDayShowsOne() = assertEquals(1, courseDaysLeft(week, start + 6))

    @Test
    fun afterCourseThereIsNoLabel() = assertNull(courseDaysLeft(week, start + 7))

    @Test
    fun endlessCourseHasNoCounter() = assertNull(courseDaysLeft(week.copy(durationDays = 0), start))

    @Test
    fun counterAgreesWithArchiving() {
        // Пока счётчик больше нуля, таблетка обязана оставаться на главном экране.
        for (day in start..(start + 6)) {
            assertTrue(courseDaysLeft(week, day)!! > 0)
            assertTrue(!courseOver(week, day))
        }
        assertNull(courseDaysLeft(week, start + 7))
        assertTrue(courseOver(week, start + 7))
    }
}
