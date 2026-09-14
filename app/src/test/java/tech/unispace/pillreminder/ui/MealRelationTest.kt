package tech.unispace.pillreminder.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tech.unispace.pillreminder.data.MEAL_NOW
import tech.unispace.pillreminder.data.MEAL_WITH

/** Единый текст связи приёма с едой: «сразу» словами, а не «1 мин». */
class MealRelationTest {

    @Test
    fun immediatelyUsesWordsNotOneMinute() {
        assertEquals("сразу после еды", RU.mealRelation(MEAL_NOW, 0))
        assertEquals("right after a meal", EN.mealRelation(MEAL_NOW, 0))
    }

    @Test
    fun withMealHasItsOwnWords() {
        assertEquals("во время еды", RU.mealRelation(MEAL_WITH, 0))
        assertEquals("with a meal", EN.mealRelation(MEAL_WITH, 0))
    }

    @Test
    fun minutesAfterMeal() {
        assertEquals("через 30 мин после еды", RU.mealRelation(30, 0))
        assertEquals("30 min after a meal", EN.mealRelation(30, 0))
    }

    @Test
    fun beforeMeal() {
        assertEquals("за 1 ч до еды", RU.mealRelation(0, 60))
        assertEquals("перед самой едой", RU.mealRelation(0, MEAL_NOW))
    }

    @Test
    fun bothJoinedWithDot() {
        assertEquals("сразу после еды · за 30 мин до еды", RU.mealRelation(MEAL_NOW, 30))
    }

    @Test
    fun noRuleIsNull() {
        assertNull(RU.mealRelation(0, 0))
        assertNull(EN.mealRelation(0, 0, 500))
    }

    @Test
    fun caloriesOnlyWithAfterMeal() {
        assertEquals("сразу после еды, еда от 300 ккал", RU.mealRelation(MEAL_NOW, 0, 300))
        assertEquals("за 30 мин до еды", RU.mealRelation(0, 30, 300))
        assertEquals("right after a meal, meal of 300 kcal or more", EN.mealRelation(MEAL_NOW, 0, 300))
        // Калории склеены с правилом: одна метка, а не две спорящие.
        assertEquals(1, RU.mealRelationParts(30, 0, 400).size)
    }

    @Test
    fun amountFactMergesDoseAndCount() {
        assertEquals("10 мг × 2 таб.", RU.amountFact(2.0, "Таблетка", "10 мг"))
        assertEquals("0.5 таб.", RU.amountFact(0.5, "Таблетка", ""))
        assertEquals("3 капли", RU.amountFact(3.0, "Капли", ""))
        assertEquals("10 mg × 2 tab.", EN.amountFact(2.0, "Таблетка", "10 mg"))
        assertEquals("1 injection", EN.amountFact(1.0, "Инъекция", " "))
    }
}
