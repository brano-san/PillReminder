@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package tech.unispace.pillreminder.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tech.unispace.pillreminder.alarm.trackerDisplayName
import tech.unispace.pillreminder.data.Dose
import tech.unispace.pillreminder.data.DoseStatus
import tech.unispace.pillreminder.data.bedtimeDropCount
import tech.unispace.pillreminder.data.EARLY_TAKE_THRESHOLD_MS
import tech.unispace.pillreminder.data.MEAL_WAIT_MAX_MS
import tech.unispace.pillreminder.data.MED_FORMS
import tech.unispace.pillreminder.data.Medication
import tech.unispace.pillreminder.data.OVERDUE_GRACE_MS
import tech.unispace.pillreminder.data.Settings
import tech.unispace.pillreminder.data.TrackerEntry
import tech.unispace.pillreminder.data.TrackerType
import tech.unispace.pillreminder.data.byClock
import tech.unispace.pillreminder.data.courseDaysLeft
import tech.unispace.pillreminder.data.courseEndDay
import tech.unispace.pillreminder.data.epochDayOf
import tech.unispace.pillreminder.data.fixedTimesList
import tech.unispace.pillreminder.data.today
import tech.unispace.pillreminder.data.weekdaysList
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Иконка формы выпуска — своя для каждой формы. */
fun formIcon(form: String): ImageVector = when (form) {
    "Таблетка", "Pill" -> Icons.Default.Medication
    "Инъекция", "Injection" -> Icons.Default.Vaccines
    "Раствор", "Solution" -> Icons.Default.Science
    "Капли", "Drops" -> Icons.Default.WaterDrop
    "Ингалятор", "Inhaler" -> Icons.Default.Air
    "Порошок", "Powder" -> Icons.Default.Grain
    "Свечи", "Suppository" -> Icons.Default.Healing
    else -> Icons.Default.MedicalServices
}

/** Сколько дней до конца курса начинать показывать метку на карточке: «ещё 30 дней» решения не меняет. */
private const val COURSE_WARN_DAYS = 7

private val GREEN = Color(0xFF4CAF50)
private val AMBER = Color(0xFFE0A100)
private val RED = Color(0xFFE53935)

/** Янтарный для текста: сам AMBER на белом не читается, в тёмной теме — наоборот, нужен светлее. */
@Composable
private fun amberText(): Color = if (isSystemInDarkTheme()) Color(0xFFFFC65C) else Color(0xFF9A6700)

/** Сколько держать подсветку карточки после тапа по кружку на схеме дня. */
private const val HIGHLIGHT_MS = 2_500L

/** Сколько после планового времени карточка пишет «сейчас», а не «опоздание на N мин». */
const val DUE_TEXT_GRACE_MS = 5 * 60_000L

@Composable
fun HomeScreen(
    state: HomeState,
    trackerRows: List<TrackerRow>,
    contentPadding: PaddingValues,
    onWakeUp: () -> Unit,
    /** «Начать новый день» и ручной сброс: без записи сна, в отличие от [onWakeUp]. */
    onRestartDay: () -> Unit,
    onTake: (Long) -> Unit,
    /** «Принять сейчас»: колбэк получает id записи для «Вернуть». */
    onTakeNow: (medId: Long, onDone: (Long) -> Unit) -> Unit,
    /** Отмена «Принять сейчас» — запись удаляется целиком. */
    onDeleteIntake: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    /** «Отложить» с карточки: приём и минуты из настроек «Отложить». */
    onSnooze: (doseId: Long, minutes: Int) -> Unit,
    onEdit: (Long) -> Unit,
    onAdd: () -> Unit,
    onDelete: (Long) -> Unit,
    onUndo: (Long) -> Unit,
    /** «Выпить всё, что пора»: колбэк получает отмеченные id для «Вернуть». */
    onTakeAll: (onDone: (List<Long>) -> Unit) -> Unit,
    onReorder: (List<Long>) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTracker: (Long) -> Unit,
    onOpenTips: () -> Unit,
    onOpenTutorial: () -> Unit,
    onOpenReport: () -> Unit,
    onBedtime: () -> Unit,
    /** «Еда»: колбэк получает записанный момент, чтобы снекбар «Вернуть» знал, что убирать. */
    onMeal: ((Long) -> Unit) -> Unit,
    /** Название только что сохранённой таблетки; экран показывает снекбар и зовёт [onSavedShown]. */
    savedMedName: String? = null,
    onSavedShown: () -> Unit = {},
    onDeleteMeal: (Long) -> Unit,
    onUndoBedtime: () -> Unit,
    sleepToRate: TrackerEntry?,
    onRateSleep: (TrackerEntry, Int, Int) -> Unit,
    onDismissSleepRating: () -> Unit,
    onDuplicate: (Long) -> Unit,
    onQuickEntry: (TrackerEntry) -> Unit,
) {
    val s = Lang.s
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val compact = settings.homeCompact
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    fun confirmWithUndo(message: String, undo: () -> Unit) {
        scope.launch {
            snackbars.currentSnackbarData?.dismiss()
            val result = snackbars.showSnackbar(message = message, actionLabel = s.undo, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }

    var deleteTarget by remember { mutableStateOf<MedRow?>(null) }
    deleteTarget?.let { row ->
        ConfirmDeleteDialog(title = row.med.name, onConfirm = { onDelete(row.med.id) }, onDismiss = { deleteTarget = null })
    }
    // Порядок карточек — стрелками в диалоге: перетаскивание в ленте конфликтовало с прокруткой и требовало ручку на каждой карточке.
    var reorderOpen by remember { mutableStateOf(false) }
    if (reorderOpen) {
        ReorderDialog(
            meds = state.rows.map { it.med },
            onDone = { ids ->
                onReorder(ids)
                reorderOpen = false
            },
            onDismiss = { reorderOpen = false },
        )
    }
    // Долгое нажатие предлагает выбор: копия схемы нужна чаще, чем удаление; в компактном режиме
    // здесь же живут «Пропустить» и «Отложить» — на карточке для них места нет.
    var actionTarget by remember { mutableStateOf<MedRow?>(null) }
    actionTarget?.let { row ->
        val next = row.nextDose
        val mealTimedOut = next != null && row.waitsMeal && state.now - next.plannedAt >= MEAL_WAIT_MAX_MS
        val due = next != null && !row.med.asNeeded && next.plannedAt <= state.now && (!row.waitsMeal || mealTimedOut)
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = { Text(row.med.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Без строки и кнопки «Изменить» у таблетки без приёма и без соседей тело было пустым:
                    // заголовок, а под ним сразу кнопки диалога.
                    Text(s.longPressBody, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(
                        onClick = {
                            actionTarget = null
                            onEdit(row.med.id)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(s.edit, maxLines = 1, softWrap = false) }
                    if (next != null && !row.med.asNeeded) {
                        OutlinedButton(
                            onClick = {
                                onSkip(next.id)
                                confirmWithUndo(s.snackSkipped(row.med.name)) { onUndo(next.id) }
                                actionTarget = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(s.skip, maxLines = 1, softWrap = false) }
                    }
                    if (compact && due) {
                        settings.snoozeOptions.forEach { minutes ->
                            OutlinedButton(
                                onClick = {
                                    onSnooze(next.id, minutes)
                                    actionTarget = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(s.snoozeFor(s.duration(minutes)), maxLines = 1, softWrap = false) }
                        }
                    }
                    if (state.rows.size > 1) {
                        OutlinedButton(
                            onClick = {
                                actionTarget = null
                                reorderOpen = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(s.reorderBtn, maxLines = 1, softWrap = false) }
                    }
                    TextButton(
                        onClick = {
                            deleteTarget = row
                            actionTarget = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(s.delete, maxLines = 1, softWrap = false) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onDuplicate(row.med.id)
                    confirmWithUndo(s.copyCreated) {}
                    actionTarget = null
                }) { Text(s.duplicateBtn) }
            },
            // «Отмена» там, где её ждёт палец; удаление — красной строкой в теле диалога.
            dismissButton = { TextButton(onClick = { actionTarget = null }) { Text(s.cancel) } },
        )
    }
    // Меньше четырёх часов сна — почти всегда ошибка нажатия, поэтому переспрашиваем.
    var shortSleep by remember { mutableStateOf<Long?>(null) }
    shortSleep?.let { slept ->
        AlertDialog(
            onDismissRequest = { shortSleep = null },
            title = { Text(s.sleepShortTitle) },
            text = { Text(s.sleepShortBody(s.duration((slept / 60_000L).toInt()))) },
            confirmButton = {
                TextButton(onClick = {
                    shortSleep = null
                    onWakeUp()
                }) { Text(s.sleepShortConfirm) }
            },
            dismissButton = { TextButton(onClick = { shortSleep = null }) { Text(s.cancel) } },
        )
    }
    fun wakeUpChecked() {
        val bedAt = settings.pendingSleepStart
        val slept = System.currentTimeMillis() - bedAt
        if (bedAt > 0 && slept < SHORT_SLEEP_MS) shortSleep = slept else onWakeUp()
    }

    // Сон, собранный кнопками «Сон» → «Подъём»: просим оценить сразу.
    sleepToRate?.let { entry ->
        var sleepRating by remember(entry.id) { mutableIntStateOf(4) }
        var wakeRating by remember(entry.id) { mutableIntStateOf(4) }
        AlertDialog(
            onDismissRequest = onDismissSleepRating,
            title = { Text(s.sleepRateTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    entry.sleepStart?.let { start ->
                        Text(
                            formatClock(start) + " — " + formatClock(entry.sleepEnd ?: entry.atMillis) +
                                " · " + s.duration(((entry.atMillis - start) / 60_000L).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(s.sleepQualityLabel, style = MaterialTheme.typography.titleSmall)
                    EmojiRating(sleepRating) { sleepRating = it }
                    Text(s.sleepWakeQuality, style = MaterialTheme.typography.titleSmall)
                    EmojiRating(wakeRating) { wakeRating = it }
                }
            },
            confirmButton = { TextButton(onClick = { onRateSleep(entry, sleepRating, wakeRating) }) { Text(s.save) } },
            dismissButton = { TextButton(onClick = onDismissSleepRating) { Text(s.later) } },
        )
    }

    val minuteKey = state.now / 60_000
    val deliveryFine = remember(minuteKey) { deliveryOk(context) }
    val showWakeButton = state.wokeUpAt == null && state.loaded

    // Схема дня: подъём → таблетки → еда → сон. Пересобирается раз в минуту, а не раз в секунду.
    // Ключи сравниваются по содержимому: rows пересоздаются каждую секунду, а карты форм/дозировок и набор ждущих — нет.
    val formById = state.rows.associate { it.med.id to it.med.form }
    val doseInfoById = state.rows.associate { it.med.id to it.med.doseInfo }
    val waitingIds = state.rows.filter { it.waitsMeal }.mapNotNull { it.nextDose?.id }.toSet()
    val nodes = if (state.wokeUpAt != null && settings.showDayTimeline) {
        remember(state.doses, state.meals, state.wokeUpAt, state.bedAt, formById, doseInfoById, waitingIds, minuteKey) {
            buildTimelineNodes(
                wakeAt = state.wokeUpAt,
                bedAt = state.bedAt,
                doses = state.doses,
                formById = formById,
                meals = state.meals,
                now = state.now,
                waitingIds = waitingIds,
                doseInfoById = doseInfoById,
            )
        }
    } else {
        emptyList()
    }
    val showTimeline = nodes.size > 1

    // Число считает планировщик тем же правилом, каким отмечает: по приёмам, а не по карточкам,
    // и без давно просроченных — иначе кнопка «(1)» молча отмечала два приёма.
    val dueCount = state.dueNowCount
    val showTakeAll = dueCount >= 2
    val showEmpty = state.rows.isEmpty() && state.loaded

    // Сколько неотмеченных приёмов снимет «Сон»: «по часам» живут по своим временам и не снимаются.
    val byClockIds = state.rows.filter { it.med.byClock }.map { it.med.id }.toSet()
    val bedtimeDrop = bedtimeDropCount(state.doses, byClockIds)
    var confirmBedtime by remember { mutableStateOf(false) }
    if (confirmBedtime) {
        AlertDialog(
            onDismissRequest = { confirmBedtime = false },
            title = { Text(s.bedtimeConfirmTitle) },
            text = { Text(if (bedtimeDrop > 0) s.bedtimeConfirmBody(bedtimeDrop) else s.bedtimeConfirmBodyEmpty) },
            confirmButton = {
                TextButton(onClick = {
                    confirmBedtime = false
                    onBedtime()
                    confirmWithUndo(s.bedtimeSaved(formatClock(System.currentTimeMillis()))) { onUndoBedtime() }
                }) { Text(s.bedtimeConfirmBtn) }
            },
            dismissButton = { TextButton(onClick = { confirmBedtime = false }) { Text(s.cancel) } },
        )
    }

    // Факт сохранения подтверждается: карточка на главном могла выглядеть так же, как до правки,
    // и человек не понимал, применилось ли.
    LaunchedEffect(savedMedName) {
        val saved = savedMedName ?: return@LaunchedEffect
        confirmWithUndo(s.savedMed(saved)) {}
        onSavedShown()
    }

    // Тап по кружку на схеме дня: прокрутить к карточке и подсветить её на пару секунд.
    var highlightMedId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(highlightMedId) {
        val id = highlightMedId ?: return@LaunchedEffect
        val index = state.rows.indexOfFirst { it.med.id == id }
        if (index >= 0) {
            val before = (if (!deliveryFine) 1 else 0) + 1 + (if (showEmpty) 1 else 0) + (if (showTakeAll) 1 else 0)
            listState.animateScrollToItem(before + index)
        }
        delay(HIGHLIGHT_MS)
        highlightMedId = null
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!deliveryFine) {
                item(key = "delivery-warning") {
                    Card(
                        Modifier.fillMaxWidth().clickable { onOpenSettings() },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.deliveryWarnTitle, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                                Text(s.deliveryWarnBody, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }

            // Карточка дня и схема дня — одна карточка: раньше «Проснулись в 08:00» и первый узел
            // «Подъём 08:00» шли подряд двумя карточками и съедали пол-экрана до первого действия.
            item(key = "wake") {
                WakeCard(state, onRestartDay) {
                    if (showTimeline) {
                        Spacer(Modifier.height(10.dp))
                        DayTimeline(nodes, state.now, Modifier.fillMaxWidth(), onPillTap = { highlightMedId = it })
                    }
                }
            }

            if (showEmpty) {
                item(key = "empty") { EmptyHint() }
            }

            if (showTakeAll) {
                item(key = "take-all") {
                    Button(
                        onClick = {
                            onTakeAll { ids ->
                                if (ids.isNotEmpty()) confirmWithUndo(s.snackTakenAll(ids.size)) { ids.forEach(onUndo) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(s.takeAllBtn(dueCount), maxLines = 1, softWrap = false)
                    }
                }
            }

            items(state.rows, key = { it.med.id }) { row ->
                MedCard(
                    row = row,
                    now = state.now,
                    // Расписание «по часам» живёт без кнопки «Подъём»; уже запланированный приём — тоже
                    // доказательство, что день размечен (цикл мог истечь по 18-часовому лимиту).
                    awake = state.wokeUpAt != null || row.med.byClock || row.nextDose != null,
                    compact = compact,
                    highlighted = highlightMedId == row.med.id,
                    snoozeOptions = settings.snoozeOptions,
                    onTake = { doseId ->
                        onTake(doseId)
                        confirmWithUndo(s.snackTaken(row.med.name)) { onUndo(doseId) }
                    },
                    onTakeNow = { medId ->
                        onTakeNow(medId) { doseId -> confirmWithUndo(s.snackTaken(row.med.name)) { onDeleteIntake(doseId) } }
                    },
                    onSkip = { doseId ->
                        onSkip(doseId)
                        confirmWithUndo(s.snackSkipped(row.med.name)) { onUndo(doseId) }
                    },
                    onSnooze = onSnooze,
                    onEdit = onEdit,
                    onLongPress = { actionTarget = it },
                    cardModifier = Modifier.animateItem(),
                )
            }

            items(trackerRows, key = { "tracker-" + it.tracker.id }) { row ->
                TrackerReminderCard(row, state.now, onOpenTracker) { entry ->
                    // Подтверждение — снекбаром экрана: внутри карточки его показать нечем.
                    onQuickEntry(entry)
                    confirmWithUndo(s.savedShort) {}
                }
            }

            // Редкие действия — в самом низу, под таблетками и трекерами: они не про сегодняшний день.
            if (settings.showHomeActions) {
                item(key = "home-actions") {
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            HomeActionButton(Icons.Default.Description, s.reportBtn, Modifier.weight(1f), onOpenReport)
                            HomeActionButton(Icons.Default.Lightbulb, s.tipsButton, Modifier.weight(1f), onOpenTips)
                            HomeActionButton(Icons.Default.School, s.tutorialBtn, Modifier.weight(1f), onOpenTutorial)
                        }
                    }
                }
            }
        }

        // Нижняя строка: «Подъём» слева (пока день не начат) и «+ Таблетка» справа.
        // До загрузки состояния не рисуем: иначе на долю секунды мигают «Сон»/«Еда».
        if (state.loaded) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Один слот на весь суточный цикл: утром это «Подъём», днём — «Сон».
                if (showWakeButton) {
                    Button(
                        onClick = { wakeUpChecked() },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) {
                        Icon(Icons.Default.WbSunny, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(s.iWokeUp, style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
                    }
                } else {
                    val bedtimeAt = remember(minuteKey) { settings.pendingSleepStart }
                    FilledTonalButton(
                        // «Сон» закрывает день, а это необратимо — спрашиваем всегда и говорим, сколько
                        // приёмов будет снято. Вернуться можно снекбаром сразу после.
                        onClick = { confirmBedtime = true },
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) {
                        Icon(Icons.Default.Bedtime, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (bedtimeAt > 0) s.bedtimeShort(formatClock(bedtimeAt)) else s.bedtimeBtn,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // «Еда» рядом: обе кнопки относятся к текущему дню.
                    FilledTonalButton(
                        onClick = {
                            // Ошибочная «Еда» открывает приёмы «после еды» и двигает их план, поэтому
                            // подтверждаем снекбаром с «Вернуть», а не системным всплывающим сообщением.
                            onMeal { at -> confirmWithUndo(s.mealSaved(formatClock(at))) { onDeleteMeal(at) } }
                        },
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) {
                        Icon(Icons.Default.Restaurant, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            s.mealBtn,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(s.pillFab, maxLines = 1, softWrap = false) },
                )
            }
        }

        SnackbarHost(
            hostState = snackbars,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = contentPadding.calculateBottomPadding() + 84.dp),
        )
    }
}

/** Порядок таблеток стрелками: крайние стрелки неактивны, результат уходит одним списком id. */
@Composable
private fun ReorderDialog(meds: List<Medication>, onDone: (List<Long>) -> Unit, onDismiss: () -> Unit) {
    val s = Lang.s
    var order by remember { mutableStateOf(meds) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.reorderTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                order.forEachIndexed { i, med ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(formIcon(med.form), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        MarqueeText(med.name, modifier = Modifier.weight(1f))
                        IconButton(
                            enabled = i > 0,
                            onClick = { order = order.toMutableList().also { it.add(i - 1, it.removeAt(i)) } },
                        ) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = s.moveUp) }
                        IconButton(
                            enabled = i < order.lastIndex,
                            onClick = { order = order.toMutableList().also { it.add(i + 1, it.removeAt(i)) } },
                        ) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = s.moveDown) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(order.map { it.id }) }) { Text(s.done) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
}

@Composable
private fun WakeCard(state: HomeState, onRestartDay: () -> Unit, extra: @Composable ColumnScope.() -> Unit = {}) {
    val s = Lang.s
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    // Новый день сдвигает все приёмы, поэтому спрашиваем подтверждение.
    var confirmNewDay by remember { mutableStateOf(false) }
    if (confirmNewDay) {
        AlertDialog(
            onDismissRequest = { confirmNewDay = false },
            title = { Text(s.newDayConfirmTitle) },
            text = { Text(s.newDayConfirmBody) },
            confirmButton = { TextButton(onClick = { confirmNewDay = false; onRestartDay() }) { Text(s.newDayBtn) } },
            dismissButton = { TextButton(onClick = { confirmNewDay = false }) { Text(s.cancel) } },
        )
    }
    var confirmShift by remember { mutableStateOf(false) }
    if (confirmShift) {
        AlertDialog(
            onDismissRequest = { confirmShift = false },
            title = { Text(s.resetDayTitle) },
            text = { Text(s.resetDayBody) },
            confirmButton = { TextButton(onClick = { confirmShift = false; onRestartDay() }) { Text(s.resetDayConfirm) } },
            dismissButton = { TextButton(onClick = { confirmShift = false }) { Text(s.cancel) } },
        )
    }
    // После «Сон» до подъёма — не «Доброе утро», а «Спокойной ночи».
    val sleepingSince = remember(state.now / 60_000, state.wokeUpAt) { if (state.wokeUpAt == null) settings.pendingSleepStart else 0L }

    // Ручной сброс дня спрятан под долгое нажатие: он нужен редко, а злоупотреблять им вредно.
    ElevatedCard(
        Modifier.fillMaxWidth().combinedClickable(
            onClick = {},
            onLongClick = { if (state.wokeUpAt != null) confirmShift = true },
        ),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (sleepingSince > 0) Icons.Default.Bedtime else Icons.Default.WbSunny,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    when {
                        state.wokeUpAt == null && sleepingSince > 0 -> {
                            Text(s.goodNight, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(s.sleepSince(formatClock(sleepingSince)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        state.wokeUpAt == null -> {
                            Text(s.goodMorning, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(s.wakeIntro, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        else -> {
                            Text(s.wokeAt(formatClock(state.wokeUpAt)), fontWeight = FontWeight.SemiBold)
                            // Подзаголовок отвечает на главный вопрос экрана: сколько пора сейчас и когда следующий,
                            // вместо бесполезного «День уже размечен».
                            val nextAt = state.rows.mapNotNull { it.nextDose }.filter { it.plannedAt > state.now }.minByOrNull { it.plannedAt }
                            Text(
                                when {
                                    state.dueNowCount > 0 -> s.dueNowLine(state.dueNowCount)
                                    nextAt != null -> s.nextIntakeLine(formatClock(nextAt.plannedAt))
                                    else -> s.allDoneForToday
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (state.wokeUpAt != null && state.allDone) {
                Spacer(Modifier.height(8.dp))
                Text(s.dayDoneHome, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Button(onClick = { confirmNewDay = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(s.newDayBtn, maxLines = 1, softWrap = false)
                }
            }
            // Схема дня рисуется здесь же, внутри карточки дня.
            extra()
        }
    }
}

@Composable
private fun TrackerReminderCard(
    row: TrackerRow,
    now: Long,
    onOpen: (Long) -> Unit,
    onQuickEntry: (TrackerEntry) -> Unit,
) {
    val s = Lang.s
    val last = row.entries.firstOrNull()
    val lastDay = last?.let { epochDayOf(it.atMillis) }
    val doneToday = lastDay == today()

    Card(Modifier.fillMaxWidth().clickable { onOpen(row.tracker.id) }) {
      Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(trackerIcon(row.tracker.type), contentDescription = null, tint = if (doneToday) GREEN else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(trackerDisplayName(row.tracker.type), fontWeight = FontWeight.SemiBold)
                // Без красного: «сегодня данных нет» — факт, а не тревога.
                Text(
                    when {
                        last != null && doneToday -> s.trackerDoneToday(formatClock(last.atMillis))
                        last != null -> s.trackerNotToday + ", " + s.lastEntryAgo(today() - lastDay!!)
                        else -> s.trackerNotToday + ", " + s.neverRecorded
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (doneToday) Icon(Icons.Default.Check, contentDescription = null, tint = GREEN)
        }
        // Кулдаун вместо «раз в день»: настроение и вес часто хочется поправить сразу.
        val quietFor = last != null && now - last.atMillis < QUICK_ENTRY_COOLDOWN_MS
        if (!quietFor) QuickTrackerEntry(row, onQuickEntry)
      }
    }
}

/** Кнопка ряда действий: иконка над подписью, ширина — по колонке. Высота 40 dp — минимум для пальца. */
@Composable
private fun HomeActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Короче этого сон считается случайным нажатием и требует подтверждения. */
const val SHORT_SLEEP_MS = 4 * 60 * 60_000L

/** Через сколько после записи снова предлагать быстрый ввод на карточке. */
const val QUICK_ENTRY_COOLDOWN_MS = 5 * 60_000L

/** Запись трекера в один тап прямо с главной: настроение — эмодзи, вес — шаг 0,1 кг. */
@Composable
private fun QuickTrackerEntry(row: TrackerRow, onQuickEntry: (TrackerEntry) -> Unit) {
    val s = Lang.s
    when (row.tracker.type) {
        TrackerType.MOOD -> {
            Text(s.quickMoodTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            EmojiRating(0) { value ->
                // Подтверждение показывает экран: снекбар живёт на уровне списка, а не внутри карточки.
                onQuickEntry(TrackerEntry(trackerId = row.tracker.id, atMillis = System.currentTimeMillis(), value = value.toDouble()))
            }
        }
        TrackerType.WEIGHT -> {
            var value by remember(row.entries.firstOrNull()?.id) {
                mutableStateOf(row.entries.firstOrNull()?.value ?: 70.0)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { value = (value - 0.1).coerceAtLeast(1.0) }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Text(s.weightStepMinus, maxLines = 1, softWrap = false)
                }
                // Разделитель дроби — по языку приложения, как и на кнопках рядом.
                Text(String.format(s.locale, "%.1f", value), fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { value = (value + 0.1).coerceAtMost(500.0) }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Text(s.weightStepPlus, maxLines = 1, softWrap = false)
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        onQuickEntry(
                            TrackerEntry(
                                trackerId = row.tracker.id,
                                atMillis = System.currentTimeMillis(),
                                value = (Math.round(value * 10.0) / 10.0),
                            ),
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) { Text(s.save, maxLines = 1, softWrap = false) }
            }
        }
        else -> Unit
    }
}

@Composable
private fun EmptyHint() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(Lang.s.emptyTitle, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(Lang.s.emptyBody, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** День, когда упаковка закончится при текущей схеме; null — считать нечего. */
fun stockRunsOut(med: Medication): Long? {
    val stock = med.stockCount ?: return null
    if (med.asNeeded || stock <= 0.0) return null
    val weekdays = med.weekdaysList()
    val perDay = if (weekdays.isNotEmpty()) {
        med.dosesPerIntake * med.timesPerDay * weekdays.size / 7.0
    } else {
        med.dosesPerIntake * med.timesPerDay / med.everyNDays.coerceAtLeast(1)
    }
    if (perDay <= 0.0) return null
    return today() + (stock / perDay).toLong()
}

/** «5 марта» — короткая дата для метки на карточке. */
fun shortDayText(day: Long): String =
    LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("d MMMM", Lang.s.locale))

/** Маленькая «таблетка»-метка с фактом о лекарстве; переносится строкой во FlowRow, длинная — с многоточием. */
@Composable
private fun InfoPill(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * Прогресс набора точками вместо строки «Приём 1 из 3»: выпито — зелёные, пропущено — серые с крестом,
 * следующий — контур primary, остальные — контур. Текст остаётся для TalkBack.
 */
@Composable
fun SetDots(statuses: List<DoseStatus?>, nextIndex: Int, modifier: Modifier = Modifier) {
    val s = Lang.s
    val outline = MaterialTheme.colorScheme.outline
    val primary = MaterialTheme.colorScheme.primary
    val skipped = MaterialTheme.colorScheme.outlineVariant
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.semantics { contentDescription = s.intakeOf(nextIndex.coerceAtLeast(1), statuses.size) },
    ) {
        statuses.forEachIndexed { i, status ->
            val isNext = i == nextIndex - 1
            val dot = Modifier.size(11.dp)
            when (status) {
                DoseStatus.TAKEN -> Box(dot.background(GREEN, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(8.dp))
                }
                DoseStatus.SKIPPED -> Box(dot.background(skipped, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(8.dp))
                }
                else -> Box(dot.border(if (isNext) 2.dp else 1.dp, if (isNext) primary else outline, CircleShape))
            }
        }
    }
}

@Composable
private fun MedCard(
    row: MedRow,
    now: Long,
    awake: Boolean,
    compact: Boolean,
    highlighted: Boolean,
    snoozeOptions: List<Int>,
    onTake: (Long) -> Unit,
    onTakeNow: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onSnooze: (Long, Int) -> Unit,
    onEdit: (Long) -> Unit,
    onLongPress: (MedRow) -> Unit,
    cardModifier: Modifier = Modifier,
) {
    val s = Lang.s
    val next = row.nextDose

    // Отметка «выпил» больше чем за час до плана — скорее ошибка, чем намерение: переспрашиваем.
    var earlyDose by remember { mutableStateOf<Dose?>(null) }
    earlyDose?.let { d ->
        val leftMin = ((d.plannedAt - System.currentTimeMillis()) / 60_000L).toInt().coerceAtLeast(1)
        AlertDialog(
            onDismissRequest = { earlyDose = null },
            title = { Text(s.earlyTitle) },
            text = { Text(s.earlyBody(formatClock(d.plannedAt), s.duration(leftMin))) },
            confirmButton = { TextButton(onClick = { earlyDose = null; onTake(d.id) }) { Text(s.earlyConfirm) } },
            dismissButton = { TextButton(onClick = { earlyDose = null }) { Text(s.cancel) } },
        )
    }
    fun takeChecked(d: Dose) {
        if (d.plannedAt - System.currentTimeMillis() > EARLY_TAKE_THRESHOLD_MS) earlyDose = d else onTake(d.id)
    }
    // Степени опоздания: до времени — нейтрально; наступило — янтарный «ждёт приёма»; больше OVERDUE_GRACE_MS
    // (будильник уже перестал звонить) — красный. Ждать еду можно не бесконечно: через MEAL_WAIT_MAX_MS
    // будильник звонит и без «Еда» — это уже просрочка. Отложенный приём — ждёт, не краснеет.
    val snoozed = row.snoozedUntil != null
    val mealTimedOut = row.waitsMeal && next != null && now - next.plannedAt >= MEAL_WAIT_MAX_MS
    val due = next != null && next.plannedAt <= now && !snoozed && (!row.waitsMeal || mealTimedOut)
    val late = due && (mealTimedOut || now - next.plannedAt >= OVERDUE_GRACE_MS)
    val done = row.dueToday && next == null && row.takenToday > 0
    val hasPlan = next != null || row.takenToday > 0 || row.skippedToday > 0
    val amber = amberText()

    val border = when {
        highlighted -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        row.med.asNeeded || !row.dueToday || !awake || !hasPlan -> null
        next == null && row.skippedToday > 0 -> BorderStroke(2.dp, AMBER)
        next == null && row.takenToday > 0 -> BorderStroke(2.dp, GREEN)
        late -> BorderStroke(2.dp, RED)
        due || snoozed -> BorderStroke(2.dp, AMBER)
        else -> null
    }

    // Факты о лекарстве — отдельными метками, чтобы длинный набор переносился аккуратно.
    val pills = buildList {
        // Форма показана иконкой; словами — только своя форма, у которой иконка общая.
        if (row.med.form !in MED_FORMS) add(row.med.form)
        add(s.amountFact(row.med.dosesPerIntake, row.med.form, row.med.doseInfo))
        // Связь с едой — такие же факты, как дозировка; каждое правило своей меткой.
        s.mealRelationParts(row.med.afterMealMinutes, row.med.beforeMealMinutes, row.med.mealCalories).forEach { add(it) }
        // Дальше — только то, что меняет решение «что сделать сейчас». Остальное (полное расписание,
        // точный остаток, прогноз) человек смотрит в карточке таблетки, а не на главном экране каждый день.
        if (!compact) {
            if (row.med.byClock) {
                // Длинный список времён не режем многоточием: лишние прячем за «и ещё N».
                val times = row.med.fixedTimesList().map { "%02d:%02d".format(it / 60, it % 60) }
                val shownTimes = times.take(3)
                add(
                    s.byClockShort + " " + shownTimes.joinToString(", ") +
                        if (times.size > shownTimes.size) " " + s.andMore(times.size - shownTimes.size) else "",
                )
            } else {
                add(
                    when {
                        row.med.asNeeded -> s.asNeededShort
                        row.linkedParentName != null -> s.afterMed(row.linkedParentName, s.duration(row.med.linkedDelayMinutes))
                        else -> s.schedule(row.med.timesPerDay, row.med.intervalMinutes, row.med.everyNDays, row.med.weekdaysList())
                    },
                )
            }
            // Курс показываем на финише: «ещё 30 дней» решения не меняет, «последний день» — меняет.
            courseDaysLeft(row.med, today())?.takeIf { it in 1..COURSE_WARN_DAYS }?.let { add(s.courseLeft(it)) }
            // Остаток — одной меткой с единицей измерения; прогноз не заходит за конец курса.
            row.med.stockCount?.let { stock ->
                val shown = stock.coerceAtLeast(0.0)
                val until = stockRunsOut(row.med)
                val courseEnd = courseEndDay(row.med.cycleStartEpochDay, row.med.durationDays)
                add(
                    when {
                        until == null -> s.stockLeft(s.pills(shown, row.med.form))
                        courseEnd != null && until >= courseEnd -> s.stockEnough(s.pills(shown, row.med.form))
                        else -> s.stockLeft(s.pills(shown, row.med.form)) + " · " + s.stockUntil(shortDayText(until))
                    },
                )
            }
        }
    }
    val showDots = row.setStatuses.isNotEmpty() && row.dueToday && awake && hasPlan

    Card(
        cardModifier
            .fillMaxWidth()
            // Тап по карточке ничего не открывает: мастер вызывается карандашом и меню долгого нажатия,
            // иначе промах мимо «Выпито» уводил в редактирование расписания.
            .combinedClickable(onClick = {}, onLongClick = { onLongPress(row) }),
        border = border,
        colors = CardDefaults.cardColors(
            containerColor = when {
                late -> MaterialTheme.colorScheme.errorContainer
                // Не surfaceVariant: на нём метки-«таблетки» того же цвета сливались с фоном.
                done -> MaterialTheme.colorScheme.surfaceContainer
                else -> MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = if (compact) 8.dp else 12.dp)) {
            val showTime = next != null && awake && row.dueToday
            val timeColor = when {
                late -> MaterialTheme.colorScheme.error
                due || snoozed -> amber
                else -> MaterialTheme.colorScheme.onSurface
            }
            val subColor = when {
                late -> MaterialTheme.colorScheme.error
                due || snoozed -> amber
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val subText = when {
                next == null -> ""
                snoozed -> s.snoozedUntilShort(formatClock(row.snoozedUntil!!))
                mealTimedOut -> s.mealNotMarked
                // «ждёт «Еда»» — только когда время уже наступило; до того полезнее обратный отсчёт.
                row.waitsMeal && next.plannedAt <= now -> s.waitsMealShort
                // Первые минуты после планового времени — «сейчас», а не «опоздание на 2 мин»: приложение само
                // ещё не напомнило (первый звонок — через интервал повторов), так что и опоздания пока нет.
                else -> s.countdown((next.plannedAt - now).let { if (it < 0 && -it < DUE_TEXT_GRACE_MS) 0L else it })
            }
            // Компактный режим без времени всё равно объясняет, почему нет кнопки.
            val compactStatus = when {
                row.med.asNeeded -> null
                !row.dueToday -> s.notTodayShort
                !awake -> s.waitingWakeShort
                next == null && row.takenToday == 0 && row.skippedToday == 0 && row.linkedParentName != null -> s.waitsForShort(row.linkedParentName)
                // Пропуск в наборе виден словами, а не только янтарной рамкой с контрастом 2,3:1.
                next == null && row.skippedToday > 0 -> s.setClosedPartialShort(row.takenToday, row.totalToday)
                next == null && row.takenToday > 0 -> s.allDoneShort
                else -> null
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(formIcon(row.med.form), contentDescription = s.formName(row.med.form), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                // Название в одну строку; длинное едет каруселью, чтобы прочитать целиком.
                MarqueeText(
                    row.med.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (compact && next != null && showTime) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            formatClock(next.plannedAt),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = timeColor,
                        )
                        Text(subText, style = MaterialTheme.typography.labelSmall, color = subColor, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                } else if (compact && compactStatus != null) {
                    Text(
                        compactStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                if (compact) {
                    when {
                        row.med.asNeeded -> FilledTonalIconButton(onClick = { onTakeNow(row.med.id) }) { Icon(Icons.Default.Check, contentDescription = s.takeNow) }
                        next != null && awake -> FilledTonalIconButton(onClick = { takeChecked(next) }) { Icon(Icons.Default.Check, contentDescription = s.took) }
                        else -> IconButton(onClick = { onEdit(row.med.id) }) { Icon(Icons.Default.Edit, contentDescription = s.edit) }
                    }
                } else {
                    IconButton(onClick = { onEdit(row.med.id) }) { Icon(Icons.Default.Edit, contentDescription = s.edit) }
                }
            }

            // Полный режим: время — отдельной строкой, крупно, с обратным отсчётом рядом.
            if (!compact && next != null && showTime) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        formatClock(next.plannedAt),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = timeColor,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        subText,
                        style = MaterialTheme.typography.labelMedium,
                        color = subColor,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(end = 8.dp),
            ) {
                // В сокращённом режиме точки прогресса живут в ряду меток: отдельной строки у него нет.
                if (compact && showDots) SetDots(row.setStatuses, row.nextIndexInSet, Modifier.padding(vertical = 5.dp))
                pills.forEach { InfoPill(it) }
            }

            if (compact) return@Column

            // Комментарий — подсказка, а не кнопка: строка с иконкой, без плашки.
            if (row.med.comment.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(end = 8.dp)) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp).padding(top = 1.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(row.med.comment, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Просроченные больше двух часов приёмы — отдельной строкой: карточка говорит про ближайший,
            // а забытый утренний приём можно разобрать здесь, не уходя в журнал.
            row.missed.firstOrNull()?.let { late ->
                Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s.missedIntakes(row.missed.size, formatClock(late.plannedAt)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onSkip(late.id) }) { Text(s.skip, maxLines = 1, softWrap = false) }
                    TextButton(onClick = { onTake(late.id) }) { Text(s.took, maxLines = 1, softWrap = false) }
                }
                Spacer(Modifier.height(4.dp))
            }

            Column(Modifier.fillMaxWidth().padding(end = 8.dp)) {
                if (showDots) {
                    SetDots(row.setStatuses, row.nextIndexInSet)
                    Spacer(Modifier.height(8.dp))
                }
                when {
                    row.med.asNeeded -> {
                        if (row.takenToday > 0) {
                            StatusLine(s.takenTodayCount(row.takenToday))
                            Spacer(Modifier.height(8.dp))
                        }
                        Button(onClick = { onTakeNow(row.med.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(s.takeNow, maxLines = 1, softWrap = false)
                        }
                    }
                    !row.dueToday -> StatusLine(s.notTodayPeriod(s.periodWords(row.med.everyNDays, row.med.weekdaysList())))
                    !awake -> StatusLine(s.waitingWake)
                    next == null && row.takenToday == 0 && row.skippedToday == 0 && row.linkedParentName != null -> StatusLine(s.waitsFor(row.linkedParentName))
                    // Закрытый набор с пропуском — не «всё выпито»: пишем честно, сколько выпито.
                    next == null && row.takenToday < row.totalToday -> StatusLine(s.setClosedPartial(row.takenToday, row.totalToday))
                    next == null -> StatusLine(s.allDone(row.takenToday, row.totalToday))
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { onSkip(next.id) }, modifier = Modifier.weight(1f).heightIn(min = 44.dp)) {
                            Text(s.skip, maxLines = 1, softWrap = false)
                        }
                        // «Отложить» — когда время наступило: за рулём, в душе, ещё не поели.
                        // Ожидание еды истекло — приём уже звонит, и «Отложить» нужна именно здесь.
                        if (next.plannedAt <= now && (!row.waitsMeal || mealTimedOut)) {
                            var menu by remember { mutableStateOf(false) }
                            Box {
                                OutlinedIconButton(onClick = { menu = true }, modifier = Modifier.size(44.dp)) {
                                    Icon(Icons.Default.Snooze, contentDescription = s.snoozeBtn)
                                }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    snoozeOptions.forEach { minutes ->
                                        DropdownMenuItem(
                                            text = { Text(s.snoozeFor(s.duration(minutes)), maxLines = 1, softWrap = false) },
                                            onClick = {
                                                menu = false
                                                onSnooze(next.id, minutes)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        Button(onClick = { takeChecked(next) }, modifier = Modifier.weight(1f).heightIn(min = 44.dp)) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(s.took, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
