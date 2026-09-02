@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package tech.unispace.pillreminder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tech.unispace.pillreminder.alarm.formatAmount
import tech.unispace.pillreminder.data.Dose
import tech.unispace.pillreminder.data.DoseStatus
import tech.unispace.pillreminder.data.today
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.format.TextStyle

/** История: журнал по дням и тепловая карта. Вкладки листаются свайпом. */
@Composable
fun StatsScreen(
    adherence: AdherenceState,
    journal: JournalState,
    heatmap: HeatmapState,
    onSelectDay: (Long) -> Unit,
    onMonthShift: (Long) -> Unit,
    onUndo: (Long) -> Unit,
    contentPadding: PaddingValues,
) {
    val s = Lang.s
    val pager = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val titles = listOf(s.tabJournal, s.tabCalendar)

    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        TabRow(selectedTabIndex = pager.currentPage) {
            titles.forEachIndexed { index, title ->
                Tab(
                    selected = pager.currentPage == index,
                    onClick = { scope.launch { pager.animateScrollToPage(index) } },
                    text = { Text(title, maxLines = 1, softWrap = false) },
                )
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> JournalTab(adherence, journal, onSelectDay, onUndo, contentPadding)
                else -> HeatmapTab(
                    heatmap,
                    onMonthShift,
                    onSelectDay,
                    goToJournal = { scope.launch { pager.animateScrollToPage(0) } },
                    contentPadding,
                )
            }
        }
    }
}

// ---------- Журнал ----------

@Composable
private fun JournalTab(
    adherence: AdherenceState,
    state: JournalState,
    onSelectDay: (Long) -> Unit,
    onUndo: (Long) -> Unit,
    contentPadding: PaddingValues,
) {
    // Фильтры журнала: приёмы, еда и сон можно скрывать по отдельности.
    var showDoses by rememberSaveable { mutableStateOf(true) }
    var showMeals by rememberSaveable { mutableStateOf(true) }
    var showSleep by rememberSaveable { mutableStateOf(true) }

    val events = remember(state, showDoses, showMeals, showSleep) {
        buildList {
            if (showDoses) state.doses.forEach { add(JournalEvent.Intake(it)) }
            if (showSleep) {
                state.wakeAt?.let { add(JournalEvent.Mark(it, MarkKind.WAKE)) }
                state.bedAt?.let { add(JournalEvent.Mark(it, MarkKind.BED)) }
            }
            if (showMeals) state.meals.forEach { add(JournalEvent.Mark(it, MarkKind.MEAL)) }
        }.sortedByDescending { it.at }
    }

    Column(Modifier.fillMaxSize()) {
        WeekStrip(selected = state.day, onSelectDay = onSelectDay)
        // Фильтры прячем под иконку: лента дня важнее, чем ряд чекбоксов.
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterEnd) {
            var filterMenu by remember { mutableStateOf(false) }
            val allShown = showDoses && showMeals && showSleep
            IconButton(onClick = { filterMenu = true }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = Lang.s.journalFilters,
                    tint = if (allShown) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                FilterMenuItem(Lang.s.filterDoses, showDoses) { showDoses = it }
                FilterMenuItem(Lang.s.filterMeals, showMeals) { showMeals = it }
                FilterMenuItem(Lang.s.filterSleep, showSleep) { showSleep = it }
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "adherence") {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Lang.s.streakTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(Lang.s.streakDays(adherence.streak), fontWeight = FontWeight.SemiBold)
                    }
                    if (adherence.perMed.isNotEmpty()) {
                        Text(Lang.s.adherenceTitle, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        adherence.perMed.forEach { (medName, percent) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(medName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
                                Text("" + percent + "%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
            if (events.isEmpty()) {
                item(key = "empty") {
                    Text(
                        Lang.s.noIntakes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            // Всё вперемешку, но по времени: видно, что за чем шло в этот день.
            items(events, key = { it.key }) { event ->
                when (event) {
                    is JournalEvent.Intake -> DoseRow(event.dose, onUndo)
                    is JournalEvent.Mark -> MarkRow(event)
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(selected: Long, onSelectDay: (Long) -> Unit) {
    val selectedDate = LocalDate.ofEpochDay(selected)
    val weekStart = selectedDate.with(DayOfWeek.MONDAY)
    val todayDay = today()

    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onSelectDay(selected - 7) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = Lang.s.weekPrev)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
            (0..6).forEach { i ->
                val date = weekStart.plusDays(i.toLong())
                val day = date.toEpochDay()
                val isSelected = day == selected
                val isToday = day == todayDay
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onSelectDay(day) }
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Lang.s.locale),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimary
                            isToday -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
        IconButton(onClick = { onSelectDay(selected + 7) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = Lang.s.weekNext)
        }
    }
}

/** Что показывает журнал дня: приём таблетки или отметка (подъём, отход ко сну, еда). */
private enum class MarkKind { WAKE, BED, MEAL }

private sealed class JournalEvent(val at: Long, val key: String) {
    class Intake(val dose: Dose) : JournalEvent(dose.takenAt ?: dose.plannedAt, "dose-" + dose.id)
    class Mark(at: Long, val kind: MarkKind) : JournalEvent(at, "mark-" + kind.name + "-" + at)
}

/** Отметка дня строкой: иконка, что случилось и во сколько. */
@Composable
private fun MarkRow(event: JournalEvent.Mark) {
    val s = Lang.s
    val (icon, label) = when (event.kind) {
        MarkKind.WAKE -> Icons.Default.WbSunny to s.eventWokeUp
        MarkKind.BED -> Icons.Default.Bedtime to s.eventBed
        MarkKind.MEAL -> Icons.Default.Restaurant to s.eventMeal
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(formatClock(event.at), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Пункт меню фильтров: чекбокс с подписью. */
@Composable
private fun FilterMenuItem(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checked, onCheckedChange = null)
                Spacer(Modifier.width(8.dp))
                Text(label, maxLines = 1, softWrap = false)
            }
        },
        onClick = { onChange(!checked) },
    )
}

@Composable
private fun DoseRow(dose: Dose, onUndo: (Long) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(10.dp).height(10.dp).background(
                    when (dose.status) {
                        DoseStatus.TAKEN -> Color(0xFF4CAF50)
                        DoseStatus.SKIPPED -> Color(0xFFE53935)
                        DoseStatus.PENDING -> Color(0xFFFFC107)
                    },
                    CircleShape,
                ),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(dose.medNameSnapshot.ifBlank { Lang.s.pillFab }, fontWeight = FontWeight.SemiBold)
                val when0 = dose.takenAt ?: dose.plannedAt
                val label = when (dose.status) {
                    DoseStatus.TAKEN -> Lang.s.takenAt(formatClock(when0))
                    DoseStatus.SKIPPED -> Lang.s.skippedAt(formatClock(when0))
                    DoseStatus.PENDING -> Lang.s.plannedAt(formatClock(dose.plannedAt))
                }
                Text(
                    label + " · " + Lang.s.planLabel(formatClock(dose.plannedAt)) + " · " + formatAmount(dose.amount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (dose.status != DoseStatus.PENDING) {
                TextButton(onClick = { onUndo(dose.id) }) { Text(Lang.s.undo, maxLines = 1, softWrap = false) }
            }
        }
    }
}

// ---------- Тепловая карта ----------

@Composable
private fun HeatmapTab(
    state: HeatmapState,
    onMonthShift: (Long) -> Unit,
    onSelectDay: (Long) -> Unit,
    goToJournal: () -> Unit,
    contentPadding: PaddingValues,
) {
    val start = state.monthStart
    val daysInMonth = start.lengthOfMonth()
    val firstCellOffset = start.dayOfWeek.value - 1
    val todayDay = today()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonthShift(-1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = Lang.s.weekPrev)
            }
            Text(
                Lang.s.monthNames[start.monthValue - 1] + " " + start.year,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onMonthShift(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = Lang.s.weekNext)
            }
            val thisMonth = LocalDate.now().withDayOfMonth(1)
            if (start != thisMonth) {
                IconButton(onClick = {
                    onMonthShift(ChronoUnit.MONTHS.between(start, thisMonth))
                    onSelectDay(todayDay)
                }) {
                    Icon(Icons.Default.Today, contentDescription = Lang.s.toToday)
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            (1..7).forEach { d ->
                Text(
                    DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, Lang.s.locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val totalCells = firstCellOffset + daysInMonth
        val rows = (totalCells + 6) / 7
        (0 until rows).forEach { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0..6).forEach { c ->
                    val dayOfMonth = r * 7 + c - firstCellOffset + 1
                    if (dayOfMonth in 1..daysInMonth) {
                        val day = start.plusDays((dayOfMonth - 1).toLong()).toEpochDay()
                        HeatCell(
                            dayOfMonth = dayOfMonth,
                            heat = state.days[day],
                            isToday = day == todayDay,
                            isFuture = day > todayDay,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onSelectDay(day)
                                goToJournal()
                            },
                        )
                    } else {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(Lang.s.heatLegend, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(contentPadding.calculateBottomPadding()))
    }
}

@Composable
private fun HeatCell(
    dayOfMonth: Int,
    heat: DayHeat?,
    isToday: Boolean,
    isFuture: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val hasData = !isFuture && heat != null && heat.planned > 0
    val ratio = if (hasData) heat!!.taken.toFloat() / heat.planned else 0f
    // Зелёный — день закрыт полностью, оранжевый — были пропуски, красный — не выпито ничего.
    val background = when {
        !hasData -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ratio == 0f -> Color(0xFFE53935).copy(alpha = 0.55f)
        ratio >= 1f -> Color(0xFF2E7D32).copy(alpha = 0.85f)
        else -> Color(0xFFEF6C00).copy(alpha = 0.35f + 0.45f * ratio)
    }
    val textColor = when {
        !hasData -> MaterialTheme.colorScheme.onSurface
        ratio >= 0.5f || ratio == 0f -> Color.White
        else -> Color(0xFF1B1B1B)
    }
    val cellModifier = modifier
        .aspectRatio(1f)
        .background(background, RoundedCornerShape(8.dp))
        .then(if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier)
        .clickable { onClick() }

    Box(cellModifier, contentAlignment = Alignment.Center) {
        Text(
            dayOfMonth.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
        )
    }
}
