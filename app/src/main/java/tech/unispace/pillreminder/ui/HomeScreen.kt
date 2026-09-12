@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package tech.unispace.pillreminder.ui

import android.widget.Toast
import tech.unispace.pillreminder.data.TrackerEntry
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import tech.unispace.pillreminder.alarm.formatAmount
import tech.unispace.pillreminder.alarm.trackerDisplayName
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import tech.unispace.pillreminder.data.EARLY_TAKE_THRESHOLD_MS
import tech.unispace.pillreminder.data.MEAL_WAIT_MAX_MS
import tech.unispace.pillreminder.data.Medication
import tech.unispace.pillreminder.data.TrackerType
import tech.unispace.pillreminder.data.Dose
import tech.unispace.pillreminder.data.askTimesList
import tech.unispace.pillreminder.data.byClock
import tech.unispace.pillreminder.data.fixedTimesList
import tech.unispace.pillreminder.data.Settings
import tech.unispace.pillreminder.data.epochDayOf
import tech.unispace.pillreminder.data.today

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

private val GREEN = Color(0xFF4CAF50)
private val AMBER = Color(0xFFFFC107)
private val RED = Color(0xFFE53935)

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
    onMeal: () -> Unit,
    sleepToRate: TrackerEntry?,
    onRateSleep: (TrackerEntry, Int, Int) -> Unit,
    onDismissSleepRating: () -> Unit,
    onDuplicate: (Long) -> Unit,
    onQuickEntry: (TrackerEntry) -> Unit,
) {
    val s = Lang.s
    val context = LocalContext.current
    val compact = Settings(context).homeCompact
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun confirmWithUndo(message: String, undo: () -> Unit) {
        scope.launch {
            snackbars.currentSnackbarData?.dismiss()
            val result = snackbars.showSnackbar(message = message, actionLabel = s.undo, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }

    // Локальный порядок для перетаскивания; персистится в onDragEnd.
    val medIds = state.rows.map { it.med.id }
    var order by remember(medIds) { mutableStateOf(medIds) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val itemHeights = remember { mutableStateMapOf<Long, Int>() }
    val orderedRows = order.mapNotNull { id -> state.rows.firstOrNull { it.med.id == id } }

    var deleteTarget by remember { mutableStateOf<MedRow?>(null) }
    deleteTarget?.let { row ->
        ConfirmDeleteDialog(title = row.med.name, onConfirm = { onDelete(row.med.id) }, onDismiss = { deleteTarget = null })
    }
    // Долгое нажатие предлагает выбор: копия схемы нужна чаще, чем удаление; в компактном режиме
    // здесь же живёт «Пропустить» — на карточке для него места нет.
    var actionTarget by remember { mutableStateOf<MedRow?>(null) }
    actionTarget?.let { row ->
        val next = row.nextDose
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = { Text(row.med.name) },
            text = if (next != null && !row.med.asNeeded) {
                {
                    OutlinedButton(
                        onClick = {
                            onSkip(next.id)
                            confirmWithUndo(s.snackSkipped(row.med.name)) { onUndo(next.id) }
                            actionTarget = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(s.skip, maxLines = 1, softWrap = false) }
                }
            } else {
                null
            },
            confirmButton = {
                TextButton(onClick = {
                    onDuplicate(row.med.id)
                    actionTarget = null
                }) { Text(s.duplicateBtn) }
            },
            dismissButton = {
                TextButton(onClick = {
                    deleteTarget = row
                    actionTarget = null
                }) { Text(s.delete) }
            },
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
        val bedAt = Settings(context).pendingSleepStart
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

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
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

            item(key = "wake") {
                WakeCard(state, onRestartDay, onOpenTips, onOpenTutorial, onOpenReport)
            }

            if (state.rows.isEmpty() && state.loaded) {
                item(key = "empty") { EmptyHint() }
            }

            // Ждущий еду приём «пора» не считается: кнопка обещает ровно то, что отметит планировщик.
            val dueCount = orderedRows.count { r -> !r.waitsMeal && r.nextDose?.let { it.plannedAt <= state.now } == true }
            if (dueCount >= 2) {
                item(key = "take-all") {
                    Button(
                        onClick = {
                            onTakeAll { ids ->
                                if (ids.isNotEmpty()) confirmWithUndo(s.snackTakenAll(ids.size)) { ids.forEach(onUndo) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(s.takeAllBtn(dueCount), maxLines = 1, softWrap = false)
                    }
                }
            }

            items(orderedRows, key = { it.med.id }) { row ->
                val id = row.med.id
                val isDragging = draggingId == id
                MedCard(
                    row = row,
                    now = state.now,
                    // Расписание «по часам» живёт без кнопки «Подъём»; уже запланированный приём — тоже
                    // доказательство, что день размечен (цикл мог истечь по 18-часовому лимиту).
                    awake = state.wokeUpAt != null || row.med.byClock || row.nextDose != null,
                    compact = compact,
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
                    onEdit = onEdit,
                    onLongPress = { actionTarget = it },
                    cardModifier = Modifier
                        // Модификатор стабилен между кадрами: тянущаяся карточка просто без
                        // анимации размещения — иначе при смене цепочки она «телепортируется».
                        .animateItem(placementSpec = if (isDragging) null else spring<IntOffset>())
                        .onGloballyPositioned { itemHeights[id] = it.size.height }
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffset else 0f
                            val sc = if (isDragging) 1.02f else 1f
                            scaleX = sc
                            scaleY = sc
                        },
                    dragHandleModifier = Modifier.pointerInput(id) {
                        detectDragGestures(
                            onDragStart = {
                                draggingId = id
                                dragOffset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val idx = order.indexOf(id)
                                val spacing = 12.dp.toPx()
                                if (dragOffset > 0 && idx < order.lastIndex) {
                                    val step = (itemHeights[order[idx + 1]] ?: 0) + spacing
                                    if (step > 0 && dragOffset > step * 0.5f) {
                                        order = order.toMutableList().apply { removeAt(idx); add(idx + 1, id) }
                                        dragOffset -= step
                                    }
                                } else if (dragOffset < 0 && idx > 0) {
                                    val step = (itemHeights[order[idx - 1]] ?: 0) + spacing
                                    if (step > 0 && -dragOffset > step * 0.5f) {
                                        order = order.toMutableList().apply { removeAt(idx); add(idx - 1, id) }
                                        dragOffset += step
                                    }
                                }
                            },
                            onDragEnd = {
                                draggingId = null
                                dragOffset = 0f
                                onReorder(order)
                            },
                            onDragCancel = {
                                draggingId = null
                                dragOffset = 0f
                            },
                        )
                    },
                )
            }

            items(trackerRows, key = { "tracker-" + it.tracker.id }) { row ->
                TrackerReminderCard(row, state.now, onOpenTracker, onQuickEntry)
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
                    val bedtimeAt = remember(state.now / 60_000) { Settings(context).pendingSleepStart }
                    FilledTonalButton(
                        onClick = {
                            onBedtime()
                            Toast.makeText(context, s.bedtimeSaved(formatClock(System.currentTimeMillis())), Toast.LENGTH_SHORT).show()
                        },
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
                            onMeal()
                            Toast.makeText(context, s.mealSaved(formatClock(System.currentTimeMillis())), Toast.LENGTH_SHORT).show()
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

@Composable
private fun WakeCard(
    state: HomeState,
    onRestartDay: () -> Unit,
    onOpenTips: () -> Unit,
    onOpenTutorial: () -> Unit,
    onOpenReport: () -> Unit,
) {
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
    var showActions by remember { mutableStateOf(settings.showHomeActions) }
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
                            Text(s.dayPlanned, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            // Схема дня: подъём → таблетки → еда → сон. Пересобирается раз в минуту, а не раз в секунду.
            if (state.wokeUpAt != null && settings.showDayTimeline) {
                val minute = state.now / 60_000
                // Ключи сравниваются по содержимому: rows пересоздаются каждую секунду, а карта форм и набор ждущих — нет.
                val formById = state.rows.associate { it.med.id to it.med.form }
                val waitingIds = state.rows.filter { it.waitsMeal }.mapNotNull { it.nextDose?.id }.toSet()
                val nodes = remember(state.doses, state.meals, state.wokeUpAt, state.bedAt, formById, waitingIds, minute) {
                    buildTimelineNodes(
                        wakeAt = state.wokeUpAt,
                        bedAt = state.bedAt,
                        doses = state.doses,
                        formById = formById,
                        meals = state.meals,
                        now = state.now,
                        waitingIds = waitingIds,
                    )
                }
                if (nodes.size > 1) {
                    Spacer(Modifier.height(10.dp))
                    DayTimeline(nodes, state.now, Modifier.fillMaxWidth())
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
            if (showActions) {
                Spacer(Modifier.height(4.dp))
                // Кнопки одинаковой ширины: ряд не «прыгает» из-за разной длины подписей.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    HomeActionButton(Icons.Default.Description, s.reportBtn, Modifier.weight(1f), onOpenReport)
                    HomeActionButton(Icons.Default.Lightbulb, s.tipsButton, Modifier.weight(1f), onOpenTips)
                    HomeActionButton(Icons.Default.School, s.tutorialBtn, Modifier.weight(1f), onOpenTutorial)
                }
            }
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
    // Красным — только когда первое время опроса уже прошло и напоминания включены:
    // в семь утра «Сегодня данных нет» о вечернем трекере не тревога, а факт.
    val minutesNow = LocalTime.now(ZoneId.systemDefault()).let { it.hour * 60 + it.minute }
    val askPassed = row.tracker.remindEnabled && minutesNow >= row.tracker.askTimesList().first()
    val subtitleColor = if (doneToday || !askPassed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error

    Card(Modifier.fillMaxWidth().clickable { onOpen(row.tracker.id) }) {
      Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(trackerIcon(row.tracker.type), contentDescription = null, tint = if (doneToday) GREEN else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(trackerDisplayName(row.tracker.type), fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        last != null && doneToday -> s.trackerDoneToday(formatClock(last.atMillis))
                        last != null -> s.trackerNotToday + ", " + s.lastEntryAgo(today() - lastDay!!)
                        else -> s.trackerNotToday + ", " + s.neverRecorded
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
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
        modifier = modifier.height(40.dp),
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
    val context = LocalContext.current
    when (row.tracker.type) {
        TrackerType.MOOD -> {
            Text(s.quickMoodTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            EmojiRating(0) { value ->
                onQuickEntry(TrackerEntry(trackerId = row.tracker.id, atMillis = System.currentTimeMillis(), value = value.toDouble()))
                Toast.makeText(context, s.savedShort, Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(context, s.savedShort, Toast.LENGTH_SHORT).show()
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
    val perDay = med.dosesPerIntake * med.timesPerDay / med.everyNDays.coerceAtLeast(1)
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

@Composable
private fun MedCard(
    row: MedRow,
    now: Long,
    awake: Boolean,
    compact: Boolean,
    onTake: (Long) -> Unit,
    onTakeNow: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onLongPress: (MedRow) -> Unit,
    cardModifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
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
    // Ждать еду можно не бесконечно: через MEAL_WAIT_MAX_MS будильник звонит и без «Еда» — карточка краснеет вместе с ним.
    val mealTimedOut = row.waitsMeal && next != null && now - next.plannedAt >= MEAL_WAIT_MAX_MS
    val overdue = next != null && next.plannedAt <= now && (!row.waitsMeal || mealTimedOut)
    val done = row.dueToday && next == null && row.takenToday > 0
    val hasPlan = next != null || row.takenToday > 0 || row.skippedToday > 0

    val border = when {
        row.med.asNeeded || !row.dueToday || !awake || !hasPlan -> null
        next == null && row.skippedToday > 0 -> BorderStroke(2.dp, AMBER)
        next == null && row.takenToday > 0 -> BorderStroke(2.dp, GREEN)
        row.takenToday > 0 -> BorderStroke(2.dp, AMBER)
        else -> BorderStroke(2.dp, RED)
    }

    // Факты о лекарстве — отдельными метками, чтобы длинный набор переносился аккуратно.
    val pills = buildList {
        add(listOf(s.formName(row.med.form), row.med.doseInfo).filter { it.isNotBlank() }.joinToString(" "))
        add(s.perIntake(formatAmount(row.med.dosesPerIntake, row.med.form)))
        // Связь с едой — такие же факты, как форма и дозировка; каждое правило своей меткой.
        s.mealRelationParts(row.med.afterMealMinutes, row.med.beforeMealMinutes, row.med.mealCalories).forEach { add(it) }
        if (row.med.byClock) {
            add(s.byClockShort + " " + row.med.fixedTimesList().joinToString(", ") { "%02d:%02d".format(it / 60, it % 60) })
        }
        row.med.stockCount?.let { stock ->
            val shown = stock.coerceAtLeast(0.0)
            add(s.stockLeft(if (shown % 1.0 == 0.0) shown.toInt().toString() else shown.toString()))
            // Прогноз «на сколько хватит» полезнее голого остатка.
            stockRunsOut(row.med)?.let { day -> add(s.stockUntil(shortDayText(day))) }
        }
        if (!compact) {
            add(
                when {
                    row.med.asNeeded -> s.asNeededShort
                    row.linkedParentName != null -> s.afterMed(row.linkedParentName, s.duration(row.med.linkedDelayMinutes))
                    else -> s.schedule(row.med.timesPerDay, row.med.intervalMinutes, row.med.everyNDays)
                },
            )
        }
    }

    Card(
        cardModifier
            .fillMaxWidth()
            .combinedClickable(onClick = { onEdit(row.med.id) }, onLongClick = { onLongPress(row) }),
        border = border,
        colors = CardDefaults.cardColors(
            containerColor = when {
                overdue -> MaterialTheme.colorScheme.errorContainer
                // Не surfaceVariant: на нём метки-«таблетки» того же цвета сливались с фоном.
                done -> MaterialTheme.colorScheme.surfaceContainer
                else -> MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = if (compact) 8.dp else 12.dp)) {
            val showTime = next != null && awake && row.dueToday
            val timeColor = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            val subColor = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            val subText = when {
                next == null -> ""
                mealTimedOut -> s.mealNotMarked
                // «ждёт «Еда»» — только когда время уже наступило; до того полезнее обратный отсчёт.
                row.waitsMeal && next.plannedAt <= now -> s.waitsMealShort
                else -> s.countdown(next.plannedAt - now)
            }
            // Компактный режим без времени всё равно объясняет, почему нет кнопки.
            val compactStatus = when {
                row.med.asNeeded -> null
                !row.dueToday -> s.notTodayShort
                !awake -> s.waitingWakeShort
                next == null && row.takenToday == 0 && row.skippedToday == 0 && row.linkedParentName != null -> s.waitsForShort(row.linkedParentName)
                next == null && (row.takenToday > 0 || row.skippedToday > 0) -> s.allDoneShort
                else -> null
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(formIcon(row.med.form), contentDescription = s.formName(row.med.form), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                // В полном режиме название не делит строку со временем: «Эсциталопрам» не ломается по буквам.
                Text(
                    row.med.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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
                        Text(subText, style = MaterialTheme.typography.labelSmall, color = subColor, maxLines = 1, softWrap = false)
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
                // Ручка перетаскивания — полноценная цель 48 dp и подпись для TalkBack.
                Box(dragHandleModifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.DragHandle, contentDescription = s.dragHandle, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                }
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
                pills.forEach { InfoPill(it) }
            }

            if (compact) return@Column

            if (row.med.comment.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    row.med.comment,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp)
                        .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            Box(Modifier.fillMaxWidth().padding(end = 8.dp)) {
                when {
                    row.med.asNeeded -> Column {
                        if (row.takenToday > 0) {
                            StatusLine(s.takenTodayCount(row.takenToday))
                            Spacer(Modifier.height(8.dp))
                        }
                        Button(onClick = { onTakeNow(row.med.id) }, modifier = Modifier.fillMaxWidth().height(46.dp)) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(s.takeNow, maxLines = 1, softWrap = false)
                        }
                    }
                    !row.dueToday -> StatusLine(s.notTodayEveryN(row.med.everyNDays))
                    !awake -> StatusLine(s.waitingWake)
                    next == null && row.takenToday == 0 && row.skippedToday == 0 && row.linkedParentName != null -> StatusLine(s.waitsFor(row.linkedParentName))
                    // Закрытый набор с пропуском — не «всё выпито»: пишем честно, сколько выпито.
                    next == null && row.takenToday < row.totalToday -> StatusLine(s.setClosedPartial(row.takenToday, row.totalToday))
                    next == null -> StatusLine(s.allDone(row.takenToday, row.totalToday))
                    else -> Column {
                        // Счётчик своей строкой: в одном ряду с двумя кнопками ему оставалось несколько dp.
                        Text(
                            s.intakeOf(row.nextIndexInSet, row.totalToday),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { onSkip(next.id) }, modifier = Modifier.weight(1f).height(44.dp)) {
                                Text(s.skip, maxLines = 1, softWrap = false)
                            }
                            Button(onClick = { takeChecked(next) }, modifier = Modifier.weight(1f).height(44.dp)) {
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
