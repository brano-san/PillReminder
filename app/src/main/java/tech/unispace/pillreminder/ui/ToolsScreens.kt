@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package tech.unispace.pillreminder.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.SnackbarResult
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tech.unispace.pillreminder.data.DoctorPreset
import tech.unispace.pillreminder.data.DoseStatus
import tech.unispace.pillreminder.data.Medication
import tech.unispace.pillreminder.data.medIdsList
import tech.unispace.pillreminder.data.Report
import tech.unispace.pillreminder.data.TrackerType
import tech.unispace.pillreminder.data.epochDayOf
import tech.unispace.pillreminder.data.today
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.sqrt

/** Каркас: шапка + скролл + снекбар. */
@Composable
private fun ToolScreen(
    title: String,
    onBack: () -> Unit,
    snackbars: SnackbarHostState,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Lang.s.back)
                    }
                },
                actions = actions,
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------- Экспорт и бэкап ----------

@Composable
fun BackupScreen(vm: MainViewModel, onBack: () -> Unit) {
    val s = Lang.s
    val context = LocalContext.current
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val say: (String) -> Unit = { msg -> scope.launch { snackbars.showSnackbar(msg) } }
    var confirmRestore by remember { mutableStateOf(false) }
    var pendingContent by remember { mutableStateOf<suspend () -> String>({ "" }) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val text = pendingContent()
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    say(s.exportDone)
                } catch (_: Exception) {
                    say(s.exportError)
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = try {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                } catch (_: Exception) {
                    null
                }
                if (text == null) {
                    say(s.importError)
                } else {
                    vm.importBackup(text) { outcome ->
                        say(
                            when (outcome) {
                                ImportOutcome.OK -> s.importDone
                                ImportOutcome.TOO_NEW -> s.importTooNew
                                ImportOutcome.FAILED -> s.importError
                            },
                        )
                    }
                }
            }
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(s.restoreConfirmTitle) },
            text = { Text(s.restoreConfirmBody) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/*", "*/*"))
                    },
                ) { Text(s.importJson, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text(s.cancel) } },
        )
    }

    val stamp = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT))

    ToolScreen(s.backupTitle, onBack, snackbars) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(s.exportJson, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(s.exportJsonBody, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = {
                        pendingContent = { vm.exportJson() }
                        saveLauncher.launch("pills-backup-$stamp.json")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(s.exportBtn) }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(s.exportCsvDoses, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = {
                        pendingContent = { vm.exportDosesCsv() }
                        saveLauncher.launch("pills-doses-$stamp.csv")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(s.exportBtn) }
                Spacer(Modifier.height(12.dp))
                Text(s.exportCsvTrackers, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = {
                        pendingContent = { vm.exportTrackersCsv() }
                        saveLauncher.launch("pills-trackers-$stamp.csv")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(s.exportBtn) }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(s.importJson, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(s.importBody, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { confirmRestore = true }, modifier = Modifier.fillMaxWidth()) { Text(s.importJson) }
            }
        }
    }
}

// ---------- Отчёт для врача ----------

/** Блок экрана-инструмента: серая карточка с заголовком, как в мастере таблетки. */
@Composable
private fun ToolSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
fun ReportScreen(vm: MainViewModel, onBack: () -> Unit) {
    val s = Lang.s
    val context = LocalContext.current
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var days by remember { mutableIntStateOf(30) }
    var sections by remember { mutableStateOf(Report.ALL_SECTIONS) }
    var report by remember { mutableStateOf("") }
    // Таблетки отчёта: null — все (и новые тоже), иначе явный набор. Пресет врача — именованный набор.
    var meds by remember { mutableStateOf<List<Medication>>(emptyList()) }
    var selectedMeds by remember { mutableStateOf<Set<Long>?>(null) }
    var presets by remember { mutableStateOf<List<DoctorPreset>>(emptyList()) }
    var activePreset by remember { mutableStateOf<Long?>(null) }
    var savePreset by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        meds = vm.medsForReport()
        presets = vm.doctorPresets()
    }
    LaunchedEffect(days, sections, selectedMeds) { report = vm.buildReport(days, sections, medIds = selectedMeds) }

    val saveTxt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    // В файл — без пустых разделов: печатный лист не должен быть замусорен графами «нет данных».
                    val text = vm.buildReport(days, sections, hideEmpty = true, medIds = selectedMeds)
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    snackbars.showSnackbar(s.exportDone)
                } catch (_: Exception) {
                    snackbars.showSnackbar(s.exportError)
                }
            }
        }
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val bytes = Report.toPdf(vm.buildReport(days, sections, hideEmpty = true, medIds = selectedMeds))
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    snackbars.showSnackbar(s.exportDone)
                } catch (_: Exception) {
                    snackbars.showSnackbar(s.exportError)
                }
            }
        }
    }
    val stamp = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT))

    ToolScreen(s.reportTitle, onBack, snackbars) {
        var customPeriod by remember { mutableStateOf(false) }
        var customDays by remember { mutableStateOf("") }
        if (customPeriod) {
            AlertDialog(
                onDismissRequest = { customPeriod = false },
                title = { Text(s.reportPeriodSection) },
                text = {
                    OutlinedTextField(
                        value = customDays,
                        onValueChange = { customDays = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text(s.periodDaysLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = (customDays.toIntOrNull() ?: 0) > 0,
                        onClick = {
                            customDays.toIntOrNull()?.coerceIn(1, 3650)?.let { days = it }
                            customPeriod = false
                        },
                    ) { Text(s.done) }
                },
                dismissButton = { TextButton(onClick = { customPeriod = false }) { Text(s.cancel) } },
            )
        }
        if (savePreset) {
            val exists = presets.any { it.name.trim().equals(presetName.trim(), ignoreCase = true) }
            AlertDialog(
                onDismissRequest = { savePreset = false },
                title = { Text(s.repPresetSave) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = presetName,
                            onValueChange = { presetName = it },
                            label = { Text(s.repPresetNameLabel) },
                            singleLine = true,
                            colors = fieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (exists) {
                            Spacer(Modifier.height(6.dp))
                            Text(s.repPresetReplace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = presetName.isNotBlank(),
                        onClick = {
                            val ids = selectedMeds ?: meds.map { it.id }.toSet()
                            val same = presets.firstOrNull { it.name.trim().equals(presetName.trim(), ignoreCase = true) }
                            savePreset = false
                            scope.launch {
                                // Пресет с тем же именем не плодим, а обновляем — иначе список зарастает дублями.
                                vm.saveDoctorPreset(
                                    DoctorPreset(id = same?.id ?: 0, name = presetName.trim(), medIds = ids.joinToString(",")),
                                )
                                presets = vm.doctorPresets()
                                activePreset = presets.firstOrNull { it.name == presetName.trim() }?.id
                                snackbars.showSnackbar(s.repPresetSaved)
                            }
                        },
                    ) { Text(s.save) }
                },
                dismissButton = { TextButton(onClick = { savePreset = false }) { Text(s.cancel) } },
            )
        }
        ToolSection(s.reportPeriodSection) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 30, 90).forEach { n ->
                FilterChip(selected = days == n, onClick = { days = n }, label = { Text(s.periodDays(n)) })
            }
        }
        // Свой период отдельной кнопкой: по чипу неясно, что число можно поменять.
        OutlinedButton(
            onClick = {
                customDays = days.toString()
                customPeriod = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (days in listOf(7, 30, 90)) s.periodCustomBtn else s.periodCustomSet(s.periodDays(days)),
                maxLines = 1,
                softWrap = false,
            )
        }

        }
        ToolSection(s.reportSectionsTitle) {
            Column {
                listOf(
                    Report.SEC_INTAKES to s.repIntakes,
                    Report.SEC_MEDS to s.repMeds,
                    Report.SEC_TRACKERS to s.repTrackers,
                    Report.SEC_NOTES to s.repNotes,
                    Report.SEC_VISITS to s.repVisits,
                    Report.SEC_LINKS to s.corrReportSection,
                ).forEach { (key, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { sections = if (key in sections) sections - key else sections + key },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = key in sections, onCheckedChange = { sections = if (it) sections + key else sections - key })
                        Text(label)
                    }
                }
            }
        }

        ToolSection(s.repFilterSection) {
            // Короткий гайд прямо здесь: без него неясно, зачем снимать галочки и что даёт пресет.
            Text(s.repFilterGuide, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val allIds = meds.map { it.id }
            val selected = selectedMeds ?: allIds.toSet()
            if (presets.isNotEmpty()) {
                Text(s.repPresetsTitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { preset ->
                        InputChip(
                            selected = activePreset == preset.id,
                            onClick = {
                                // Таблетку могли удалить — в пресете остаются только существующие id.
                                selectedMeds = preset.medIdsList().filter { it in allIds }.toSet()
                                activePreset = preset.id
                            },
                            label = { Text(preset.name, maxLines = 1) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = s.delete,
                                    modifier = Modifier.size(18.dp).clickable {
                                        scope.launch {
                                            vm.deleteDoctorPreset(preset.id)
                                            presets = vm.doctorPresets()
                                            if (activePreset == preset.id) activePreset = null
                                            snackbars.currentSnackbarData?.dismiss()
                                            val result = snackbars.showSnackbar(s.repPresetRemoved, actionLabel = s.undo)
                                            if (result == SnackbarResult.ActionPerformed) {
                                                vm.saveDoctorPreset(preset.copy(id = 0))
                                                presets = vm.doctorPresets()
                                            }
                                        }
                                    },
                                )
                            },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { selectedMeds = null; activePreset = null },
                    modifier = Modifier.weight(1f),
                ) { Text(s.repFilterAll, maxLines = 1, softWrap = false) }
                OutlinedButton(
                    onClick = { selectedMeds = emptySet(); activePreset = null },
                    modifier = Modifier.weight(1f),
                ) { Text(s.repFilterNone, maxLines = 1, softWrap = false) }
            }
            Column {
                meds.forEach { med ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            selectedMeds = if (med.id in selected) selected - med.id else selected + med.id
                            activePreset = null
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = med.id in selected,
                            onCheckedChange = {
                                selectedMeds = if (it) selected + med.id else selected - med.id
                                activePreset = null
                            },
                        )
                        MarqueeText(med.name, modifier = Modifier.weight(1f))
                        if (!med.active) {
                            Text(
                                s.repArchivedMark,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
            Text(
                s.repFilterCount(selected.size, allIds.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (selected.isEmpty() && meds.isNotEmpty()) {
                Text(s.repFilterEmpty, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            OutlinedButton(
                onClick = {
                    presetName = ""
                    savePreset = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(s.repPresetSave, maxLines = 1, softWrap = false) }
        }

        ToolSection(s.reportExportTitle) {
        // Файлы в одну строку, отправка — отдельной строкой под ними.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { saveTxt.launch("pills-report-$stamp.txt") },
                modifier = Modifier.weight(1f),
            ) { Text("TXT", maxLines = 1, softWrap = false) }
            OutlinedButton(
                onClick = { savePdf.launch("pills-report-$stamp.pdf") },
                modifier = Modifier.weight(1f),
            ) { Text("PDF", maxLines = 1, softWrap = false) }
        }
        FilledTonalButton(
            onClick = {
                scope.launch {
                    val text = vm.buildReport(days, sections, hideEmpty = true, medIds = selectedMeds)
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    context.startActivity(Intent.createChooser(send, s.reportShare))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(s.reportShare, maxLines = 1, softWrap = false) }

        }
        ToolSection(s.reportPreviewTitle) {
            Text(s.reportEmptyHidden, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(report, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---------- Корреляции: любые серии на одном графике ----------

private enum class Series(val key: String) {
    WEIGHT("weight"), MOOD("mood"), SLEEP_QUALITY("sleep_quality"), SLEEP_HOURS("sleep_hours"), ADHERENCE("adherence"),
}

private fun seriesLabel(series: Series, s: S): String = when (series) {
    Series.WEIGHT -> s.trackerWeight
    Series.MOOD -> s.trackerMood
    Series.SLEEP_QUALITY -> s.trackerSleep
    Series.SLEEP_HOURS -> s.seriesSleepHours
    Series.ADHERENCE -> s.seriesAdherence
}

private val SERIES_COLORS = listOf(
    Color(0xFF2E7D6F), Color(0xFFFB8C00), Color(0xFF5C6BC0), Color(0xFFE53935), Color(0xFF8E24AA),
)

/** Несколько серий на одном холсте; каждая нормирована в 0..1 по своему диапазону, пропуски соединяются. */
@Composable
private fun MultiLineChart(series: List<Pair<List<Double?>, Color>>, xLabels: List<String>, modifier: Modifier) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    Canvas(modifier) {
        val padY = size.height * 0.08f
        val usable = size.height - padY * 2 - X_LABELS_PAD
        drawXLabels(xLabels, 0f, size.width, labelColor)
        for (i in 0..3) {
            val y = padY + usable * (1f - i / 3f)
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
        }
        series.forEach { (values, color) ->
            val present = values.filterNotNull()
            if (present.size < 2 || values.size < 2) return@forEach
            val min = present.min()
            val max = present.max()
            val span = (max - min).takeIf { it > 0.0 } ?: 1.0
            val stepX = size.width / (values.size - 1)
            val path = Path()
            var started = false
            values.forEachIndexed { i, v ->
                if (v == null) return@forEachIndexed
                val x = stepX * i
                val y = padY + usable * (1f - ((v - min) / span).toFloat())
                if (!started) {
                    path.moveTo(x, y)
                    started = true
                } else {
                    path.lineTo(x, y)
                }
            }
            drawPath(path, color, style = Stroke(width = 5f))
        }
    }
}

@Composable
fun CorrelationScreen(vm: MainViewModel, onBack: () -> Unit) {
    val s = Lang.s
    val snackbars = remember { SnackbarHostState() }

    var days by remember { mutableIntStateOf(30) }
    var selected by remember { mutableStateOf(setOf(Series.MOOD, Series.SLEEP_QUALITY)) }
    var data by remember { mutableStateOf<Map<Series, List<Double?>>>(emptyMap()) }

    val trackerRows by vm.trackerRows.collectAsState()

    LaunchedEffect(days, selected, trackerRows) {
        data = selected.associateWith { vm.seriesPerDay(it.key, days) }
    }

    val colorOf = Series.entries.withIndex().associate { (i, sr) -> sr to SERIES_COLORS[i % SERIES_COLORS.size] }

    var showInfo by remember { mutableStateOf(false) }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(s.corrInfoTitle) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(s.corrInfoBody, style = MaterialTheme.typography.bodyMedium)
                    val picked = Series.entries.filter { it in selected }
                    if (picked.isNotEmpty()) {
                        Text(s.corrPairsTitle, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        // Полные названия пар — то, что убрали с экрана ради компактности.
                        for (i in picked.indices) {
                            for (j in i + 1 until picked.size) {
                                val a = data[picked[i]] ?: emptyList()
                                val b = data[picked[j]] ?: emptyList()
                                val r = pearson(a, b)
                                Text(
                                    "${i + 1}×${j + 1}  " + seriesLabel(picked[i], s) + " × " + seriesLabel(picked[j], s) + ": " +
                                        (r?.let { String.format(Locale.ROOT, "r = %.2f", it) } ?: corrMissingReason(a, b, s)),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text(s.done) } },
        )
    }

    ToolScreen(
        s.corrTitle,
        onBack,
        snackbars,
        actions = {
            IconButton(onClick = { showInfo = true }) {
                Icon(Icons.Outlined.Info, contentDescription = s.corrInfoTitle)
            }
        },
    ) {
        // График сверху.
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                MultiLineChart(
                    series = selected.map { sr -> (data[sr] ?: emptyList()) to colorOf.getValue(sr) },
                    xLabels = (0 until days).map { LocalDate.ofEpochDay(today() - days + 1 + it).format(DateTimeFormatter.ofPattern("d.MM")) },
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                )
                Spacer(Modifier.height(10.dp))
                // Легенда.
                // Легенда с номерами: по ним же читаются пары ниже — иначе текста слишком много.
                val picked = Series.entries.filter { it in selected }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    picked.forEachIndexed { i, sr ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.foundation.layout.Box(Modifier.size(10.dp).background(colorOf.getValue(sr), CircleShape))
                            Spacer(Modifier.width(6.dp))
                            Text("${i + 1}. " + seriesLabel(sr, s), style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                        }
                    }
                }
                // Парные корреляции: «1×2 0,42» вместо длинных названий.
                val pairs = buildList {
                    for (i in picked.indices) for (j in i + 1 until picked.size) {
                        add(Triple(i + 1, j + 1, pearson(data[picked[i]] ?: emptyList(), data[picked[j]] ?: emptyList())))
                    }
                }
                if (pairs.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        pairs.forEach { (a, b, r) ->
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        "$a×$b  " + (r?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "—"),
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                },
                            )
                        }
                    }
                    // Причина прочерка честная: «мало дней» и «показатель не менялся» — разные советы пользователю.
                    val reasons = buildList {
                        for (i in picked.indices) for (j in i + 1 until picked.size) {
                            val a = data[picked[i]] ?: emptyList()
                            val b = data[picked[j]] ?: emptyList()
                            if (pearson(a, b) == null) add(corrMissingReason(a, b, s))
                        }
                    }.distinct()
                    reasons.forEach { reason ->
                        Spacer(Modifier.height(6.dp))
                        Text("— " + reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Управление снизу.
        Text(s.periodLabel, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(14, 30, 90).forEach { n ->
                FilterChip(selected = days == n, onClick = { days = n }, label = { Text(s.periodDays(n)) })
            }
        }
        Text(s.corrPick, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Series.entries.forEach { sr ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = if (sr in selected) selected - sr else selected + sr },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = sr in selected, onCheckedChange = { selected = if (it) selected + sr else selected - sr })
                        androidx.compose.foundation.layout.Box(Modifier.size(10.dp).background(colorOf.getValue(sr), CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(seriesLabel(sr, s))
                    }
                }
            }
        }
    }
}

/** Пирсон по дням, где есть обе серии. */
private fun pearson(a: List<Double?>, b: List<Double?>): Double? {
    val pairs = a.zip(b).mapNotNull { (x, y) -> if (x != null && y != null) x to y else null }
    if (pairs.size < 3) return null
    val n = pairs.size
    val mx = pairs.sumOf { it.first } / n
    val my = pairs.sumOf { it.second } / n
    var cov = 0.0
    var vx = 0.0
    var vy = 0.0
    for ((x, y) in pairs) {
        cov += (x - mx) * (y - my)
        vx += (x - mx) * (x - mx)
        vy += (y - my) * (y - my)
    }
    if (vx == 0.0 || vy == 0.0) return null
    return cov / sqrt(vx * vy)
}

/** Почему корреляции нет: меньше трёх общих дней или один из показателей не менялся. */
private fun corrMissingReason(a: List<Double?>, b: List<Double?>, s: S): String {
    val pairs = a.zip(b).mapNotNull { (x, y) -> if (x != null && y != null) x to y else null }
    if (pairs.size < 3) return s.corrNotEnough
    val constant = pairs.map { it.first }.distinct().size == 1 || pairs.map { it.second }.distinct().size == 1
    return if (constant) s.corrConstant else s.corrNotEnough
}

/** Значение серии на каждый из последних [days] дней (null — данных нет). */
suspend fun MainViewModel.seriesPerDay(key: String, days: Int): List<Double?> {
    val db = dbAccess()
    val toDay = today()
    val fromDay = toDay - days + 1

    return when (key) {
        "adherence" -> {
            val doses = db.doseDao().getAll().filter { it.dayEpochDay in fromDay..toDay }.groupBy { it.dayEpochDay }
            (fromDay..toDay).map { day ->
                val list = doses[day] ?: return@map null
                if (list.isEmpty()) null else list.count { it.status == DoseStatus.TAKEN } * 100.0 / list.size
            }
        }
        else -> {
            val type = when (key) {
                "weight" -> TrackerType.WEIGHT
                "mood" -> TrackerType.MOOD
                else -> TrackerType.SLEEP
            }
            val tracker = db.trackerDao().getAll().firstOrNull { it.type == type } ?: return List(days) { null }
            val entries = db.trackerDao().getAllEntries()
                .filter { it.trackerId == tracker.id && epochDayOf(it.atMillis) in fromDay..toDay }
                .groupBy { epochDayOf(it.atMillis) }
            (fromDay..toDay).map { day ->
                val list = entries[day] ?: return@map null
                when (key) {
                    "sleep_hours" -> list.mapNotNull { e ->
                        if (e.sleepStart != null && e.sleepEnd != null) (e.sleepEnd - e.sleepStart) / 3_600_000.0 else null
                    }.averageOrNull()
                    else -> list.map { it.value }.averageOrNull()
                }
            }
        }
    }
}

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
