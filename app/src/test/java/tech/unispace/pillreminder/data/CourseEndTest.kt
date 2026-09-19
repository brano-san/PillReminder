package tech.unispace.pillreminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Дата конца курса в каталоге и правило «курс истёк» должны говорить об одном дне. */
class CourseEndTest {

    private val start = 500L

    @Test
    fun lastDayIsInclusive() = assertEquals(start + 6, courseEndDay(start, 7))

    @Test
    fun endlessCourseHasNoDate() = assertNull(courseEndDay(start, 0))

    @Test
    fun oneDayCourseEndsTheSameDay() = assertEquals(start, courseEndDay(start, 1))

    @Test
    fun endDayAgreesWithIsExpiredOn() {
        val med = Medication(id = 1, groupId = 0, name = "Курс", cycleStartEpochDay = start, durationDays = 7)
        val end = courseEndDay(start, 7)!!
        assertFalse(med.isExpiredOn(end))
        assertTrue(med.isExpiredOn(end + 1))
    }
}
