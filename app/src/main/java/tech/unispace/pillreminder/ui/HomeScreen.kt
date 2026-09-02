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
import java.time.format.DateTimeFormatter
import tech.unispace.pillreminder.data.Medication
import tech.unispace.pillreminder.data.TrackerType
import tech.unispace.pillreminder.data.Dose
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

/** За сколько до планового времени отметка «выпил» считается преждевременной. */
const val EARLY_TAKE_THRESHOLD_MS = 60 * 60_000L

@Composable
fun HomeScreen(
    state: HomeState,
    trackerRows: List<TrackerRow>,
    contentPadding: PaddingValues,
    onWakeUp: () -> Unit,
    onTake: (Long) -> Unit,
    onTakeNow: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onAdd: () -> Unit,
    onDelete: (Long) -> Unit,
    onUndo: (Long) -> Unit,
    onTakeAll: () -> Unit,
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

    fun confirmWithUndo(message: String, doseId: Long) {
        scope.launch {
            snackbars.currentSnackbarData?.dismiss()
            val result = snackbars.showSnackbar(message = message, actionLabel = s.undo, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) onUndo(doseId)
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
    // Долгое нажатие теперь предлагает выбор: копия схемы нужна чаще, чем удаление.
    var actionTarget by remember { mutableStateOf<MedRow?>(null) }
    actionTarget?.let { row ->
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = { Text(row.med.name) },
            text = null,
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

    // Сон, собранный кнопками «Ложусь спать» → «Я проснулся»: просим оценить сразу.
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
                WakeCard(state, onWakeUp, onOpenTips, onOpenTutorial, onOpenReport, onBedtime, onMeal)
            }

            if (state.rows.isEmpty() && state.loaded) {
                item(key = "empty") { EmptyHint() }
            }

            val dueCount = orderedRows.count { r -> r.nextDose?.let { it.plannedAt <= state.now } == true }
            if (dueCount >= 2) {
                item(key = "take-all") {
                    Button(onClick = onTakeAll, modifier = Modifier.fillMaxWidth().height(48.dp)) {
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
                    // Расписание «по часам» живёт без кнопки «я проснулся».
                    awake = state.wokeUpAt != null || row.med.byClock,
                    compact = compact,
                    onTake = { doseId ->
                        onTake(doseId)
                        confirmWithUndo(s.snackTaken(row.med.name), doseId)
                    },
                    onTakeNow = onTakeNow,
                    onSkip = { doseId ->
                        onSkip(doseId)
                        confirmWithUndo(s.snackSkipped(row.med.name), doseId)
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
                TrackerReminderCard(row, onOpenTracker, onQuickEntry)
            }
        }

        // Нижняя строка: «Я проснулся» слева (пока день не начат) и «+ Таблетка» справа.
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Один слот на весь суточный цикл: утром это «Я проснулся», днём — «Ложусь спать».
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
                // «Поел» рядом: обе кнопки относятся к текущему дню.
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

        SnackbarHost(
            hostState = snackbars,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = contentPadding.calculateBottomPadding() + 84.dp),
        )
    }
}

@Composable
private fun WakeCard(
    state: HomeState,
    onWakeUp: () -> Unit,
    onOpenTips: () -> Unit,
    onOpenTutorial: () -> Unit,
    onOpenReport: () -> Unit,
    onBedtime: () -> Unit,
    onMeal: () -> Unit,
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
            confirmButton = { TextButton(onClick = { confirmNewDay = false; onWakeUp() }) { Text(s.newDayBtn) } },
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
            confirmButton = { TextButton(onClick = { confirmShift = false; onWakeUp() }) { Text(s.resetDayConfirm) } },
            dismissButton = { TextButton(onClick = { confirmShift = false }) { Text(s.cancel) } },
        )
    }

    // Ручной сброс дня спрятан под долгое нажатие: он нужен редко, а злоупотреблять им вредно.
    ElevatedCard(
        Modifier.fillMaxWidth().combinedClickable(
            onClick = {},
            onLongClick = { if (state.wokeUpAt != null) confirmShift = true },
        ),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WbSunny, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    if (state.wokeUpAt == null) {
                        Text(s.goodMorning, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(s.wakeIntro, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(s.wokeAt(formatClock(state.wokeUpAt)), fontWeight = FontWeight.SemiBold)
                        Text(s.dayPlanned, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            if (showActions) {
                Spacer(Modifier.height(4.dp))
                // Кнопки одинаковой ширины: ряд не «прыгает» из-за разной длины подписей.
                // Компактный ряд: место на экране нужнее таблеткам, чем кнопкам.
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
                Text(
                    when {
                        last != null && doneToday -> s.trackerDoneToday(formatClock(last.atMillis))
                        last != null -> s.trackerNotToday + ", " + s.lastEntryAgo(today() - lastDay!!)
                        else -> s.trackerNotToday + ", " + s.neverRecorded
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (doneToday) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
            if (doneToday) Icon(Icons.Default.Check, contentDescription = null, tint = GREEN)
        }
        // Кулдаун вместо «раз в день»: настроение и вес часто хочется поправить сразу.
        val quietFor = last != null && System.currentTimeMillis() - last.atMillis < QUICK_ENTRY_COOLDOWN_MS
        if (!quietFor) QuickTrackerEntry(row, onQuickEntry)
      }
    }
}

/** Кнопка ряда действий: иконка над подписью, ширина — по колонке. */
@Composable
private fun HomeActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(34.dp),
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
                    Text("−0,1", maxLines = 1, softWrap = false)
                }
                Text(trimNum(value), fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { value = (value + 0.1).coerceAtMost(500.0) }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Text("+0,1", maxLines = 1, softWrap = false)
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

/** Маленькая «таблетка»-метка с фактом о лекарстве; переносится строкой во FlowRow. */
@Composable
private fun InfoPill(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        softWrap = false,
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
    val overdue = next != null && next.plannedAt <= now
    val done = row.dueToday && next == null && row.takenToday > 0
    val hasPlan = next != null || row.takenToday > 0

    val border = when {
        row.med.asNeeded || !row.dueToday || !awake || !hasPlan -> null
        next == null && row.takenToday > 0 -> BorderStroke(2.dp, GREEN)
        row.takenToday > 0 -> BorderStroke(2.dp, AMBER)
        else -> BorderStroke(2.dp, RED)
    }

    // Факты о лекарстве — отдельными метками, чтобы длинный набор переносился аккуратно.
    val pills = buildList {
        add(listOf(row.med.form, row.med.doseInfo).filter { it.isNotBlank() }.joinToString(" "))
        add(s.perIntake(formatAmount(row.med.dosesPerIntake, row.med.form)))
        if (row.med.byClock) {
            add(s.byClockShort + " " + row.med.fixedTimesList().joinToString(", ") { "%02d:%02d".format(it / 60, it % 60) })
        }
        row.med.stockCount?.let { stock ->
            add(s.stockLeft(if (stock % 1.0 == 0.0) stock.toInt().toString() else stock.toString()))
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
                done -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = if (compact) 8.dp else 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(formIcon(row.med.form), contentDescription = row.med.form, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    row.med.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (next != null && awake && row.dueToday) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            formatClock(next.plannedAt),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            s.countdown(next.plannedAt - now),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                Icon(Icons.Default.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = dragHandleModifier.padding(start = 6.dp).size(28.dp))
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
                    next == null && row.takenToday == 0 && row.linkedParentName != null -> StatusLine(s.waitsFor(row.linkedParentName))
                    next == null -> StatusLine(s.allDone(row.takenToday, row.totalToday))
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            s.intakeOf(next.indexInDay + 1, row.totalToday),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = { onSkip(next.id) }, modifier = Modifier.height(44.dp)) { Text(s.skip, maxLines = 1, softWrap = false) }
                        Button(onClick = { takeChecked(next) }, modifier = Modifier.height(44.dp)) {
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
