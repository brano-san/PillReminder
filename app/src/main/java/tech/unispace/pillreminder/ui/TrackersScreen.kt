@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package tech.unispace.pillreminder.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tech.unispace.pillreminder.alarm.MAX_ASK_TIMES
import tech.unispace.pillreminder.alarm.trackerDisplayName
import tech.unispace.pillreminder.data.Settings
import tech.unispace.pillreminder.data.Tracker
import tech.unispace.pillreminder.data.TrackerEntry
import tech.unispace.pillreminder.data.TrackerType
import tech.unispace.pillreminder.data.askTimesList
import tech.unispace.pillreminder.data.today
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val MOOD_EMOJI = listOf("😞", "😕", "😐", "🙂", "😄")
val TRACKER_TYPES = listOf(TrackerType.WEIGHT, TrackerType.MOOD, TrackerType.SLEEP)

fun trackerIcon(type: String): ImageVector = when (type) {
    TrackerType.WEIGHT -> Icons.Default.MonitorWeight
    TrackerType.MOOD -> Icons.Default.Mood
    else -> Icons.Default.Bedtime
}

private val dtFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT)
private val tmFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

private fun Long.toLdt(): LocalDateTime = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()

private fun trimNum(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.ROOT, "%.1f", v)

private fun hhmm(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

// ---------- Категории ИМТ ----------

data class BmiCategory(val label: String, val range: String, val color: Color)

fun bmiCategories(s: S): List<BmiCategory> = listOf(
    BmiCategory(s.bmiUnder, "< 18.5", Color(0xFF4FC3F7)),
    BmiCategory(s.bmiNormal, "18.5 – 25", Color(0xFF4CAF50)),
    BmiCategory(s.bmiPre, "25 – 30", Color(0xFFFBC02D)),
    BmiCategory(s.bmiOb1, "30 – 35", Color(0xFFFB8C00)),
    BmiCategory(s.bmiOb2, "35 – 40", Color(0xFFF4511E)),
    BmiCategory(s.bmiOb3, "≥ 40", Color(0xFFE53935)),
)

fun bmiCategoryFor(bmi: Double, s: S): BmiCategory {
    val cats = bmiCategories(s)
    return when {
        bmi < 18.5 -> cats[0]
        bmi < 25 -> cats[1]
        bmi < 30 -> cats[2]
        bmi < 35 -> cats[3]
        bmi < 40 -> cats[4]
        else -> cats[5]
    }
}

// ---------- График ----------

/**
 * Линейный график. Рисуется всегда: без данных — сетка (или базовая линия для мини-режима).
 * [showGrid] добавляет 4 горизонтальные линии с подписями значений.
 */
@Composable
fun LineChart(
    values: List<Double>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Float = 5f,
    showPoints: Boolean = true,
    showGrid: Boolean = false,
    xLabels: List<String> = emptyList(),
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    Canvas(modifier) {
        val labelPad = if (showGrid) 44f else 0f
        val chartWidth = size.width - labelPad
        val padY = size.height * 0.08f
        val hasXLabels = showGrid && xLabels.size == values.size && values.size >= 2
        val bottomPad = if (hasXLabels) X_LABELS_PAD else 0f
        val usable = size.height - padY * 2 - bottomPad
        val min = values.minOrNull() ?: 0.0
        val max = values.maxOrNull() ?: 1.0
        val span = (max - min).takeIf { it > 0.0 } ?: 1.0

        fun yOf(v: Double): Float = padY + usable * (1f - ((v - min) / span).toFloat())

        if (showGrid) {
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = 26f
                this.color = labelColor
            }
            for (i in 0..3) {
                val frac = i / 3f
                val y = padY + usable * (1f - frac)
                drawLine(gridColor, Offset(labelPad, y), Offset(size.width, y), strokeWidth = 1.5f)
                if (values.isNotEmpty()) {
                    drawContext.canvas.nativeCanvas.drawText(trimNum(min + span * frac), 0f, y + 9f, paint)
                }
            }
        } else {
            // Мини-режим без данных: пунктирная базовая линия, чтобы блок не был пустым.
            val y = size.height / 2
            var x = 0f
            while (x < size.width) {
                drawLine(gridColor, Offset(x, y), Offset(minOf(x + 10f, size.width), y), strokeWidth = 2f)
                x += 18f
            }
        }

        if (hasXLabels) drawXLabels(xLabels, labelPad, chartWidth, labelColor)
        if (values.size < 2) return@Canvas
        val stepX = chartWidth / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = labelPad + stepX * i
            val y = yOf(v)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = strokeWidth))
        if (showPoints) {
            values.forEachIndexed { i, v ->
                drawCircle(color, radius = strokeWidth * 1.4f, center = Offset(labelPad + stepX * i, yOf(v)))
            }
        }
    }
}

/** Высота полосы под подписи дат по оси X. */
const val X_LABELS_PAD = 34f

/** Подписи по оси X: первая, последняя и до трёх промежуточных, чтобы не налезали. */
fun DrawScope.drawXLabels(labels: List<String>, left: Float, width: Float, colorArgb: Int) {
    val n = labels.size
    if (n < 2) return
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = 24f
        color = colorArgb
    }
    val stepX = width / (n - 1)
    val idx = if (n <= 5) (0 until n).toList() else (0..4).map { it * (n - 1) / 4 }
    idx.forEach { i ->
        paint.textAlign = when (i) {
            0 -> android.graphics.Paint.Align.LEFT
            n - 1 -> android.graphics.Paint.Align.RIGHT
            else -> android.graphics.Paint.Align.CENTER
        }
        drawContext.canvas.nativeCanvas.drawText(labels[i], left + stepX * i, size.height - 6f, paint)
    }
}

/** «7.03» — короткая дата для подписей графика. */
fun shortDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d.MM"))

// ---------- Вкладка «Трекеры»: три шаблона всегда на экране ----------
@Composable
fun TrackersScreen(
    rows: List<TrackerRow>,
    onOpen: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onAddEntry: (TrackerEntry) -> Unit,
    onOpenCorrelations: () -> Unit,
    contentPadding: PaddingValues,
) {
    val s = Lang.s
    val context = LocalContext.current
    val miniPoints = remember { Settings(context).miniTrackerPoints }
    var addFor by remember { mutableStateOf<Tracker?>(null) }

    addFor?.let { tracker ->
        AddEntryDialog(tracker = tracker, onSave = onAddEntry, onDismiss = { addFor = null })
    }

    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(s.tabTrackers, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenCorrelations) { Text(s.corrButton, maxLines = 1, softWrap = false) }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(TRACKER_TYPES, key = { it }) { type ->
                val row = rows.firstOrNull { it.tracker.type == type }
                if (row != null) TrackerCard(row, miniPoints, onOpen) { addFor = row.tracker } else TemplateCard(type, onCreate)
            }
        }
    }
}

@Composable
private fun TemplateCard(type: String, onCreate: (String) -> Unit) {
    val s = Lang.s
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(trackerIcon(type), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(trackerDisplayName(type), fontWeight = FontWeight.SemiBold)
                    Text(
                        when (type) {
                            TrackerType.WEIGHT -> s.weightTemplateBody
                            TrackerType.MOOD -> s.moodTemplateBody
                            else -> s.sleepTemplateBody
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = { onCreate(type) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(s.createTracker)
            }
        }
    }
}

@Composable
private fun TrackerCard(row: TrackerRow, miniPoints: Int, onOpen: (Long) -> Unit, onAdd: () -> Unit) {
    val s = Lang.s
    val last = row.entries.firstOrNull()
    Card(Modifier.fillMaxWidth().clickable { onOpen(row.tracker.id) }) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(trackerIcon(row.tracker.type), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(trackerDisplayName(row.tracker.type), fontWeight = FontWeight.SemiBold)
                    Text(
                        last?.let { trimNum(it.value) + " · " + formatNoteTime(it.atMillis) } ?: s.neverRecorded,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // «Записать» прямо из списка; настройки — внутри деталки.
                FilledTonalIconButton(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = s.addValue) }
            }
            Spacer(Modifier.height(10.dp))
            LineChart(
                values = row.entries.take(miniPoints).reversed().map { it.value },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                strokeWidth = 4f,
                showPoints = false,
            )
        }
    }
}

// ---------- Настройка трекера ----------

@Composable
fun EditTrackerScreen(
    vm: MainViewModel,
    trackerId: Long,
    presetType: String,
    onDone: () -> Unit,
) {
    val s = Lang.s
    val isNew = trackerId == 0L
    var loaded by remember { mutableStateOf(isNew) }
    var type by remember { mutableStateOf(presetType.ifBlank { TrackerType.WEIGHT }) }
    var heightText by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf("") }
    var askTimes by remember { mutableStateOf(listOf(600)) }
    var remind by remember { mutableStateOf(true) }
    var showTimePicker by remember { mutableStateOf(false) }
    var startDay by remember { mutableStateOf(today()) }

    LaunchedEffect(trackerId) {
        if (!isNew) {
            vm.loadTracker(trackerId)?.let { t ->
                type = t.type
                heightText = if (t.heightCm > 0) t.heightCm.toString() else ""
                sex = t.sex
                askTimes = t.askTimesList()
                remind = t.remindEnabled
                startDay = t.startEpochDay
            }
            loaded = true
        }
    }

    if (showTimePicker) {
        TimeWheelDialog(
            initial = LocalTime.of(9, 0),
            onPick = {
                val m = it.hour * 60 + it.minute
                if (askTimes.size < MAX_ASK_TIMES) askTimes = (askTimes + m).distinct().sorted()
            },
            onDismiss = { showTimePicker = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((if (isNew) s.newTracker else s.editTracker) + " · " + trackerDisplayName(type)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back) }
                },
                actions = {
                    if (!isNew) TextButton(onClick = { vm.deleteTracker(trackerId) { onDone() } }) { Text(s.delete) }
                },
            )
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(16.dp)) {
                Button(
                    onClick = {
                        vm.saveTracker(
                            Tracker(
                                id = trackerId,
                                type = type,
                                askTimes = askTimes.ifEmpty { listOf(600) }.joinToString(","),
                                startEpochDay = startDay,
                                remindEnabled = remind,
                                heightCm = heightText.toIntOrNull()?.coerceIn(50, 250) ?: 0,
                                sex = sex,
                            ),
                        ) { onDone() }
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text(s.save) }
            }
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            if (type == TrackerType.WEIGHT) {
                OutlinedTextField(
                    value = heightText,
                    onValueChange = { heightText = it.filter { c -> c.isDigit() } },
                    label = { Text(s.heightLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(s.sexLabel, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = sex == "m", onClick = { sex = "m" }, label = { Text(s.sexM) })
                    FilterChip(selected = sex == "f", onClick = { sex = "f" }, label = { Text(s.sexF) })
                }
            }

            Text(s.askTimesLabel, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                askTimes.forEach { m ->
                    InputChip(
                        selected = false,
                        onClick = { if (askTimes.size > 1) askTimes = askTimes - m },
                        label = { Text(hhmm(m)) },
                        trailingIcon = {
                            if (askTimes.size > 1) Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                    )
                }
            }
            if (askTimes.size < MAX_ASK_TIMES) {
                OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(s.addTime)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.remindSwitch, modifier = Modifier.weight(1f).padding(end = 12.dp))
                Switch(checked = remind, onCheckedChange = { remind = it })
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------- Детальный экран трекера ----------

private val WINDOW_OPTIONS = listOf(5, 10, 15, 20, 30)

@Composable
fun TrackerDetailScreen(
    vm: MainViewModel,
    trackerId: Long,
    rows: List<TrackerRow>,
    onEditTracker: () -> Unit,
    onDone: () -> Unit,
) {
    val s = Lang.s
    val row = rows.firstOrNull { it.tracker.id == trackerId }
    // Трекер удалён (например, из экрана настройки) — уходим, а не рисуем пустоту.
    if (row == null) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    val tracker = row.tracker

    var window by remember { mutableIntStateOf(10) }
    var windowMenu by remember { mutableStateOf(false) }
    var customDialog by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }
    var showBmiTable by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var deleteEntry by remember { mutableStateOf<TrackerEntry?>(null) }

    val shown = row.entries.take(window).reversed()
    val values = shown.map { it.value }

    if (showBmiTable) BmiTableDialog(onDismiss = { showBmiTable = false })
    if (showAdd) AddEntryDialog(tracker = tracker, onSave = { vm.addTrackerEntry(it) }, onDismiss = { showAdd = false })
    deleteEntry?.let { entry ->
        ConfirmDeleteDialog(
            title = trimNum(entry.value) + " · " + formatNoteTime(entry.atMillis),
            onConfirm = { vm.deleteTrackerEntry(entry.id) },
            onDismiss = { deleteEntry = null },
        )
    }
    if (customDialog) {
        AlertDialog(
            onDismissRequest = { customDialog = false },
            title = { Text(s.windowLabel) },
            text = {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it.filter { c -> c.isDigit() } },
                    label = { Text(s.customWindowLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        customText.toIntOrNull()?.coerceIn(2, 1000)?.let { window = it }
                        customDialog = false
                    },
                ) { Text(s.done) }
            },
            dismissButton = { TextButton(onClick = { customDialog = false }) { Text(s.cancel) } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trackerDisplayName(tracker.type)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back) }
                },
                actions = {
                    IconButton(onClick = onEditTracker) { Icon(Icons.Default.Settings, contentDescription = s.editTracker) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(s.addValue) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "chart") {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatChip(s.minLabel, values.minOrNull()?.let { trimNum(it) } ?: "—", Modifier.weight(1f))
                            StatChip(s.maxLabel, values.maxOrNull()?.let { trimNum(it) } ?: "—", Modifier.weight(1f))
                            StatChip(s.avgLabel, if (values.isEmpty()) "—" else trimNum(values.average()), Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                        LineChart(
                            values = values,
                            xLabels = shown.map { shortDate(it.atMillis) },
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                            showGrid = true,
                        )
                        if (values.size < 2) {
                            Spacer(Modifier.height(6.dp))
                            Text(s.notEnoughData, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item(key = "window") {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        ExposedDropdownMenuBox(expanded = windowMenu, onExpandedChange = { windowMenu = it }) {
                            OutlinedTextField(
                                value = window.toString(),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(s.windowLabel) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = windowMenu) },
                                colors = fieldColors(),
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            )
                            ExposedDropdownMenu(expanded = windowMenu, onDismissRequest = { windowMenu = false }) {
                                WINDOW_OPTIONS.forEach { n ->
                                    DropdownMenuItem(
                                        text = { Text(n.toString()) },
                                        onClick = {
                                            window = n
                                            windowMenu = false
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(s.windowAll) },
                                    onClick = {
                                        window = Int.MAX_VALUE
                                        windowMenu = false
                                    },
                                )
                                // «Другое…» открывает окно ввода своего числа.
                                DropdownMenuItem(
                                    text = { Text(s.windowOther) },
                                    onClick = {
                                        windowMenu = false
                                        customText = ""
                                        customDialog = true
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (tracker.type == TrackerType.WEIGHT) {
                item(key = "bmi") {
                    val lastWeight = row.entries.firstOrNull()?.value
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s.bmiTitle, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                TextButton(onClick = { showBmiTable = true }) { Text(s.bmiTable, maxLines = 1, softWrap = false) }
                            }
                            if (tracker.heightCm <= 0) {
                                Text(s.bmiNeedHeight, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else if (lastWeight != null) {
                                val h = tracker.heightCm / 100.0
                                val bmi = lastWeight / (h * h)
                                val cat = bmiCategoryFor(bmi, s)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(14.dp).background(cat.color, RoundedCornerShape(7.dp)))
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        s.bmiValue(String.format(Locale.ROOT, "%.1f", bmi)) + " · " + cat.label,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = cat.color,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            } else {
                                Text(s.repNoData, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            if (row.entries.isEmpty()) {
                item(key = "no-entries") {
                    Text(
                        s.noEntriesYet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    )
                }
            } else {
                item(key = "log-title") {
                    Text(s.logTitle, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                }
                items(row.entries, key = { it.id }) { entry ->
                    EntryRow(tracker = tracker, entry = entry, onLongPress = { deleteEntry = it })
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun EntryRow(tracker: Tracker, entry: TrackerEntry, onLongPress: (TrackerEntry) -> Unit) {
    val s = Lang.s
    Card(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { onLongPress(entry) })) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (tracker.type) {
                        TrackerType.WEIGHT -> trimNum(entry.value)
                        else -> MOOD_EMOJI.getOrElse(entry.value.toInt() - 1) { "•" } + " " + entry.value.toInt() + "/5"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(formatNoteTime(entry.atMillis), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (tracker.type == TrackerType.SLEEP && entry.sleepStart != null && entry.sleepEnd != null) {
                Spacer(Modifier.height(4.dp))
                val durMin = ((entry.sleepEnd - entry.sleepStart) / 60_000).toInt().coerceAtLeast(0)
                Text(
                    entry.sleepStart.toLdt().format(tmFmt) + " → " + entry.sleepEnd.toLdt().format(tmFmt) + " · " +
                        s.sleptFor(s.duration(durMin)) + if (entry.awakenings > 0) " · ↑" + entry.awakenings else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (tracker.type == TrackerType.SLEEP && entry.awakenings > 0) {
                Spacer(Modifier.height(4.dp))
                Text("↑" + entry.awakenings, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (entry.tags.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(entry.tags, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (entry.note.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(entry.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BmiTableDialog(onDismiss: () -> Unit) {
    val s = Lang.s
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.bmiTable) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                bmiCategories(s).forEach { cat ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).background(cat.color, RoundedCornerShape(6.dp)))
                        Spacer(Modifier.width(10.dp))
                        Text(cat.label, modifier = Modifier.weight(1f))
                        Text(cat.range, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(s.done) } },
    )
}

// ---------- Добавление записи ----------

@Composable
fun AddEntryDialog(tracker: Tracker, onSave: (TrackerEntry) -> Unit, onDismiss: () -> Unit) {
    val s = Lang.s
    var weightText by remember { mutableStateOf("") }
    var rating by remember { mutableIntStateOf(3) }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var time by remember { mutableStateOf(LocalTime.now().withSecond(0).withNano(0)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Сон: время лёг/встал — по желанию; по умолчанию вчера 23:00 → сегодня 07:00.
    var withTimes by remember { mutableStateOf(false) }
    var sleepStartDate by remember { mutableStateOf(LocalDate.now().minusDays(1)) }
    var sleepStartTime by remember { mutableStateOf(LocalTime.of(23, 0)) }
    var sleepEndTime by remember { mutableStateOf(LocalTime.of(7, 0)) }
    var awakeningsText by remember { mutableStateOf("0") }
    var tags by remember { mutableStateOf(setOf<String>()) }
    var showSleepStartPicker by remember { mutableStateOf(false) }
    var showSleepEndPicker by remember { mutableStateOf(false) }

    if (showDatePicker) DateWheelDialog(initial = date, onPick = { date = it }, onDismiss = { showDatePicker = false })
    if (showTimePicker) TimeWheelDialog(initial = time, onPick = { time = it }, onDismiss = { showTimePicker = false })
    if (showSleepStartPicker) {
        TimeWheelDialog(
            initial = sleepStartTime,
            onPick = {
                sleepStartTime = it
                sleepStartDate = if (it.hour < 12) LocalDate.now() else LocalDate.now().minusDays(1)
            },
            onDismiss = { showSleepStartPicker = false },
        )
    }
    if (showSleepEndPicker) TimeWheelDialog(initial = sleepEndTime, onPick = { sleepEndTime = it }, onDismiss = { showSleepEndPicker = false })

    fun buildEntry(): TrackerEntry? {
        val zone = ZoneId.systemDefault()
        val at = if (tracker.type == TrackerType.SLEEP) System.currentTimeMillis()
        else LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()
        return when (tracker.type) {
            TrackerType.WEIGHT -> {
                val w = weightText.replace(',', '.').toDoubleOrNull() ?: return null
                TrackerEntry(trackerId = tracker.id, atMillis = at, value = w.coerceIn(1.0, 500.0), note = note.trim())
            }
            TrackerType.MOOD -> TrackerEntry(trackerId = tracker.id, atMillis = at, value = rating.toDouble(), note = note.trim())
            else -> {
                var start: Long? = null
                var end: Long? = null
                if (withTimes) {
                    start = LocalDateTime.of(sleepStartDate, sleepStartTime).atZone(zone).toInstant().toEpochMilli()
                    end = LocalDateTime.of(sleepStartDate, sleepEndTime).atZone(zone).toInstant().toEpochMilli()
                    if (end <= start) end = LocalDateTime.of(sleepStartDate.plusDays(1), sleepEndTime).atZone(zone).toInstant().toEpochMilli()
                }
                TrackerEntry(
                    trackerId = tracker.id,
                    atMillis = at,
                    value = rating.toDouble(),
                    note = note.trim(),
                    sleepStart = start,
                    sleepEnd = end,
                    awakenings = (awakeningsText.toIntOrNull() ?: 0).coerceIn(0, 50),
                    tags = tags.joinToString(", "),
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(trackerDisplayName(tracker.type)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (tracker.type) {
                    TrackerType.WEIGHT -> OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it },
                        label = { Text(s.weightLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TrackerType.MOOD -> {
                        Text(s.moodLabel, style = MaterialTheme.typography.titleSmall)
                        EmojiRating(rating) { rating = it }
                    }
                    else -> {
                        Text(s.sleepQualityLabel, style = MaterialTheme.typography.titleSmall)
                        EmojiRating(rating) { rating = it }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.sleepTimesSwitch, modifier = Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = withTimes, onCheckedChange = { withTimes = it })
                        }
                        if (withTimes) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { showSleepStartPicker = true }, modifier = Modifier.weight(1f)) {
                                    Text(s.sleepWentLabel + " " + sleepStartTime.format(tmFmt), maxLines = 1, softWrap = false)
                                }
                                OutlinedButton(onClick = { showSleepEndPicker = true }, modifier = Modifier.weight(1f)) {
                                    Text(s.sleepWokeLabel + " " + sleepEndTime.format(tmFmt), maxLines = 1, softWrap = false)
                                }
                            }
                        }
                        OutlinedTextField(
                            value = awakeningsText,
                            onValueChange = { awakeningsText = it.filter { c -> c.isDigit() } },
                            label = { Text(s.awakeningsLabel) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = fieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(s.sleepTagsLabel, style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            s.sleepTagPresets.forEach { tag ->
                                FilterChip(
                                    selected = tag in tags,
                                    onClick = { tags = if (tag in tags) tags - tag else tags + tag },
                                    label = { Text(tag, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(s.noteLabel) },
                    minLines = 2,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Время записи — только для веса и настроения; сон пишется «сейчас».
                if (tracker.type != TrackerType.SLEEP) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1.3f)) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(date.format(dtFmt), maxLines = 1, softWrap = false)
                        }
                        OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(time.format(tmFmt), maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    buildEntry()?.let {
                        onSave(it)
                        onDismiss()
                    }
                },
                enabled = tracker.type != TrackerType.WEIGHT || weightText.replace(',', '.').toDoubleOrNull() != null,
            ) { Text(s.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
}

@Composable
private fun EmojiRating(rating: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        MOOD_EMOJI.forEachIndexed { index, emoji ->
            val value = index + 1
            Text(
                emoji,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .background(if (rating == value) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(12.dp))
                    .clickable { onPick(value) }
                    .padding(6.dp),
            )
        }
    }
}
