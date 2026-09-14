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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.filled.Warning
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

fun trimNum(v: Double): String =
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
    /** Индексы точек, собранных кнопками: данных меньше, рисуем контуром. */
    hollowPoints: Set<Int> = emptySet(),
    /** Границы шкалы: у сна и настроения они всегда 0–5, у веса считаются по данным. */
    range: ClosedFloatingPointRange<Double>? = null,
    /** Сглаженная линия вместо ломаной. */
    smooth: Boolean = true,
    /** Вторая серия (оценка пробуждения у сна); рисуется другим цветом. */
    extraValues: List<Double> = emptyList(),
    extraColor: Color = Color.Unspecified,
) {
    // На карточке (surfaceContainerHighest) outlineVariant почти не виден — пунктир мини-графика берёт контрастнее.
    val gridColor = if (showGrid) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    Canvas(modifier) {
        val labelPad = if (showGrid) 44f else 0f
        val chartWidth = size.width - labelPad
        val padY = size.height * 0.08f
        val hasXLabels = showGrid && xLabels.size == values.size && values.size >= 2
        val bottomPad = if (hasXLabels) X_LABELS_PAD else 0f
        val usable = size.height - padY * 2 - bottomPad
        val min = range?.start ?: values.minOrNull() ?: 0.0
        val max = range?.endInclusive ?: values.maxOrNull() ?: 1.0
        val span = (max - min).takeIf { it > 0.0 } ?: 1.0

        fun yOf(v: Double): Float = padY + usable * (1f - ((v - min) / span).toFloat())

        // Сетка рисуется всегда — и на большом графике, и на превью, даже без данных:
        // так виден масштаб, а пустой блок не выглядит сломанным.
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 26f
            this.color = labelColor
        }
        for (i in 0..3) {
            val frac = i / 3f
            val y = padY + usable * (1f - frac)
            val dashed = !showGrid
            if (dashed) {
                var x = labelPad
                while (x < size.width) {
                    drawLine(gridColor, Offset(x, y), Offset(minOf(x + 8f, size.width), y), strokeWidth = 1.2f)
                    x += 16f
                }
            } else {
                drawLine(gridColor, Offset(labelPad, y), Offset(size.width, y), strokeWidth = 1.5f)
                drawContext.canvas.nativeCanvas.drawText(trimNum(min + span * frac), 0f, y + 9f, paint)
            }
        }

        if (hasXLabels) drawXLabels(xLabels, labelPad, chartWidth, labelColor)
        if (values.size < 2) return@Canvas
        val stepX = chartWidth / (values.size - 1)
        val points = values.mapIndexed { i, v -> Offset(labelPad + stepX * i, yOf(v)) }
        val path = Path()
        path.moveTo(points.first().x, points.first().y)
        if (smooth) {
            // Кубические кривые с небольшим натяжением: линия мягкая, но идёт через сами точки,
            // а не «мимо» них, как при сглаживании по серединам отрезков.
            val tension = 0.2f
            for (i in 0 until points.size - 1) {
                val p0 = points[(i - 1).coerceAtLeast(0)]
                val p1 = points[i]
                val p2 = points[i + 1]
                val p3 = points[(i + 2).coerceAtMost(points.size - 1)]
                path.cubicTo(
                    p1.x + (p2.x - p0.x) * tension,
                    p1.y + (p2.y - p0.y) * tension,
                    p2.x - (p3.x - p1.x) * tension,
                    p2.y - (p3.y - p1.y) * tension,
                    p2.x,
                    p2.y,
                )
            }
        } else {
            points.drop(1).forEach { path.lineTo(it.x, it.y) }
        }
        drawPath(path, color, style = Stroke(width = strokeWidth))
        // Вторая серия рисуется тем же способом, но тоньше и без точек.
        if (extraValues.size == values.size && extraValues.size >= 2) {
            val extraPath = Path()
            val extraPoints = extraValues.mapIndexed { i, v -> Offset(labelPad + stepX * i, yOf(v)) }
            extraPath.moveTo(extraPoints.first().x, extraPoints.first().y)
            extraPoints.drop(1).forEach { extraPath.lineTo(it.x, it.y) }
            drawPath(extraPath, extraColor, style = Stroke(width = strokeWidth * 0.6f))
        }
        if (showPoints) {
            values.forEachIndexed { i, v ->
                val center = Offset(labelPad + stepX * i, yOf(v))
                if (i in hollowPoints) {
                    drawCircle(color, radius = strokeWidth * 1.6f, center = center, style = Stroke(width = strokeWidth * 0.7f))
                } else {
                    drawCircle(color, radius = strokeWidth * 1.4f, center = center)
                }
            }
        }
    }
}

/**
 * Границы шкалы графика: оценки всегда 0–5, вес — 0–100 без данных
 * и «минимум − 5 … максимум + 5» с данными, чтобы линия шла посередине.
 */
fun chartRange(type: String, values: List<Double>): ClosedFloatingPointRange<Double> = when (type) {
    TrackerType.WEIGHT -> {
        val min = values.minOrNull()
        val max = values.maxOrNull()
        if (min == null || max == null) 0.0..100.0 else (min - 5).coerceAtLeast(0.0)..(max + 5)
    }
    else -> 0.0..5.0
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
            // Каждая карточка самостоятельна: трекер заводится своей кнопкой, общей «Создать все» нет.
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
    val smoothCharts = Settings(LocalContext.current).chartSmooth
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
                        last?.let { formatNoteTime(it.atMillis) } ?: s.neverRecorded,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // «Записать» прямо из списка; настройки — внутри деталки.
                FilledTonalIconButton(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = s.addValue) }
            }
            Spacer(Modifier.height(10.dp))
            // Неоценённые автозаписи сна (0) на график не идут — иначе каждая ночь без оценки рисуется провалом.
            val miniValues = row.entries
                .filter { row.tracker.type == TrackerType.WEIGHT || it.value > 0 }
                .take(miniPoints).reversed().map { it.value }
            LineChart(
                values = miniValues,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                strokeWidth = 4f,
                showPoints = false,
                range = chartRange(row.tracker.type, miniValues),
                smooth = smoothCharts,
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
    // Id после первого сохранения (перенос ночей создаёт трекер до кнопки «Сохранить»):
    // иначе «Сохранить» завёл бы второй трекер того же типа с двойными напоминаниями.
    var savedId by remember { mutableStateOf(trackerId) }

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

    // При создании трекера сна предлагаем перенести уже накопленные ночи из истории.
    // Только при создании: у существующего трекера диалог всплывал бы при каждом открытии настроек.
    var importCandidates by remember { mutableStateOf<List<Pair<Long, Long>>>(emptyList()) }
    LaunchedEffect(type, trackerId) {
        if (isNew && type == TrackerType.SLEEP) importCandidates = vm.sleepHistoryCandidates()
    }
    if (importCandidates.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { importCandidates = emptyList() },
            title = { Text(s.importFromHistoryTitle) },
            text = { Text(s.importFromHistoryBody(importCandidates.size)) },
            confirmButton = {
                TextButton(onClick = {
                    val nights = importCandidates
                    importCandidates = emptyList()
                    // Трекер мог ещё не существовать — сохраняем и импортируем в него.
                    vm.saveTracker(
                        Tracker(
                            id = savedId,
                            type = type,
                            askTimes = askTimes.joinToString(","),
                            startEpochDay = startDay,
                            remindEnabled = remind && askTimes.isNotEmpty(),
                            heightCm = heightText.toIntOrNull()?.coerceIn(50, 250) ?: 0,
                            sex = sex,
                        ),
                    ) { id ->
                        savedId = id
                        vm.importSleepHistory(id, nights)
                    }
                }) { Text(s.importBtn) }
            },
            dismissButton = { TextButton(onClick = { importCandidates = emptyList() }) { Text(s.later) } },
        )
    }

    // Удаление трекера уносит с собой все записи, поэтому спрашиваем подтверждение.
    var confirmDeleteTracker by remember { mutableStateOf(false) }
    if (confirmDeleteTracker) {
        ConfirmDeleteDialog(
            title = trackerDisplayName(type),
            onConfirm = { vm.deleteTracker(savedId) { onDone() } },
            onDismiss = { confirmDeleteTracker = false },
        )
    }

    // Тап по чипу времени открывает правку: раньше 10:00 нельзя было изменить.
    var editAskIndex by remember { mutableStateOf<Int?>(null) }
    editAskIndex?.let { idx ->
        val cur = askTimes.getOrElse(idx) { 600 }
        TimeWheelDialog(
            initial = LocalTime.of(cur / 60, cur % 60),
            onPick = { t ->
                askTimes = askTimes.toMutableList()
                    .also { list -> list[idx] = t.hour * 60 + t.minute }
                    .distinct().sorted()
            },
            onDismiss = { editAskIndex = null },
        )
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
                    if (!isNew) TextButton(onClick = { confirmDeleteTracker = true }) { Text(s.delete) }
                },
            )
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().imePadding().padding(16.dp)) {
                Button(
                    onClick = {
                        // Пустой список времён = напоминаний нет, как и обещает подпись; 10:00 не подставляем молча.
                        vm.saveTracker(
                            Tracker(
                                id = savedId,
                                type = type,
                                askTimes = askTimes.joinToString(","),
                                startEpochDay = startDay,
                                remindEnabled = remind && askTimes.isNotEmpty(),
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
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            if (type == TrackerType.WEIGHT) {
                TrackerSection(s.trackerBodySection) {
                    Text(s.heightSubsection, style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = heightText,
                        onValueChange = { heightText = it.filter { c -> c.isDigit() } },
                        label = { Text(s.heightFieldLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // «Пол» не спрашиваем: ИМТ и отчёт его не используют, а личный вопрос без эффекта смущает.
                    // Поле в базе оставлено для совместимости бэкапов.
                }
            }

            // Сначала «нужны ли напоминания», и только потом — когда именно.
            TrackerSection(s.trackerRemindSection) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.remindSwitch, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 12.dp))
                    Switch(checked = remind, onCheckedChange = { remind = it })
                }
                if (type == TrackerType.SLEEP) {
                    HorizontalDivider()
                    // Настройка про сон живёт рядом с трекером сна, а не во «Внешнем виде».
                    val ctx = LocalContext.current
                    var askSleep by remember { mutableStateOf(Settings(ctx).askSleepOnWake) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(s.askSleepTitle, style = MaterialTheme.typography.bodyMedium)
                            Text(s.askSleepBody, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = askSleep,
                            onCheckedChange = {
                                askSleep = it
                                Settings(ctx).askSleepOnWake = it
                            },
                        )
                    }
                }
                if (remind) {
                    HorizontalDivider()
                    Text(s.trackerTimesSection, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (askTimes.isEmpty()) {
                        Text(s.trackerNoTimes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    // Выбранные времена: подсвечены и с явной кнопкой удаления.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        askTimes.forEachIndexed { i, m ->
                            InputChip(
                                selected = true,
                                onClick = { editAskIndex = i },
                                label = { Text(hhmm(m), maxLines = 1, softWrap = false) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { askTimes = askTimes - m },
                                        modifier = Modifier.size(22.dp),
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = Lang.s.delete, modifier = Modifier.size(15.dp))
                                    }
                                },
                            )
                        }
                    }
                    if (askTimes.size < MAX_ASK_TIMES) {
                        Text(s.trackerPresetsLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // Пресеты именно добавляют время, поэтому это кнопки-подсказки, а не «выбор».
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(8 * 60, 12 * 60, 20 * 60, 22 * 60).filter { it !in askTimes }.forEach { m ->
                                AssistChip(
                                    onClick = { askTimes = (askTimes + m).distinct().sorted().take(MAX_ASK_TIMES) },
                                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text(hhmm(m), maxLines = 1, softWrap = false) },
                                )
                            }
                            AssistChip(
                                onClick = { showTimePicker = true },
                                leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text(s.trackerAddOwnTime, maxLines = 1, softWrap = false) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Совет «когда ложиться»: берём ночи со временем и оценкой не ниже средней и усредняем
 * моменты по кругу суток — иначе 23:40 и 00:20 дали бы «12:00».
 */
fun sleepAdvice(entries: List<TrackerEntry>): Triple<Int, Int, Int>? {
    val nights = entries.filter { it.sleepStart != null && it.sleepEnd != null && it.value > 0 }
    if (nights.size < SLEEP_ADVICE_MIN) return null
    val avg = nights.map { it.value }.average()
    val good = nights.filter { it.value >= avg }.ifEmpty { nights }

    fun circularMean(minutes: List<Int>): Int {
        var x = 0.0
        var y = 0.0
        minutes.forEach { m ->
            val a = m / (24.0 * 60) * 2 * Math.PI
            x += kotlin.math.cos(a)
            y += kotlin.math.sin(a)
        }
        val angle = kotlin.math.atan2(y / minutes.size, x / minutes.size)
        val raw = (angle / (2 * Math.PI) * 24 * 60).toInt()
        return ((raw % 1440) + 1440) % 1440
    }

    fun minutesOfDay(millis: Long): Int =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime().let { it.hour * 60 + it.minute }

    val bed = circularMean(good.mapNotNull { it.sleepStart?.let(::minutesOfDay) })
    val wake = circularMean(good.mapNotNull { it.sleepEnd?.let(::minutesOfDay) })
    val duration = good.map { ((it.sleepEnd!! - it.sleepStart!!) / 60_000L).toInt() }.average().toInt()
    return Triple(bed, wake, duration)
}

/** Сколько ночей нужно, чтобы совет имел смысл. */
const val SLEEP_ADVICE_MIN = 4

/** Блок настроек трекера: серая карточка с заголовком — как в мастере таблетки. */
@Composable
private fun TrackerSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
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
    // Записи, собранные кнопками, приходят без оценки — её можно поставить позже.
    var rateEntry by remember { mutableStateOf<TrackerEntry?>(null) }
    val smoothCharts = Settings(LocalContext.current).chartSmooth
    rateEntry?.let { entry ->
        var sleepRating by remember(entry.id) { mutableIntStateOf(4) }
        var wakeRating by remember(entry.id) { mutableIntStateOf(4) }
        AlertDialog(
            onDismissRequest = { rateEntry = null },
            title = { Text(s.sleepRateTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(s.sleepQualityLabel, style = MaterialTheme.typography.titleSmall)
                    EmojiRating(sleepRating) { sleepRating = it }
                    Text(s.sleepWakeQuality, style = MaterialTheme.typography.titleSmall)
                    EmojiRating(wakeRating) { wakeRating = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addTrackerEntry(entry.copy(value = sleepRating.toDouble(), wakeValue = wakeRating.toDouble()))
                    rateEntry = null
                }) { Text(s.save) }
            },
            dismissButton = { TextButton(onClick = { rateEntry = null }) { Text(s.cancel) } },
        )
    }

    // Неоценённые автозаписи сна (0) остаются в списке с кнопкой «Оценить», но не рисуются как худшая ночь.
    val shown = row.entries.filter { row.tracker.type == TrackerType.WEIGHT || it.value > 0 }.take(window).reversed()
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
                        // Сколько записей показывать — компактной кнопкой прямо над графиком.
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                trackerDisplayName(tracker.type),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            Box {
                                TextButton(onClick = { windowMenu = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                    Text(
                                        s.chartPointsLabel + ": " + (if (window == Int.MAX_VALUE) s.windowAll else window.toString()),
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                DropdownMenu(expanded = windowMenu, onDismissRequest = { windowMenu = false }) {
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
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatChip(s.minLabel, values.minOrNull()?.let { trimNum(it) } ?: "—", Modifier.weight(1f))
                            StatChip(s.maxLabel, values.maxOrNull()?.let { trimNum(it) } ?: "—", Modifier.weight(1f))
                            StatChip(s.avgLabel, if (values.isEmpty()) "—" else trimNum(values.average()), Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                        val wakeValues = if (tracker.type == TrackerType.SLEEP) {
                            shown.map { it.wakeValue ?: 0.0 }
                        } else {
                            emptyList()
                        }
                        LineChart(
                            values = values,
                            extraValues = if (wakeValues.any { it > 0.0 }) wakeValues else emptyList(),
                            extraColor = MaterialTheme.colorScheme.tertiary,
                            range = chartRange(tracker.type, values),
                            smooth = smoothCharts,
                            xLabels = shown.map { shortDate(it.atMillis) },
                            hollowPoints = shown.withIndex().filter { it.value.auto }.map { it.index }.toSet(),
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                            showGrid = true,
                        )
                        if (tracker.type == TrackerType.SLEEP && wakeValues.any { it > 0.0 }) {
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                LegendDot(MaterialTheme.colorScheme.primary, s.sleepQualityLabel)
                                LegendDot(MaterialTheme.colorScheme.tertiary, s.wakeRatingLine)
                            }
                        }
                        if (values.size < 2) {
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(s.notEnoughData, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            if (tracker.type == TrackerType.SLEEP) {
                item(key = "sleep-advice") {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(s.sleepAdviceTitle, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            val advice = sleepAdvice(row.entries)
                            if (advice == null) {
                                val have = row.entries.count { it.sleepStart != null && it.sleepEnd != null && it.value > 0 }
                                Text(
                                    s.sleepAdviceNeedMore((SLEEP_ADVICE_MIN - have).coerceAtLeast(1)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    s.sleepAdvice(hhmm(advice.first), hhmm(advice.second), s.duration(advice.third)),
                                    style = MaterialTheme.typography.bodyMedium,
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
                        // Сверху отступ меньше: кнопка «Таблица ИМТ» уже даёт свой внутренний паддинг.
                        Column(
                            Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
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
                    EntryRow(
                        tracker = tracker,
                        entry = entry,
                        onLongPress = { deleteEntry = it },
                        onRate = { rateEntry = it },
                    )
                }
            }
        }
    }
}

/** Точка легенды с подписью: две линии на графике сна надо как-то различать. */
@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun EntryRow(
    tracker: Tracker,
    entry: TrackerEntry,
    onLongPress: (TrackerEntry) -> Unit,
    onRate: (TrackerEntry) -> Unit = {},
) {
    val s = Lang.s
    Card(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { onLongPress(entry) })) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        tracker.type == TrackerType.WEIGHT -> trimNum(entry.value)
                        entry.value <= 0.0 -> "—"
                        else -> MOOD_EMOJI.getOrElse(entry.value.toInt() - 1) { "•" } + " " + entry.value.toInt() + "/5" +
                            (entry.wakeValue?.let { " · " + s.sleepWakeQuality.lowercase() + " " + it.toInt() + "/5" } ?: "")
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
            if (entry.auto) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.sleepAutoBadge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (entry.value <= 0.0) {
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { onRate(entry) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text(s.sleepRateBtn, maxLines = 1, softWrap = false)
                        }
                    }
                }
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
    var wakeRating by remember { mutableIntStateOf(4) }
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
                    wakeValue = wakeRating.toDouble(),
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
                        onValueChange = { weightText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
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
                        Text(s.sleepWakeQuality, style = MaterialTheme.typography.titleSmall)
                        EmojiRating(wakeRating) { wakeRating = it }
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
fun EmojiRating(rating: Int, onPick: (Int) -> Unit) {
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
