package tech.unispace.pillreminder.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Приём «после еды» ждёт кнопку «Поел» после подъёма или после предыдущего приёма. */
class MealGateTest {

    private val hour = 60 * 60_000L
    private val wake = 7 * hour
    private val day = 10L

    private val afterMealMed = Medication(
        id = 1, groupId = 0, name = "Фурамаг", cycleStartEpochDay = 0,
        timesPerDay = 2, afterMealMinutes = MEAL_NOW,
    )

    private fun dose(index: Int, status: DoseStatus = DoseStatus.PENDING, takenAt: Long? = null) = Dose(
        id = index + 1L, medId = 1, dayEpochDay = day, indexInDay = index,
        plannedAt = wake + index * 8 * hour, status = status, takenAt = takenAt, amount = 1.0,
    )

    @Test
    fun noRuleNeverWaits() {
        val med = afterMealMed.copy(afterMealMinutes = 0)
        assertTrue(mealSatisfied(med, dose(0), listOf(dose(0)), emptyList(), wake))
    }

    @Test
    fun byClockNeverWaits() {
        val med = afterMealMed.copy(fixedTimes = "480,1200")
        assertTrue(mealSatisfied(med, dose(0), listOf(dose(0)), emptyList(), wake))
    }

    @Test
    fun firstDoseWaitsWithoutMeal() {
        assertFalse(mealSatisfied(afterMealMed, dose(0), listOf(dose(0)), emptyList(), wake))
    }

    @Test
    fun mealBeforeWakeDoesNotCount() {
        assertFalse(mealSatisfied(afterMealMed, dose(0), listOf(dose(0)), listOf(wake - hour), wake))
    }

    @Test
    fun mealAfterWakeReleasesFirstDose() {
        assertTrue(mealSatisfied(afterMealMed, dose(0), listOf(dose(0)), listOf(wake + hour), wake))
    }

    @Test
    fun secondDoseNeedsMealAfterFirstIntake() {
        val first = dose(0, DoseStatus.TAKEN, takenAt = wake + hour + 60_000L)
        val second = dose(1)
        val breakfast = wake + hour
        assertFalse(mealSatisfied(afterMealMed, second, listOf(first, second), listOf(breakfast), wake))
        val lunch = wake + 6 * hour
        assertTrue(mealSatisfied(afterMealMed, second, listOf(first, second), listOf(breakfast, lunch), wake))
    }

    @Test
    fun skippedPreviousDoseAlsoMovesThreshold() {
        val first = dose(0, DoseStatus.SKIPPED, takenAt = wake + 2 * hour)
        val second = dose(1)
        assertFalse(mealSatisfied(afterMealMed, second, listOf(first, second), listOf(wake + hour), wake))
        assertTrue(mealSatisfied(afterMealMed, second, listOf(first, second), listOf(wake + 3 * hour), wake))
    }

    @Test
    fun otherDaysDosesAreIgnored() {
        val yesterday = dose(0, DoseStatus.TAKEN, takenAt = wake - 20 * hour).copy(id = 99, dayEpochDay = day - 1)
        assertTrue(mealSatisfied(afterMealMed, dose(0), listOf(yesterday, dose(0)), listOf(wake + hour), wake))
    }

    @Test
    fun unknownWakeDoesNotWait() {
        assertTrue(mealSatisfied(afterMealMed, dose(0), listOf(dose(0)), emptyList(), null))
    }

    @Test
    fun breakfastDoesNotOpenAnAfternoonDose() {
        // Смещение «Днём (+6 ч)»: базовое время 13:00, еда учитывается не раньше 10:00.
        val afternoon = dose(0).copy(plannedAt = wake + 6 * hour, baseAt = wake + 6 * hour)
        val breakfast = wake + hour / 2
        assertFalse(mealSatisfied(afterMealMed, afternoon, listOf(afternoon), listOf(breakfast), wake))
        val lunch = wake + 5 * hour
        assertTrue(mealSatisfied(afterMealMed, afternoon, listOf(afternoon), listOf(breakfast, lunch), wake))
    }

    @Test
    fun lookbackNeverGoesBeforeWake() {
        // Смещение 1 ч: порог остаётся подъёмом, а не «за 3 ч до приёма» (это было бы до подъёма).
        val early = dose(0).copy(plannedAt = wake + hour, baseAt = wake + hour)
        assertFalse(mealSatisfied(afterMealMed, early, listOf(early), listOf(wake - hour), wake))
        assertTrue(mealSatisfied(afterMealMed, early, listOf(early), listOf(wake + 10 * 60_000L), wake))
    }
}
