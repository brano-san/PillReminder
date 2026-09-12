package tech.unispace.pillreminder.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.unispace.pillreminder.data.Dose
import tech.unispace.pillreminder.data.DoseStatus

/** Узлы схемы дня: порядок, состояния приёмов, отсев еды вне цикла. */
class DayTimelineTest {

    private val hour = 3_600_000L
    private val wake = 7 * hour

    private fun dose(
        id: Long,
        plannedAt: Long,
        status: DoseStatus = DoseStatus.PENDING,
        takenAt: Long? = null,
        name: String = "A",
    ) = Dose(
        id = id, medId = 1, dayEpochDay = 1, indexInDay = 0, plannedAt = plannedAt,
        status = status, takenAt = takenAt, amount = 1.0, medNameSnapshot = name,
    )

    @Test
    fun nodesAreSortedByTime() {
        val nodes = buildTimelineNodes(
            wakeAt = wake, bedAt = null,
            doses = listOf(dose(1, wake + 5 * hour), dose(2, wake + hour)),
            formById = emptyMap(), meals = listOf(wake + 2 * hour), now = wake,
        )
        assertEquals(
            listOf(TimelineKind.WAKE, TimelineKind.PILL, TimelineKind.MEAL, TimelineKind.PILL),
            nodes.map { it.kind },
        )
        assertTrue(nodes.zipWithNext().all { (a, b) -> a.at <= b.at })
    }

    @Test
    fun takenDoseUsesActualTimeAndForm() {
        val taken = dose(1, wake + hour, DoseStatus.TAKEN, takenAt = wake + 2 * hour, name = "Капли")
        val node = buildTimelineNodes(wake, null, listOf(taken), mapOf(1L to "Капли"), emptyList(), wake + 3 * hour)
            .single { it.kind == TimelineKind.PILL }
        assertEquals(TimelineState.TAKEN, node.state)
        assertEquals(wake + 2 * hour, node.at)
        assertEquals("Капли", node.form)
        assertEquals("Капли", node.label)
    }

    @Test
    fun pendingInThePastIsOverdue() {
        val now = wake + 3 * hour
        val nodes = buildTimelineNodes(wake, null, listOf(dose(1, wake + hour), dose(2, wake + 5 * hour)), emptyMap(), emptyList(), now)
        val pills = nodes.filter { it.kind == TimelineKind.PILL }
        assertEquals(TimelineState.OVERDUE, pills[0].state)
        assertEquals(TimelineState.PENDING, pills[1].state)
    }

    @Test
    fun skippedKeepsPlannedTime() {
        val skipped = dose(1, wake + hour, DoseStatus.SKIPPED, takenAt = wake + 4 * hour)
        val node = buildTimelineNodes(wake, null, listOf(skipped), emptyMap(), emptyList(), wake + 5 * hour)
            .single { it.kind == TimelineKind.PILL }
        assertEquals(TimelineState.SKIPPED, node.state)
        assertEquals(wake + hour, node.at)
    }

    @Test
    fun mealsOutsideTheCycleAreDropped() {
        val bed = wake + 15 * hour
        val nodes = buildTimelineNodes(
            wake, bed, emptyList(), emptyMap(),
            meals = listOf(wake - hour, wake + hour, bed + hour), now = bed,
        )
        assertEquals(listOf(TimelineKind.WAKE, TimelineKind.MEAL, TimelineKind.BED), nodes.map { it.kind })
        assertEquals(wake + hour, nodes[1].at)
    }

    @Test
    fun doseWaitingForMealIsNotOverdue() {
        val now = wake + 3 * hour
        val waiting = dose(1, wake + hour)
        val late = dose(2, wake + hour)
        val nodes = buildTimelineNodes(wake, null, listOf(waiting, late), emptyMap(), emptyList(), now, waitingIds = setOf(1L))
        val pills = nodes.filter { it.kind == TimelineKind.PILL }
        assertEquals(TimelineState.PENDING, pills.first { it.label == "A" && it.at == waiting.plannedAt && it.state == TimelineState.PENDING }.state)
        assertEquals(1, pills.count { it.state == TimelineState.OVERDUE })
    }

    @Test
    fun dueWithinGraceIsAmberNotRed() {
        val now = wake + hour + 30 * 60_000L
        val node = buildTimelineNodes(wake, null, listOf(dose(1, wake + hour)), emptyMap(), emptyList(), now)
            .single { it.kind == TimelineKind.PILL }
        assertEquals(TimelineState.DUE, node.state)
    }

    @Test
    fun snoozedDoseStaysPending() {
        val now = wake + 2 * hour
        val snoozed = dose(1, wake + hour).copy(remindAt = now + 30 * 60_000L, attempt = 0)
        val repeating = dose(2, wake + hour).copy(remindAt = now + 3 * 60_000L, attempt = 2)
        val pills = buildTimelineNodes(wake, null, listOf(snoozed, repeating), emptyMap(), emptyList(), now)
            .filter { it.kind == TimelineKind.PILL }
        assertEquals(TimelineState.PENDING, pills.first { it.at == snoozed.plannedAt && it.state == TimelineState.PENDING }.state)
        assertEquals(1, pills.count { it.state == TimelineState.DUE })
    }

    @Test
    fun pillLabelIsShortAndCarriesDose() {
        assertEquals("Эсц 10мг", timelinePillLabel("Эсциталопрам", "10 мг"))
        assertEquals("Магний", timelinePillLabel("Магний", ""))
        assertEquals("Эсцита…", timelinePillLabel("Эсциталопрам", ""))
        val node = buildTimelineNodes(
            wake, null, listOf(dose(1, wake + hour, name = "Эсциталопрам")), emptyMap(), emptyList(), wake,
            doseInfoById = mapOf(1L to "10 мг"),
        ).single { it.kind == TimelineKind.PILL }
        assertEquals("Эсц 10мг", node.label)
        assertEquals(1L, node.medId)
    }

    @Test
    fun noWakeMeansNoWakeNode() {
        val nodes = buildTimelineNodes(null, null, listOf(dose(1, wake + hour)), emptyMap(), listOf(wake + 2 * hour), wake)
        assertEquals(listOf(TimelineKind.PILL, TimelineKind.MEAL), nodes.map { it.kind })
    }
}
