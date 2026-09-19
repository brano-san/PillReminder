@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package tech.unispace.pillreminder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import tech.unispace.pillreminder.data.MED_FORMS
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditOff
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tech.unispace.pillreminder.alarm.formatAmount
import tech.unispace.pillreminder.data.Dose
import tech.unispace.pillreminder.data.DoseStatus
import tech.unispace.pillreminder.data.isMissed
import tech.unispace.pillreminder.data.JournalAction
import tech.unispace.pillreminder.data.journalActions
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
    /** Отметить приём прошлого дня задним числом: время — плановое. */
    onTakeAt: (doseId: Long, at: Long) -> Unit,
    onSkip: (Long) -> Unit,
    /** Ошибочная отметка «Еда» убирается кнопкой в строке журнала. */
    onDeleteMeal: (Long) -> Unit,
    /** Вернуть ошибочно убранную отметку «Еда». */
    onRestoreMeal: (Long) -> Unit,
    contentPadding: PaddingValues,
) {
    val s = Lang.s
    val pager = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val titles = listOf(s.tabJournal, s.tabCalendar)
    val snackbars = remember { SnackbarHostState() }
    // Отметку «Еда» можно вернуть: она открывает приёмы «после еды», и ошибочное удаление сдвигает день.
    val deleteMeal: (Long) -> Unit = { at ->
        onDeleteMeal(at)
        scope.launch {
            snackbars.currentSnackbarData?.dismiss()
            if (snackbars.showSnackbar(s.mealDeleted, actionLabel = s.undo) == SnackbarResult.ActionPerformed) onRestoreMeal(at)
        }
    }

    Box(Modifier.fillMaxSize()) {
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
                0 -> JournalTab(adherence, journal, onSelectDay, onUndo, onTakeAt, onSkip, deleteMeal, contentPadding)
                else -> HeatmapTab(
                    heatmap,
                    journal,
                    onMonthShift,
                    onSelectDay,
                    goToJournal = { scope.launch { pager.animateScrollToPage(0) } },
                    contentPadding,
                )
            }
        }
    }
        SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter).padding(bottom = contentPadding.calculateBottomPadding() + 16.dp))
    }
}

// ---------- Журнал ----------

@Composable
private fun JournalTab(
    adherence: AdherenceState,
    state: JournalState,
    onSelectDay: (Long) -> Unit,
    onUndo: (Long) -> Unit,
    onTakeAt: (Long, Long) -> Unit,
    onSkip: (Long) -> Unit,
    onDeleteMeal: (Long) -> Unit,
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

    // «Прошлый день» — по дню цикла: после полуночи идущий день ещё не история, и прятать у него
    // кнопки нельзя. Именно ради этого в приложении плавающий день.
    val pastDay = state.day < state.cycleDay
    // Правка прошлого дня — намеренно за отдельной кнопкой и подтверждением: история должна оставаться историей.
    // Сбрасывается при смене дня.
    var editPast by remember(state.day) { mutableStateOf(false) }
    var confirmEdit by remember { mutableStateOf(false) }
    if (confirmEdit) {
        AlertDialog(
            onDismissRequest = { confirmEdit = false },
            title = { Text(Lang.s.editHistoryTitle) },
            text = { Text(Lang.s.editHistoryBody) },
            confirmButton = { TextButton(onClick = { confirmEdit = false; editPast = true }) { Text(Lang.s.editHistoryConfirm) } },
            dismissButton = { TextButton(onClick = { confirmEdit = false }) { Text(Lang.s.cancel) } },
        )
    }

    Column(Modifier.fillMaxSize()) {
        // Какой день открыт: в ленте только буквы и числа, месяц и «сегодня/вчера» были не видны.
        Text(
            formatDay(state.day),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
        )
        WeekStrip(selected = state.day, onSelectDay = onSelectDay)
        // Фильтры и правка истории — под иконками справа: лента дня важнее, чем ряд чекбоксов.
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (pastDay && state.doses.isNotEmpty()) {
                IconButton(onClick = { if (!editPast) confirmEdit = true else editPast = false }) {
                    Icon(
                        if (editPast) Icons.Default.EditOff else Icons.Default.Edit,
                        contentDescription = Lang.s.editHistoryBtn,
                        tint = if (editPast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Box {
                var filterMenu by remember { mutableStateOf(false) }
                val allShown = showDoses && showMeals && showSleep
                IconButton(onClick = { filterMenu = true }) {
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
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "adherence") {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Серия хвалит за успех; ноль — не «неудача», а нейтральное приглашение начать.
                    if (adherence.streak > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                Lang.s.streakTitle,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            Text("🔥 " + Lang.s.streakDays(adherence.streak), fontWeight = FontWeight.SemiBold)
                        }
                    } else if (!pastDay) {
                        // «Сегодня отличный день…» — только про сегодня; на прошлом дне фраза была бы неправдой.
                        Text(Lang.s.streakStartToday, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        // Скрытое фильтром — не «данных нет»: раньше выключенная галочка выглядела как пустой день.
                        if (!(showDoses && showMeals && showSleep)) Lang.s.hiddenByFilter else Lang.s.noIntakes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            // Всё вперемешку, но по времени: видно, что за чем шло в этот день.
            items(events, key = { it.key }) { event ->
                when (event) {
                    is JournalEvent.Intake -> DoseRow(
                        dose = event.dose,
                        form = state.formById[event.dose.medId] ?: MED_FORMS.first(),
                        pastDay = pastDay,
                        // Кнопки у прошлого дня — только после явного «Изменить историю».
                        editable = !pastDay || editPast,
                        onUndo = onUndo,
                        onTakeAt = onTakeAt,
                        onSkip = onSkip,
                    )
                    is JournalEvent.Mark -> MarkRow(event, onDeleteMeal)
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

    // Семь дней делят ширину поровну (weight), подписи не переносятся; стрелки и «к сегодня» — компактные 36 dp,
    // иначе на узком экране воскресенье сжималось в столбик из букв.
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onSelectDay(selected - 7) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = Lang.s.weekPrev)
        }
        Row(Modifier.weight(1f)) {
            (0..6).forEach { i ->
                val date = weekStart.plusDays(i.toLong())
                val day = date.toEpochDay()
                val isSelected = day == selected
                val isToday = day == todayDay
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 1.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onSelectDay(day) }
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Lang.s.locale),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
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
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        IconButton(onClick = { onSelectDay(selected + 7) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = Lang.s.weekNext)
        }
        // «К сегодня» — как на календаре: после пары недель назад стрелками возвращаться долго.
        if (selected != todayDay) {
            IconButton(onClick = { onSelectDay(todayDay) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Today, contentDescription = Lang.s.toToday, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Что показывает журнал дня: приём таблетки или отметка (подъём, отход ко сну, еда). */
private enum class MarkKind { WAKE, BED, MEAL }

private sealed class JournalEvent(val at: Long, val key: String) {
    class Intake(val dose: Dose) : JournalEvent(dose.takenAt ?: dose.plannedAt, "dose-" + dose.id)
    class Mark(at: Long, val kind: MarkKind) : JournalEvent(at, "mark-" + kind.name + "-" + at)
}

/** Отметка дня строкой: иконка, что случилось и во сколько. Еду можно убрать долгим нажатием. */
@Composable
private fun MarkRow(event: JournalEvent.Mark, onDeleteMeal: (Long) -> Unit) {
    val s = Lang.s
    val (icon, label) = when (event.kind) {
        MarkKind.WAKE -> Icons.Default.WbSunny to s.eventWokeUp
        MarkKind.BED -> Icons.Default.Bedtime to s.eventBed
        MarkKind.MEAL -> Icons.Default.Restaurant to s.eventMeal
    }
    // Ошибочная «Еда» открывает приём «после еды» — её должно быть можно убрать.
    var confirm by remember { mutableStateOf(false) }
    if (confirm) {
        ConfirmDeleteDialog(title = s.eventMeal, onConfirm = { onDeleteMeal(event.at) }, onDismiss = { confirm = false })
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(formatClock(event.at), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Явная кнопка вместо единственного долгого нажатия: о нём нигде не сказано,
            // а ошибочная «Еда» открывает приёмы «после еды» и сдвигает их план.
            if (event.kind == MarkKind.MEAL) {
                IconButton(onClick = { confirm = true }) {
                    Icon(Icons.Default.Close, contentDescription = s.delete, modifier = Modifier.size(18.dp))
                }
            }
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

/**
 * Приём в журнале. У прошлого дня ожидающий приём (например, после «Вернуть») можно отметить
 * задним числом — иначе он навсегда жёлтый, а «Вернуть» ведёт в тупик.
 */
@Composable
private fun DoseRow(
    dose: Dose,
    form: String,
    pastDay: Boolean,
    /** Показывать кнопки: сегодня — всегда, прошлый день — только после «Изменить историю». */
    editable: Boolean,
    onUndo: (Long) -> Unit,
    onTakeAt: (Long, Long) -> Unit,
    onSkip: (Long) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        // Текст — во всю ширину, кнопки — своей строкой под ним: в одном ряду с двумя кнопками
        // «Запланировано на 07:07» рвалось посреди слова.
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.width(10.dp).height(10.dp).background(
                        when (dose.status) {
                            // Те же три состояния, что на карточке и схеме дня: впереди — нейтральный,
                            // пора — янтарный, просрочен больше двух часов — красный.
                            DoseStatus.TAKEN -> Color(0xFF4CAF50)
                            DoseStatus.SKIPPED -> MaterialTheme.colorScheme.outlineVariant
                            DoseStatus.PENDING -> when {
                                isMissed(dose, System.currentTimeMillis()) -> Color(0xFFE53935)
                                dose.plannedAt <= System.currentTimeMillis() -> Color(0xFFE0A100)
                                else -> MaterialTheme.colorScheme.outlineVariant
                            }
                        },
                        CircleShape,
                    ),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    MarqueeText(dose.medNameSnapshot.ifBlank { Lang.s.pillFab }, fontWeight = FontWeight.SemiBold)
                    val when0 = dose.takenAt ?: dose.plannedAt
                    val label = when (dose.status) {
                        DoseStatus.TAKEN -> Lang.s.takenAt(formatClock(when0))
                        DoseStatus.SKIPPED -> Lang.s.skippedAt(formatClock(when0))
                        DoseStatus.PENDING -> Lang.s.plannedAt(formatClock(dose.plannedAt))
                    }
                    Text(
                        // Слово «таблетки/капли» — по форме выпуска, как на карточке и в шторке.
                        // «план 07:07» печатаем только когда факт отличается от плана.
                        listOfNotNull(
                            label,
                            Lang.s.planLabel(formatClock(dose.plannedAt)).takeIf { dose.takenAt != null && dose.takenAt != dose.plannedAt },
                            formatAmount(dose.amount, form),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Кнопки — по состоянию приёма: выпитому не предлагаем «Выпито», пропущенному — «Пропустить».
            val actions = journalActions(dose.status, editable, pastDay)
            if (actions.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    actions.forEach { action ->
                        when (action) {
                            JournalAction.TAKE -> TextButton(onClick = { onTakeAt(dose.id, dose.plannedAt) }) {
                                Text(Lang.s.took, maxLines = 1, softWrap = false)
                            }
                            JournalAction.SKIP -> TextButton(onClick = { onSkip(dose.id) }) {
                                Text(Lang.s.skip, maxLines = 1, softWrap = false)
                            }
                            JournalAction.UNDO -> TextButton(onClick = { onUndo(dose.id) }) {
                                Text(Lang.s.undo, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Тепловая карта ----------

@Composable
private fun HeatmapTab(
    state: HeatmapState,
    /** Выбранный день — общий с журналом: превью под календарём показывает его события. */
    journal: JournalState,
    onMonthShift: (Long) -> Unit,
    onSelectDay: (Long) -> Unit,
    goToJournal: () -> Unit,
    contentPadding: PaddingValues,
) {
    val s = Lang.s
    val start = state.monthStart
    val daysInMonth = start.lengthOfMonth()
    val firstCellOffset = start.dayOfWeek.value - 1
    val todayDay = today()

    // Прокрутка: в ландшафте и на коротких экранах нижние ряды и превью иначе обрезаются.
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonthShift(-1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = s.weekPrev)
            }
            Text(
                s.monthNames[start.monthValue - 1] + " " + start.year,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onMonthShift(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = s.weekNext)
            }
            val thisMonth = LocalDate.now().withDayOfMonth(1)
            if (start != thisMonth) {
                IconButton(onClick = {
                    onMonthShift(ChronoUnit.MONTHS.between(start, thisMonth))
                    onSelectDay(todayDay)
                }) {
                    Icon(Icons.Default.Today, contentDescription = s.toToday)
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            (1..7).forEach { d ->
                Text(
                    DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, s.locale),
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
                            isSelected = day == journal.day,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectDay(day) },
                        )
                    } else {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        // Легенда маркерами, а не абзацем: её читают глазами за секунду.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LegendMarker(heatColor(1f), s.legendAll)
            LegendMarker(heatColor(0.5f), s.legendPartial)
            LegendMarker(heatColor(0f), s.legendMissed)
            LegendMarker(Color.Transparent, s.legendToday, border = MaterialTheme.colorScheme.primary)
            LegendMarker(MaterialTheme.colorScheme.surfaceVariant, s.legendNone)
            // Точки под числом и бледные дни объясняем тут же: раньше их значение надо было угадывать.
            LegendMarker(Color.White, s.legendDotTaken)
            LegendMarker(Color.White.copy(alpha = 0.35f), s.legendDotMissed)
            LegendMarker(Color.Transparent, s.legendDotAhead, border = MaterialTheme.colorScheme.onSurfaceVariant)
            LegendMarker(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), s.legendFuture)
        }
        // Превью выбранного дня — здесь же, без перехода в журнал: «почему 11-е оранжевое» видно сразу.
        DayPreview(journal, goToJournal)
        Spacer(Modifier.height(contentPadding.calculateBottomPadding() + 16.dp))
    }
}

@Composable
private fun LegendMarker(color: Color, label: String, border: Color? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .background(color, RoundedCornerShape(3.dp))
                .then(if (border != null) Modifier.border(2.dp, border, RoundedCornerShape(3.dp)) else Modifier),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
    }
}

/** Доля выпитого → ступень 0…5 (по 20 %): 0 % — красный, 100 % — зелёный. Чистая функция, с тестом. */
fun heatStep(ratio: Float): Int = (ratio.coerceIn(0f, 1f) * 5).toInt()

/**
 * Цвет клетки по ступени: оттенок от красного (0°) к зелёному (120°) через жёлтый.
 * Яркость 0,45 выбрана по контрасту: на прежних светлых жёлтом и зелёном белая цифра давала 2,2:1
 * при норме 4,5:1 — то есть чем лучше дисциплина, тем хуже читался календарь.
 */
fun heatColor(ratio: Float): Color {
    val step = heatStep(ratio)
    // Светлота растёт вместе с долей выпитого: на одинаковой яркости соседние ступени различались
    // только оттенком, и месяц читался ровным тёмным полем. Потолок — по контрасту белой цифры.
    return Color.hsv(hue = 24f * step, saturation = 0.75f, value = 0.33f + 0.028f * step)
}

/**
 * Цвет цифры на цветной клетке: белый контрастен только на тёмных оттенках, а зелёный «всё выпито»
 * светлый — на нём белая цифра почти не читалась. Порог — относительная яркость 0,5.
 */
fun heatTextColor(ratio: Float): Color = if (heatColor(ratio).luminance() > 0.5f) Color.Black else Color.White

/** Больше стольких приёмов в день точками не показать — вместо них «4/8». */
private const val HEAT_DOTS_MAX = 6

@Composable
private fun HeatCell(
    dayOfMonth: Int,
    heat: DayHeat?,
    isToday: Boolean,
    isFuture: Boolean,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val decided = heat != null && heat.planned > 0
    val ratio = if (decided) heat!!.taken.toFloat() / heat.planned else 0f
    // Заливка — функциональная и для сегодня: утренняя выпита, вечерняя ещё нет — видно по цвету и точкам.
    // «Сегодня» отличает рамка, а не заливка. Будущие дни — без плашки и полупрозрачные: они ещё не наступили.
    val background = when {
        isFuture -> Color.Transparent
        decided -> heatColor(ratio)
        else -> scheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val textColor = when {
        isFuture -> scheme.onSurfaceVariant
        decided -> heatTextColor(ratio)
        else -> scheme.onSurface
    }
    val border = when {
        isToday -> Modifier.border(2.5.dp, scheme.primary, RoundedCornerShape(8.dp))
        isSelected -> Modifier.border(1.5.dp, scheme.onSurface, RoundedCornerShape(8.dp))
        else -> Modifier
    }
    // Клетка озвучивается целиком: «12 марта, выпито 3 из 4», иначе TalkBack читает только число.
    val spoken = buildString {
        append(dayOfMonth)
        if (heat != null && heat.planned > 0) {
            append(", ")
            append(Lang.s.heatCellDesc(heat.taken, heat.planned))
        }
        if (isToday) append(", ").append(Lang.s.legendToday)
    }
    Box(
        modifier
            .heightIn(min = 44.dp)
            .aspectRatio(1f)
            .alpha(if (isFuture) 0.4f else 1f)
            .background(background, RoundedCornerShape(8.dp))
            .then(border)
            .clickable { onClick() }
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                dayOfMonth.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
            )
            val marks = heat?.marks.orEmpty()
            if (marks.isNotEmpty() && !isFuture) {
                Spacer(Modifier.height(2.dp))
                if (marks.size <= HEAT_DOTS_MAX) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        marks.forEach { mark ->
                            Box(
                                Modifier.size(4.dp).then(
                                    when (mark) {
                                        // Точки того же цвета, что цифра: на светлой зелёной клетке белые пропадали.
                                        HeatMark.TAKEN -> Modifier.background(textColor, CircleShape)
                                        HeatMark.MISSED -> Modifier.background(textColor.copy(alpha = 0.35f), CircleShape)
                                        HeatMark.PENDING -> Modifier.border(1.dp, if (decided) textColor else scheme.onSurfaceVariant, CircleShape)
                                    },
                                ),
                            )
                        }
                    }
                } else {
                    Text(
                        "" + marks.count { it == HeatMark.TAKEN } + "/" + marks.size,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/** Компактный список событий выбранного дня под календарём: приёмы, подъём, еда, сон — читать, не править. */
/**
 * Превью выбранного дня под календарём — та же схема дня, что на главном экране (`DayTimeline`),
 * а не список строк: один и тот же день должен выглядеть одинаково везде. Будущий день без
 * событий не показывается вовсе — «приёмов не было» про завтра звучало нелепо.
 */
@Composable
private fun DayPreview(journal: JournalState, goToJournal: () -> Unit) {
    val s = Lang.s
    val now = System.currentTimeMillis()
    val nodes = remember(journal, now / 60_000) {
        buildTimelineNodes(
            wakeAt = journal.wakeAt,
            bedAt = journal.bedAt,
            doses = journal.doses,
            formById = journal.formById,
            meals = journal.meals,
            now = now,
            doseInfoById = journal.doseInfoById,
        )
    }
    val isToday = journal.day == today()
    if (nodes.isEmpty() && journal.day > today()) return
    Card(Modifier.fillMaxWidth()) {
        // Кнопка «В журнал» ростом 48 dp раздувала шапку: отступ сверху казался больше боковых. Кнопка ниже, отступ меньше.
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDay(journal.day), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                TextButton(onClick = goToJournal, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(36.dp)) {
                    Text(s.openJournalBtn, maxLines = 1, softWrap = false)
                }
            }
            if (nodes.isEmpty()) {
                Text(s.noIntakes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                DayTimeline(nodes, now, Modifier.fillMaxWidth(), autoScroll = isToday)
            }
        }
    }
}
