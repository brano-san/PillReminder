@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package tech.unispace.pillreminder.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tech.unispace.pillreminder.alarm.formatAmount
import tech.unispace.pillreminder.alarm.trackerDisplayName
import tech.unispace.pillreminder.data.epochDayOf
import tech.unispace.pillreminder.data.today

/** Иконка формы выпуска — своя для каждой формы. */
fun formIcon(form: String): androidx.compose.ui.graphics.vector.ImageVector = when (form) {
    "Таблетка", "Pill" -> Icons.Default.Medication
    "Инъекция", "Injection" -> Icons.Default.Vaccines
    "Раствор", "Solution" -> Icons.Default.Science
    "Капли", "Drops" -> Icons.Default.WaterDrop
    "Ингалятор", "Inhaler" -> Icons.Default.Air
    "Порошок", "Powder" -> Icons.Default.Grain
    "Свечи", "Suppository" -> Icons.Default.Healing
    else -> Icons.Default.MedicalServices
}

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
) {
    val s = Lang.s
    val context = LocalContext.current
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    /** Показать снекбар с «Вернуть» после отметки приёма. */
    fun confirmWithUndo(message: String, doseId: Long) {
        scope.launch {
            snackbars.currentSnackbarData?.dismiss()
            val result = snackbars.showSnackbar(
                message = message,
                actionLabel = s.undo,
                duration = SnackbarDuration.Short,
            )
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
        ConfirmDeleteDialog(
            title = row.med.name,
            onConfirm = { onDelete(row.med.id) },
            onDismiss = { deleteTarget = null },
        )
    }
    // Перепроверяем раз в минуту (и после каждого возврата на экран).
    val minuteKey = state.now / 60_000
    val deliveryFine = remember(minuteKey) { deliveryOk(context) }

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
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    s.deliveryWarnTitle,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Text(
                                    s.deliveryWarnBody,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }
                }
            }

            item { WakeCard(state, onWakeUp) }

            if (state.rows.isEmpty() && state.loaded) {
                item { EmptyHint() }
            }

            // «Выпить всё, что пора» — когда наступило время сразу нескольких таблеток.
            val dueCount = orderedRows.count { r ->
                r.nextDose?.let { it.plannedAt <= state.now } == true
            }
            if (dueCount >= 2) {
                item(key = "take-all") {
                    Button(
                        onClick = { onTakeAll() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(s.takeAllBtn(dueCount))
                    }
                }
            }

            items(orderedRows, key = { it.med.id }) { row ->
                val id = row.med.id
                MedCard(
                    row = row,
                    now = state.now,
                    awake = state.wokeUpAt != null,
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
                    onLongPress = { deleteTarget = it },
                    cardModifier = Modifier
                        .onGloballyPositioned { itemHeights[id] = it.size.height }
                        .zIndex(if (draggingId == id) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (draggingId == id) dragOffset else 0f
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
                                    if (step > 0 && dragOffset > step * 0.6f) {
                                        order = order.toMutableList().apply {
                                            removeAt(idx)
                                            add(idx + 1, id)
                                        }
                                        dragOffset -= step
                                    }
                                } else if (dragOffset < 0 && idx > 0) {
                                    val step = (itemHeights[order[idx - 1]] ?: 0) + spacing
                                    if (step > 0 && -dragOffset > step * 0.6f) {
                                        order = order.toMutableList().apply {
                                            removeAt(idx)
                                            add(idx - 1, id)
                                        }
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

            // Напоминалки трекеров: записаны ли данные сегодня.
            items(trackerRows, key = { "tracker-" + it.tracker.id }) { row ->
                TrackerReminderCard(row, onOpenTracker)
            }

            if (state.wokeUpAt == null) {
                // Место под закреплённую снизу кнопку «я проснулся».
                item(key = "wake-spacer") { Spacer(Modifier.height(64.dp)) }
            }
        }

        // Кнопка «я проснулся» закреплена внизу — до неё легко дотянуться большим пальцем.
        if (state.wokeUpAt == null && state.loaded) {
            Button(
                onClick = onWakeUp,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = contentPadding.calculateBottomPadding() + 88.dp,
                    )
                    .height(58.dp),
            ) {
                Icon(Icons.Default.WbSunny, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(s.iWokeUp, style = MaterialTheme.typography.titleMedium)
            }
        }

        ExtendedFloatingActionButton(
            onClick = onAdd,
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text(s.pillFab) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        )

        SnackbarHost(
            hostState = snackbars,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = contentPadding.calculateBottomPadding() + 80.dp),
        )
    }
}

@Composable
private fun WakeCard(state: HomeState, onWakeUp: () -> Unit) {
    val s = Lang.s
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (state.wokeUpAt == null) {
                // Сама кнопка закреплена внизу экрана — тут только пояснение.
                Text(s.goodMorning, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    s.wakeIntro,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.WbSunny,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.wokeAt(formatClock(state.wokeUpAt)), fontWeight = FontWeight.SemiBold)
                        Text(
                            s.dayPlanned,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onWakeUp) { Text(s.shiftDay) }
                }
            }
        }
    }
}

@Composable
private fun TrackerReminderCard(row: TrackerRow, onOpen: (Long) -> Unit) {
    val s = Lang.s
    val last = row.entries.firstOrNull()
    val lastDay = last?.let { epochDayOf(it.atMillis) }
    val doneToday = lastDay == today()

    Card(Modifier.fillMaxWidth().clickable { onOpen(row.tracker.id) }) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                trackerIcon(row.tracker.type),
                contentDescription = null,
                tint = if (doneToday) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    trackerDisplayName(row.tracker.type),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    when {
                        last != null && doneToday -> s.trackerDoneToday(formatClock(last.atMillis))
                        last != null -> s.trackerNotToday + ", " +
                            s.lastEntryAgo(today() - lastDay!!)
                        else -> s.trackerNotToday + ", " + s.neverRecorded
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (doneToday) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            if (doneToday) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF4CAF50))
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(Lang.s.emptyTitle, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                Lang.s.emptyBody,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MedCard(
    row: MedRow,
    now: Long,
    awake: Boolean,
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
    val overdue = next != null && next.plannedAt <= now
    val done = row.dueToday && next == null && row.takenToday > 0
    val hasPlan = next != null || row.takenToday > 0

    // Обводка — прогресс дня одним взглядом: зелёная — всё выпито,
    // жёлтая — начато, но не всё, красная — ещё ничего.
    val border = when {
        row.med.asNeeded || !row.dueToday || !awake || !hasPlan -> null
        next == null && row.takenToday > 0 -> BorderStroke(2.dp, Color(0xFF4CAF50))
        row.takenToday > 0 -> BorderStroke(2.dp, Color(0xFFFFC107))
        else -> BorderStroke(2.dp, Color(0xFFE53935))
    }

    Card(
        cardModifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onEdit(row.med.id) },
                onLongClick = { onLongPress(row) },
            ),
        border = border,
        colors = CardDefaults.cardColors(
            containerColor = when {
                overdue -> MaterialTheme.colorScheme.errorContainer
                done -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    formIcon(row.med.form),
                    contentDescription = row.med.form,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    row.med.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                // Ручка перетаскивания: тянуть за неё, карточка едет по списку.
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = dragHandleModifier.size(28.dp),
                )
                IconButton(onClick = { onEdit(row.med.id) }) {
                    Icon(Icons.Default.Edit, contentDescription = s.edit)
                }
            }

            Text(
                buildString {
                    append(row.med.form)
                    if (row.med.doseInfo.isNotBlank()) {
                        append(" ")
                        append(row.med.doseInfo)
                    }
                    append(" · ")
                    append(s.perIntake(formatAmount(row.med.dosesPerIntake)))
                    row.med.stockCount?.let {
                        append(" · ")
                        append(s.stockLeft(if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()))
                    }
                    append(" · ")
                    append(
                        when {
                            row.med.asNeeded -> s.asNeededShort
                            row.linkedParentName != null -> s.afterMed(
                                row.linkedParentName,
                                s.duration(row.med.linkedDelayMinutes),
                            )
                            else -> s.schedule(
                                row.med.timesPerDay,
                                row.med.intervalMinutes,
                                row.med.everyNDays,
                            )
                        },
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (row.med.comment.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.tertiaryContainer,
                            RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        row.med.comment,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            when {
                row.med.asNeeded -> {
                    if (row.takenToday > 0) {
                        StatusLine(s.takenTodayCount(row.takenToday))
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = { onTakeNow(row.med.id) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(s.takeNow)
                    }
                }
                !row.dueToday -> StatusLine(s.notTodayEveryN(row.med.everyNDays))
                !awake -> StatusLine(s.waitingWake)
                next == null && row.takenToday == 0 && row.linkedParentName != null ->
                    StatusLine(s.waitsFor(row.linkedParentName))
                next == null -> StatusLine(s.allDone(row.takenToday, row.totalToday))
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                formatClock(next.plannedAt) + " · " + s.countdown(next.plannedAt - now),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                s.intakeOf(next.indexInDay + 1, row.totalToday),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onTake(next.id) },
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(s.took)
                        }
                        OutlinedButton(
                            onClick = { onSkip(next.id) },
                            modifier = Modifier.height(48.dp),
                        ) {
                            Text(s.skip)
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
    )
}
