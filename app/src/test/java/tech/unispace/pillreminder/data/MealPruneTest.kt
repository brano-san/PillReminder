package tech.unispace.pillreminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Отметки «Еда» читаются на каждой пересборке расписания, поэтому таблица не должна расти вечно. */
class MealPruneTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_700_000_000_000L

    @Test
    fun keepsHalfAYear() = assertEquals(now - 180 * day, mealPruneBefore(now))

    @Test
    fun yesterdaysMealSurvives() = assertTrue(now - day > mealPruneBefore(now))

    @Test
    fun lastYearsMealIsPruned() = assertTrue(now - 365 * day < mealPruneBefore(now))

    @Test
    fun borderIsExactlyKeepDays() = assertEquals(now - 10 * day, mealPruneBefore(now, keepDays = 10))
}
