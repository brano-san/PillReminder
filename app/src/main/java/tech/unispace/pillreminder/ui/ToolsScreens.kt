@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package tech.unispace.pillreminder.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tech.unispace.pillreminder.data.DoseStatus
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
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
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

    // Что выгружаем — определяется тем, какой кнопкой открыли системный диалог сохранения.
    var pendingContent by remember { mutableStateOf<suspend () -> String>({ "" }) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val text = pendingContent()
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(text.toByteArray(Charsets.UTF_8))
                    }
                    say(s.exportDone)
                } catch (_: Exception) {
                    say(s.importError)
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = try {
                    context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    }
                } catch (_: Exception) {
                    null
                }
                if (text == null) {
                    say(s.importError)
                } else {
                    vm.importBackup(text) { ok -> say(if (ok) s.importDone else s.importError) }
                }
            }
        }
    }

    val stamp = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT))

    ToolScreen(s.backupTitle, onBack, snackbars) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(s.exportJson, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    s.exportJsonBody,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = {
                        pendingContent = { vm.exportJson() }
                        saveLauncher.launch("pills-backup-$stamp.json")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(s.exportBtn)
                }
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
                ) {
                    Text(s.exportBtn)
                }
                Spacer(Modifier.height(12.dp))
                Text(s.exportCsvTrackers, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = {
                        pendingContent = { vm.exportTrackersCsv() }
                        saveLauncher.launch("pills-trackers-$stamp.csv")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(s.exportBtn)
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(s.importJson, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    s.importBody,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/*", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(s.importJson)
                }
            }
        }
    }
}

// ---------- Отчёт для врача ----------

@Composable
fun ReportScreen(vm: MainViewModel, onBack: () -> Unit) {
    val s = Lang.s
    val context = LocalContext.current
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var days by remember { mutableIntStateOf(30) }
    var report by remember { mutableStateOf("") }

    LaunchedEffect(days) {
        report = vm.buildReport(days)
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(report.toByteArray(Charsets.UTF_8))
                    }
                    snackbars.showSnackbar(s.exportDone)
                } catch (_: Exception) {
                    snackbars.showSnackbar(s.importError)
                }
            }
        }
    }

    ToolScreen(s.reportTitle, onBack, snackbars) {
        Text(s.periodLabel, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 30, 90).forEach { n ->
                FilterChip(
                    selected = days == n,
                    onClick = { days = n },
                    label = { Text(s.periodDays(n)) },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, report)
                    context.startActivity(Intent.createChooser(send, s.reportShare))
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(s.reportShare)
            }
            OutlinedButton(
                onClick = {
                    val stamp = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT))
                    saveLauncher.launch("pills-report-$stamp.txt")
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(s.reportSave)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Text(
                report,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

// ---------- Корреляции ----------

private enum class Series { WEIGHT, MOOD, SLEEP_QUALITY, SLEEP_HOURS, ADHERENCE }

private fun seriesLabel(series: Series, s: S): String = when (series) {
    Series.WEIGHT -> s.trackerWeight
    Series.MOOD -> s.trackerMood
    Series.SLEEP_QUALITY -> s.trackerSleep
    Series.SLEEP_HOURS -> s.seriesSleepHours
    Series.ADHERENCE -> s.seriesAdherence
}

@Composable
fun CorrelationScreen(vm: MainViewModel, onBack: () -> Unit) {
    val s = Lang.s
    val snackbars = remember { SnackbarHostState() }

    var days by remember { mutableIntStateOf(30) }
    var s1 by remember { mutableStateOf(Series.MOOD) }
    var s2 by remember { mutableStateOf(Series.SLEEP_QUALITY) }
    // Значения по дням окна: index -> value (или null, если данных нет).
    var data1 by remember { mutableStateOf<List<Double?>>(emptyList()) }
    var data2 by remember { mutableStateOf<List<Double?>>(emptyList()) }

    val trackerRows by vm.trackerRows.collectAsState()
    val journalUnused = trackerRows // подписка, чтобы пересчитывать при новых записях

    LaunchedEffect(days, s1, s2, trackerRows) {
        data1 = vm.seriesPerDay(seriesKey(s1), days)
        data2 = vm.seriesPerDay(seriesKey(s2), days)
    }

    val color1 = MaterialTheme.colorScheme.primary
    val color2 = Color(0xFFFB8C00)

    ToolScreen(s.corrTitle, onBack, snackbars) {
        Text(s.periodLabel, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(14, 30, 90).forEach { n ->
                FilterChip(selected = days == n, onClick = { days = n }, label = { Text(s.periodDays(n)) })
            }
        }

        Text(s.corrSeries1, style = MaterialTheme.typography.titleSmall, color = color1)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Series.entries.forEach { series ->
                FilterChip(
                    selected = s1 == series,
                    onClick = { s1 = series },
                    label = { Text(seriesLabel(series, s)) },
                )
            }
        }
        Text(s.corrSeries2, style = MaterialTheme.typography.titleSmall, color = color2)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Series.entries.forEach { series ->
                FilterChip(
                    selected = s2 == series,
                    onClick = { s2 = series },
                    label = { Text(seriesLabel(series, s)) },
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                val v1 = data1.filterNotNull()
                val v2 = data2.filterNotNull()
                if (v1.size < 2 || v2.size < 2) {
                    Text(
                        s.notEnoughData,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    // Обе серии нормируются в 0..1 каждая по своему диапазону.
                    LineChart(
                        values = v1,
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        color = color1,
                        showPoints = false,
                    )
                    LineChart(
                        values = v2,
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        color = color2,
                        showPoints = false,
                    )
                    Spacer(Modifier.height(8.dp))
                    val r = pearson(data1, data2)
                    Text(
                        if (r == null) s.corrNotEnough
                        else s.corrCoef(String.format(Locale.ROOT, "%.2f", r)),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

private fun seriesKey(series: Series): String = when (series) {
    Series.WEIGHT -> "weight"
    Series.MOOD -> "mood"
    Series.SLEEP_QUALITY -> "sleep_quality"
    Series.SLEEP_HOURS -> "sleep_hours"
    Series.ADHERENCE -> "adherence"
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

/** Значение серии на каждый из последних [days] дней (null — данных нет). */
suspend fun MainViewModel.seriesPerDay(key: String, days: Int): List<Double?> {
    val db = dbAccess()
    val toDay = today()
    val fromDay = toDay - days + 1

    return when (key) {
        "adherence" -> {
            val doses = db.doseDao().getAll().filter { it.dayEpochDay in fromDay..toDay }
                .groupBy { it.dayEpochDay }
            (fromDay..toDay).map { day ->
                val list = doses[day] ?: return@map null
                if (list.isEmpty()) null
                else list.count { it.status == DoseStatus.TAKEN } * 100.0 / list.size
            }
        }
        else -> {
            val type = when (key) {
                "weight" -> TrackerType.WEIGHT
                "mood" -> TrackerType.MOOD
                else -> TrackerType.SLEEP
            }
            val tracker = db.trackerDao().getAll().firstOrNull { it.type == type }
                ?: return List(days) { null }
            val entries = db.trackerDao().getAllEntries()
                .filter { it.trackerId == tracker.id && epochDayOf(it.atMillis) in fromDay..toDay }
                .groupBy { epochDayOf(it.atMillis) }
            (fromDay..toDay).map { day ->
                val list = entries[day] ?: return@map null
                when (key) {
                    "sleep_hours" -> {
                        val durations = list.mapNotNull { e ->
                            if (e.sleepStart != null && e.sleepEnd != null) {
                                (e.sleepEnd - e.sleepStart) / 3_600_000.0
                            } else {
                                null
                            }
                        }
                        durations.averageOrNull()
                    }
                    else -> list.map { it.value }.averageOrNull()
                }
            }
        }
    }
}

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
