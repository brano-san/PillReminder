@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package tech.unispace.pillreminder.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AssistChip
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import tech.unispace.pillreminder.data.MedLibraryEntry
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.automirrored.filled.MenuBook
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalTime
import tech.unispace.pillreminder.data.MED_FORMS
import tech.unispace.pillreminder.data.apartFromList
import tech.unispace.pillreminder.data.byClock
import tech.unispace.pillreminder.data.fixedTimesList
import tech.unispace.pillreminder.data.Medication
import tech.unispace.pillreminder.data.today

/** Шаги мастера. Промежуток между приёмами живёт на шаге «как часто». */
private enum class Step { NAME, DETAILS, AMOUNT, FREQ, CONDITIONS, SUMMARY }

private val pageAnim = tween<Float>(durationMillis = 150)

/** Разобрать сохранённую дозировку «500 мг» на число и единицу. */
private fun splitDose(dose: String, units: List<String>): Pair<String, String> {
    val trimmed = dose.trim()
    if (trimmed.isBlank()) return "" to ""
    val idx = trimmed.indexOf(' ')
    if (idx < 0) return trimmed to ""
    val value = trimmed.substring(0, idx)
    val unit = trimmed.substring(idx + 1).trim()
    return value to unit.ifBlank { units.firstOrNull().orEmpty() }
}

/** Часов бодрствования в сутках по умолчанию — для равномерной раскладки при N ≥ 5. */
const val AWAKE_HOURS_DEFAULT = 16

/**
 * Промежуток по числу приёмов в день: 2 → 12 ч, 3 → 8 ч, 4 → 5 ч,
 * N ≥ 5 — почти наверняка строгая схема, даём равномерно по времени бодрствования.
 * null — один приём, промежуток не нужен.
 */
fun defaultIntervalMinutes(timesPerDay: Int): Int? = when {
    timesPerDay <= 1 -> null
    timesPerDay == 2 -> 12 * 60
    timesPerDay == 3 -> 8 * 60
    timesPerDay == 4 -> 5 * 60
    else -> (AWAKE_HOURS_DEFAULT * 60 / (timesPerDay - 1)).coerceAtLeast(30)
}

/** Набор меток о лекарстве — те же факты, что показывает карточка на главном экране. */
private fun previewFacts(
    form: String,
    doseValue: String,
    doseUnit: String,
    amount: String,
    times: Int,
    interval: Int,
    everyNDays: Int,
    duration: Int,
    byClock: Boolean,
    clockTimes: List<Int>,
    asNeeded: Boolean,
    stock: String,
): List<String> {
    val s = Lang.s
    val amountValue = amount.replace(',', '.').toDoubleOrNull() ?: 1.0
    return buildList {
        add(listOf(form, listOf(doseValue, doseUnit).filter { it.isNotBlank() }.joinToString(" ")).filter { it.isNotBlank() }.joinToString(" "))
        add(s.perIntake(s.pills(amountValue, form)))
        when {
            asNeeded -> add(s.asNeededShort)
            byClock -> add(s.byClockShort + " " + clockTimes.sorted().joinToString(", ") { hhmmText(it) })
            else -> add(s.schedule(times, interval, everyNDays))
        }
        if (duration > 0) add(s.durationLabelShort(duration))
        if (stock.isNotBlank()) add(s.stockLeft(stock))
    }
}

/**
 * Выбор промежутка: пресеты списком плюс «своё…». Отдельным компонентом, потому что
 * используется и для «после еды», и для «до еды».
 */
@Composable
private fun MinutesPicker(value: Int, label: String, onPick: (Int) -> Unit) {
    val s = Lang.s
    var expanded by remember { mutableStateOf(false) }
    var customDialog by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }
    // MEAL_NOW = «сразу»: 0 означает «неважно», поэтому нужна отдельная минимальная величина.
    val presets = listOf(MEAL_NOW, 30, 60, 120, 180)

    if (customDialog) {
        AlertDialog(
            onDismissRequest = { customDialog = false },
            title = { Text(s.mealCustomTitle) },
            text = {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text(s.minutesLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = (customText.toIntOrNull() ?: 0) > 0,
                    onClick = {
                        customText.toIntOrNull()?.coerceIn(1, 1440)?.let(onPick)
                        customDialog = false
                    },
                ) { Text(s.done) }
            },
            dismissButton = { TextButton(onClick = { customDialog = false }) { Text(s.cancel) } },
        )
    }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = when {
                value <= 0 -> s.mealNone
                value == MEAL_NOW -> s.mealImmediately
                else -> s.duration(value)
            },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(s.mealNone) },
                onClick = {
                    onPick(0)
                    expanded = false
                },
            )
            presets.forEach { m ->
                DropdownMenuItem(
                    text = { Text(if (m == MEAL_NOW) s.mealImmediately else s.duration(m)) },
                    onClick = {
                        onPick(m)
                        expanded = false
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(s.mealCustom) },
                onClick = {
                    expanded = false
                    customText = if (value > 0) value.toString() else ""
                    customDialog = true
                },
            )
        }
    }
}

/** «Сразу после еды»: ноль занят значением «неважно», поэтому одна минута. */
const val MEAL_NOW = 1

/** Времена «по часам» по умолчанию: с 9:00 через выбранный промежуток. */
fun defaultClockTimes(times: Int, intervalMinutes: Int): List<Int> {
    val step = if (intervalMinutes > 0) intervalMinutes else 24 * 60 / times.coerceAtLeast(1)
    return (0 until times.coerceIn(1, 24)).map { (9 * 60 + it * step) % (24 * 60) }.distinct().sorted()
}

private fun hhmmText(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

@Composable
fun EditMedScreen(
    vm: MainViewModel,
    medId: Long,
    onOpenLibrary: () -> Unit,
    onOpenLibraryEntry: (Long) -> Unit,
    onDone: () -> Unit,
) {
    val s = Lang.s
    val isNew = medId == 0L
    var loaded by remember { mutableStateOf(isNew) }
    var name by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("1") }
    var timesPerDay by remember { mutableStateOf("1") }
    var intervalHours by remember { mutableStateOf("4") }
    var intervalMinutes by remember { mutableStateOf("0") }
    var everyNDays by remember { mutableStateOf("1") }
    var offsetMinutes by remember { mutableStateOf("0") }
    var form by remember { mutableStateOf(MED_FORMS.first()) }
    var formIsCustom by remember { mutableStateOf(false) }
    var doseValue by remember { mutableStateOf("") }
    var doseUnit by remember { mutableStateOf(s.doseUnits.first()) }
    var unitIsCustom by remember { mutableStateOf(false) }
    var asNeeded by remember { mutableStateOf(false) }
    var durationText by remember { mutableStateOf("0") }
    var linkedTo by remember { mutableStateOf<Long?>(null) }
    var linkedDelay by remember { mutableStateOf("120") }
    var stockText by remember { mutableStateOf("") }
    // Расписание «по часам»: пустой список = приёмы считаются от кнопки «я проснулся».
    var byClock by remember { mutableStateOf(false) }
    var clockTimes by remember { mutableStateOf(listOf(9 * 60)) }
    var editTimeIndex by remember { mutableStateOf<Int?>(null) }
    var confirmQuickSave by remember { mutableStateOf(false) }
    var showRecommend by remember { mutableStateOf(false) }
    var photoPromptFor by remember { mutableStateOf<Long?>(null) }
    // Ограничения по еде и по соседству с другими таблетками; 0 — не важно.
    var afterMeal by remember { mutableIntStateOf(0) }
    var apartOthers by remember { mutableIntStateOf(0) }
    var beforeMeal by remember { mutableIntStateOf(0) }
    var apartIds by remember { mutableStateOf(emptySet<Long>()) }
    // Выбранная готовая схема; слетает, как только что-то правят руками.
    var template by remember { mutableStateOf<String?>(null) }
    var otherMeds by remember { mutableStateOf<List<Medication>>(emptyList()) }
    var libraryNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var libraryEntries by remember { mutableStateOf<List<MedLibraryEntry>>(emptyList()) }
    var existing by remember { mutableStateOf<Medication?>(null) }

    LaunchedEffect(medId) {
        otherMeds = vm.activeMeds().filter { it.id != medId && it.linkedToMedId == null }
        libraryEntries = vm.libraryEntries()
        libraryNames = libraryEntries.map { it.name }.filter { it.isNotBlank() }.distinct()
        if (!isNew) {
            vm.load(medId)?.let { med ->
                existing = med
                name = med.name
                comment = med.comment
                amount = trimNumber(med.dosesPerIntake)
                timesPerDay = med.timesPerDay.toString()
                intervalHours = (med.intervalMinutes / 60).toString()
                intervalMinutes = (med.intervalMinutes % 60).toString()
                everyNDays = med.everyNDays.toString()
                offsetMinutes = med.firstDoseOffsetMinutes.toString()
                form = med.form
                formIsCustom = med.form !in MED_FORMS
                val (v, u) = splitDose(med.doseInfo, s.doseUnits)
                doseValue = v
                if (u.isNotBlank()) {
                    doseUnit = u
                    unitIsCustom = u !in s.doseUnits
                }
                asNeeded = med.asNeeded
                durationText = med.durationDays.toString()
                linkedTo = med.linkedToMedId
                linkedDelay = med.linkedDelayMinutes.toString()
                stockText = med.stockCount?.let { it.toInt().toString() } ?: ""
                byClock = med.byClock
                clockTimes = med.fixedTimesList().ifEmpty { listOf(9 * 60) }
                afterMeal = med.afterMealMinutes
                apartOthers = med.apartFromOthersMinutes
                beforeMeal = med.beforeMealMinutes
                apartIds = med.apartFromList().toSet()
            }
            loaded = true
        }
    }

    /** Смена числа приёмов подставляет промежуток по схеме; пользователь может поправить руками. */
    fun setTimes(raw: String) {
        timesPerDay = raw
        template = null
        val n = raw.toIntOrNull() ?: return
        val m = defaultIntervalMinutes(n)
        if (m != null) {
            intervalHours = (m / 60).toString()
            intervalMinutes = (m % 60).toString()
        }
        if (byClock) clockTimes = defaultClockTimes(n, m ?: 0)
    }
    val times = (timesPerDay.toIntOrNull() ?: 1).coerceIn(1, 24)
    val interval = ((intervalHours.toIntOrNull() ?: 0) * 60 + (intervalMinutes.toIntOrNull() ?: 0)).coerceAtLeast(5)
    val offset = (offsetMinutes.toIntOrNull() ?: 0).coerceIn(0, 24 * 60)
    val days = (everyNDays.toIntOrNull() ?: 1).coerceIn(1, 365)
    val duration = (durationText.toIntOrNull() ?: 0).coerceIn(0, 3650)

    val steps = Step.entries
    val pager = rememberPagerState(pageCount = { steps.size })
    val scope = rememberCoroutineScope()
    val page = pager.currentPage
    val isLast = page == steps.lastIndex

    fun goTo(target: Int) {
        scope.launch { pager.animateScrollToPage(target.coerceIn(0, steps.lastIndex), animationSpec = pageAnim) }
    }

    editTimeIndex?.let { idx ->
        val cur = clockTimes.getOrElse(idx) { 9 * 60 }
        TimeWheelDialog(
            initial = LocalTime.of(cur / 60, cur % 60),
            onPick = { t ->
                clockTimes = clockTimes.toMutableList().also { it[idx] = t.hour * 60 + t.minute }.distinct().sorted()
            },
            onDismiss = { editTimeIndex = null },
        )
    }

    fun save() {
        if (name.isBlank()) return
        val doseInfo = if (doseValue.isBlank()) "" else (doseValue.trim() + " " + doseUnit.trim()).trim()
        val med = (existing ?: Medication(groupId = 0, name = "", cycleStartEpochDay = today())).copy(
            name = name.trim(),
            comment = comment.trim(),
            dosesPerIntake = amount.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.01) ?: 1.0,
            timesPerDay = if (byClock && !asNeeded) clockTimes.size else times,
            fixedTimes = if (byClock && !asNeeded) clockTimes.sorted().joinToString(",") else "",
            afterMealMinutes = afterMeal,
            beforeMealMinutes = beforeMeal,
            apartFromOthersMinutes = apartOthers,
            apartFromMedIds = if (apartOthers > 0) apartIds.joinToString(",") else "",
            intervalMinutes = interval,
            everyNDays = days,
            firstDoseOffsetMinutes = offset,
            active = true,
            form = form.trim().ifBlank { MED_FORMS.first() },
            doseInfo = doseInfo,
            asNeeded = asNeeded,
            durationDays = duration,
            linkedToMedId = linkedTo,
            linkedDelayMinutes = (linkedDelay.toIntOrNull() ?: 120).coerceIn(1, 24 * 60),
            stockCount = stockText.toIntOrNull()?.toDouble(),
        )
        vm.save(med) { savedId ->
            // Новая таблетка заводится и в каталоге — предлагаем сразу добавить фото упаковки.
            if (isNew) {
                scope.launch {
                    val entry = vm.libraryEntries().firstOrNull { it.name.equals(med.name, ignoreCase = true) }
                    if (entry != null) photoPromptFor = entry.id else onDone()
                }
            } else {
                onDone()
            }
        }
    }

    if (showRecommend) {
        AlertDialog(
            onDismissRequest = { showRecommend = false },
            title = { Text(s.recommendTitle) },
            text = { Text(s.recommendBody) },
            confirmButton = { TextButton(onClick = { showRecommend = false }) { Text(s.done) } },
        )
    }
    if (confirmQuickSave) {
        AlertDialog(
            onDismissRequest = { confirmQuickSave = false },
            title = { Text(s.quickSaveTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.quickSaveBody, style = MaterialTheme.typography.bodyMedium)
                    // Тот же набор меток, что и на карточке главного экрана.
                    Text(name.trim(), fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        previewFacts(
                            form = form,
                            doseValue = doseValue,
                            doseUnit = doseUnit,
                            amount = amount,
                            times = times,
                            interval = interval,
                            everyNDays = days,
                            duration = duration,
                            byClock = byClock,
                            clockTimes = clockTimes,
                            asNeeded = asNeeded,
                            stock = stockText,
                        ).forEach { fact ->
                            Text(
                                fact,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { confirmQuickSave = false; save() }) { Text(s.save) } },
            dismissButton = { TextButton(onClick = { confirmQuickSave = false }) { Text(s.quickSaveMore) } },
        )
    }
    photoPromptFor?.let { entryId ->
        AlertDialog(
            onDismissRequest = { photoPromptFor = null; onDone() },
            title = { Text(s.photoPromptTitle) },
            text = { Text(s.photoPromptBody) },
            confirmButton = {
                TextButton(onClick = {
                    photoPromptFor = null
                    onOpenLibraryEntry(entryId)
                }) { Text(s.photoPromptAdd) }
            },
            dismissButton = { TextButton(onClick = { photoPromptFor = null; onDone() }) { Text(s.later) } },
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(if (isNew) s.newPill else s.editPill)
                            Text(s.stepOf(page + 1, steps.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { if (page == 0) onDone() else goTo(page - 1) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                        }
                    },
                    actions = {
                        if (!isNew) {
                            IconButton(onClick = { save() }, enabled = name.isNotBlank()) { Icon(Icons.Default.Check, contentDescription = s.save) }
                            IconButton(onClick = { vm.delete(medId) { onDone() } }) { Icon(Icons.Default.DeleteOutline, contentDescription = s.delete) }
                        }
                        IconButton(onClick = onDone) { Icon(Icons.Default.Close, contentDescription = s.closeNoSave) }
                    },
                )
                LinearProgressIndicator(
                    progress = { (page + 1f) / steps.size },
                    modifier = Modifier.fillMaxWidth(),
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        },
        bottomBar = {
            val compact = WindowInsets.isImeVisible
            val buttonHeight = if (compact) 40.dp else 52.dp
            Row(
                Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp, vertical = if (compact) 6.dp else 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnimatedVisibility(visible = page > 0) {
                    OutlinedButton(onClick = { goTo(page - 1) }, contentPadding = PaddingValues(horizontal = 20.dp), modifier = Modifier.height(buttonHeight)) {
                        Text(s.back, maxLines = 1, softWrap = false)
                    }
                }
                // Быстрый путь: имя есть — можно сохранить с дефолтами, не листая мастер.
                AnimatedVisibility(visible = !isLast && name.isNotBlank()) {
                    OutlinedButton(
                        onClick = { confirmQuickSave = true },
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.height(buttonHeight),
                    ) { Text(s.quickSaveBtn, maxLines = 1, softWrap = false) }
                }
                Button(
                    onClick = { if (isLast) save() else goTo(page + 1) },
                    enabled = name.isNotBlank(),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    modifier = Modifier.weight(1f).height(buttonHeight),
                ) { Text(if (isLast) s.save else s.next, maxLines = 1, softWrap = false) }
            }
        },
    ) { padding ->
        if (!loaded) return@Scaffold

        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().padding(padding), userScrollEnabled = false) { index ->
            val step = steps[index]
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (step) {
                    Step.NAME -> {
                        StepHeader(s.nameQ, s.nameBody)
                        if (libraryNames.isNotEmpty()) {
                            Text(s.fromLibraryHint, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                libraryNames.forEach { n ->
                                    FilterChip(
                                        selected = name == n,
                                        onClick = {
                                            name = n
                                            // Из каталога подтягиваем форму и дозировку — их не нужно вводить снова.
                                            libraryEntries.firstOrNull { it.name == n }?.let { entry ->
                                                if (entry.form.isNotBlank()) {
                                                    form = entry.form
                                                    formIsCustom = entry.form !in MED_FORMS
                                                }
                                                if (entry.doseInfo.isNotBlank()) {
                                                    val (v, u) = splitDose(entry.doseInfo, s.doseUnits)
                                                    doseValue = v
                                                    doseUnit = u
                                                    unitIsCustom = u !in s.doseUnits
                                                }
                                            }
                                        },
                                        label = { Text(n) },
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(s.nameLabel) },
                            placeholder = { Text(s.namePlaceholder) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            singleLine = true,
                            colors = fieldColors(),
                            supportingText = { if (name.isBlank()) Text(s.nameOptionalHint) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedButton(onClick = onOpenLibrary, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(s.openLibrary, maxLines = 1, softWrap = false)
                        }
                    }

                    Step.DETAILS -> {
                        StepHeader(s.detailsQ, s.detailsBody)
                        SectionCard(s.formSection) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MED_FORMS.forEachIndexed { i, f ->
                                FilterChip(selected = form == f && !formIsCustom, onClick = { form = f; formIsCustom = false }, label = { Text(s.forms.getOrElse(i) { f }) })
                            }
                            FilterChip(selected = formIsCustom, onClick = { formIsCustom = true; form = "" }, label = { Text(s.otherForm) })
                        }
                        if (formIsCustom) {
                            OutlinedTextField(
                                value = form,
                                onValueChange = { form = it },
                                label = { Text(s.customFormLabel) },
                                placeholder = { Text(s.customFormPlaceholder) },
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                singleLine = true,
                                colors = fieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        }
                        SectionCard(s.commentSection) {
                            OutlinedTextField(
                                value = comment,
                                onValueChange = { comment = it },
                                label = { Text(s.commentLabel) },
                                placeholder = { Text(s.commentPlaceholder) },
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                minLines = 3,
                                colors = fieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                s.commentHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Step.AMOUNT -> {
                        StepHeader(s.amountQ, s.amountBody)
                        // Пресеты и поле — в одном блоке, чтобы было понятно, к чему они относятся.
                        SectionCard(s.amountSection) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("0.25", "0.5", "1", "2", "3").forEach { v ->
                                    FilterChip(selected = amount.replace(',', '.') == v, onClick = { amount = v }, label = { Text(v) })
                                }
                            }
                            OutlinedTextField(
                                value = amount,
                                onValueChange = { amount = it },
                                label = { Text(s.amountLabel) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                colors = fieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        // Дозировка: число + единица чипами, а не свободный текст.
                        SectionCard(s.doseSection) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = doseValue,
                                    onValueChange = { doseValue = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                                    label = { Text(s.doseValueLabel) },
                                    placeholder = { Text("500") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    colors = fieldColors(),
                                    modifier = Modifier.weight(1f),
                                )
                                Text(doseUnit, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                s.doseUnits.forEach { u ->
                                    FilterChip(selected = doseUnit == u && !unitIsCustom, onClick = { doseUnit = u; unitIsCustom = false }, label = { Text(u) })
                                }
                                FilterChip(selected = unitIsCustom, onClick = { unitIsCustom = true; doseUnit = "" }, label = { Text(s.otherUnit) })
                            }
                            if (unitIsCustom) {
                                OutlinedTextField(
                                    value = doseUnit,
                                    onValueChange = { doseUnit = it },
                                    label = { Text(s.customUnitLabel) },
                                    singleLine = true,
                                    colors = fieldColors(),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Text(s.doseInfoSupport, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        SectionCard(s.stockSection) {
                            OutlinedTextField(
                                value = stockText,
                                onValueChange = { stockText = it.filter { c -> c.isDigit() } },
                                label = { Text(s.stockLabel) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                colors = fieldColors(),
                                supportingText = { Text(s.stockHintEmpty) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(s.stockSupport, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Step.FREQ -> {
                        StepHeader(s.freqQ, s.freqBody)
                        SectionCard(s.asNeededTitle) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s.asNeededBody, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(12.dp))
                                Switch(checked = asNeeded, onCheckedChange = { asNeeded = it })
                            }
                        }
                        if (!asNeeded) {
                            SectionCard(s.perDaySection) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(1, 2, 3, 4).forEach { n ->
                                        FilterChip(selected = times == n, onClick = { setTimes(n.toString()) }, label = { Text(n.toString()) })
                                    }
                                }
                                OutlinedTextField(
                                    value = timesPerDay,
                                    onValueChange = { setTimes(it.filter { c -> c.isDigit() }) },
                                    label = { Text(s.otherNumber) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    colors = fieldColors(),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            SectionCard(s.scheduleSection) {
                                Text(s.scheduleBody, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(selected = !byClock, onClick = { byClock = false }, label = { Text(s.modeWake) })
                                    FilterChip(
                                        selected = byClock,
                                        onClick = {
                                            byClock = true
                                            clockTimes = defaultClockTimes(times, interval)
                                            // «По часам» не совместимо со связкой: время задаётся явно.
                                            linkedTo = null
                                        },
                                        label = { Text(s.modeClock) },
                                    )
                                }
                                if (byClock) {
                                    Text(s.clockTimesTitle, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        clockTimes.forEachIndexed { i, m ->
                                            InputChip(selected = false, onClick = { editTimeIndex = i }, label = { Text(hhmmText(m)) })
                                        }
                                    }
                                    Text(s.clockTimesHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            // Промежуток — прямо здесь, где выбирают число приёмов.
                            if (times > 1 && !byClock) {
                                SectionCard(s.intervalSection) {
                                    Text(s.intervalBodyMulti, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TextButton(
                                        onClick = { showRecommend = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(s.recommendBtn, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                                    }
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(180, 240, 300, 360, 480, 720).forEach { m ->
                                            FilterChip(
                                                selected = interval == m,
                                                onClick = {
                                                    intervalHours = (m / 60).toString()
                                                    intervalMinutes = (m % 60).toString()
                                                },
                                                label = { Text(s.duration(m)) },
                                            )
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        OutlinedTextField(
                                            value = intervalHours,
                                            onValueChange = { intervalHours = it.filter { c -> c.isDigit() } },
                                            label = { Text(s.hoursLabel) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            colors = fieldColors(),
                                            modifier = Modifier.weight(1f),
                                        )
                                        OutlinedTextField(
                                            value = intervalMinutes,
                                            onValueChange = { intervalMinutes = it.filter { c -> c.isDigit() } },
                                            label = { Text(s.minutesLabel) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            colors = fieldColors(),
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                            SectionCard(s.everyNSection) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(1 to s.everyDayChip, 2 to s.everyOtherDayChip, 3 to s.every3DaysChip).forEach { (n, label) ->
                                        FilterChip(selected = days == n, onClick = { everyNDays = n.toString() }, label = { Text(label) })
                                    }
                                }
                                OutlinedTextField(
                                    value = everyNDays,
                                    onValueChange = { everyNDays = it.filter { c -> c.isDigit() } },
                                    label = { Text(s.otherPeriodLabel) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    colors = fieldColors(),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            SectionCard(s.durationQ) {
                                Text(s.durationBody, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(0 to s.durUnlimited, 7 to s.durWeek, 14 to s.dur2Weeks, 30 to s.durMonth).forEach { (d, label) ->
                                        FilterChip(
                                            selected = duration == d,
                                            onClick = { durationText = d.toString(); template = null },
                                            label = { Text(label) },
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = durationText,
                                    onValueChange = { durationText = it.filter { c -> c.isDigit() }; template = null },
                                    label = { Text(s.durationLabel) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    colors = fieldColors(),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (!isNew) {
                                    Text(s.durationNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    OutlinedButton(onClick = { vm.finishCourse(medId) { onDone() } }, modifier = Modifier.fillMaxWidth()) {
                                        Text(s.finishCourseNow, maxLines = 1, softWrap = false)
                                    }
                                }
                            }
                            // Итог — последним: он подводит черту под всеми настройками шага.
                            HintCard(s.scheduleResult(s.schedule(times, interval, days)))
                        }
                    }

                    Step.CONDITIONS -> {
                        StepHeader(s.conditionsQ, s.conditionsBody)
                        SectionCard(s.firstDoseSection) {
                        Text(s.firstDoseBody, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (byClock) Text(s.clockNoOffset, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!byClock) {
                            // По две кнопки в ряд: «после другой таблетки» не помещается в одну строку.
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                maxItemsInEachRow = 1,
                            ) {
                                FilterChip(
                                    selected = linkedTo == null && afterMeal == 0,
                                    onClick = { linkedTo = null; afterMeal = 0 },
                                    label = { Text(s.fromWake, maxLines = 1, softWrap = false) },
                                    modifier = Modifier.weight(1f),
                                )
                                FilterChip(
                                    selected = linkedTo == null && afterMeal > 0,
                                    onClick = {
                                        linkedTo = null
                                        // «Сразу после еды» — самый частый случай; точное время правится ниже.
                                        if (afterMeal == 0) afterMeal = MEAL_NOW
                                    },
                                    label = { Text(s.fromMeal, maxLines = 1, softWrap = false) },
                                    modifier = Modifier.weight(1f),
                                )
                                FilterChip(
                                    selected = linkedTo != null,
                                    onClick = {
                                        if (otherMeds.isNotEmpty()) {
                                            linkedTo = otherMeds.first().id
                                            // Якорь один: «после другой таблетки» отменяет «после еды».
                                            afterMeal = 0
                                        }
                                    },
                                    label = { Text(s.afterOtherPill, maxLines = 1, softWrap = false) },
                                    enabled = otherMeds.isNotEmpty(),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            // 10: подсказка относится только к недоступному варианту, поэтому стоит под ним.
                            if (otherMeds.isEmpty()) {
                                Text(
                                    s.linkNoMeds,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (!byClock && linkedTo == null && afterMeal == 0) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0 to s.immediately, 15 to "+15", 30 to "+30", 45 to "+45", 60 to "+60", 120 to "+120").forEach { (m, label) ->
                                    FilterChip(selected = offset == m, onClick = { offsetMinutes = m.toString() }, label = { Text(label) })
                                }
                            }
                            OutlinedTextField(
                                value = offsetMinutes,
                                onValueChange = { offsetMinutes = it.filter { c -> c.isDigit() } },
                                label = { Text(s.offsetLabel) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                colors = fieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else if (!byClock && linkedTo != null) {
                            SectionCard(s.linkPickLabel) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    otherMeds.forEach { med -> FilterChip(selected = linkedTo == med.id, onClick = { linkedTo = med.id }, label = { Text(med.name) }) }
                                }
                            }
                            OutlinedTextField(
                                value = linkedDelay,
                                onValueChange = { linkedDelay = it.filter { c -> c.isDigit() } },
                                label = { Text(s.linkDelayLabel) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                colors = fieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        }
                        SectionCard(s.apartSection) {
                            Text(s.apartSectionBody, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = apartOthers == 0, onClick = { apartOthers = 0 }, label = { Text(s.apartNo) })
                                listOf(30, 60, 120).forEach { m ->
                                    FilterChip(
                                        selected = apartOthers == m,
                                        onClick = { apartOthers = m },
                                        label = { Text(s.apartFor(s.duration(m)), maxLines = 1, softWrap = false) },
                                    )
                                }
                            }
                            // С какими именно таблетками разносить: пусто = с любыми.
                            if (apartOthers > 0 && otherMeds.isNotEmpty()) {
                                Text(s.apartPickLabel, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                if (linkedTo != null) {
                                    Text(
                                        s.apartConflictHint,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = apartIds.isEmpty(),
                                        onClick = { apartIds = emptySet() },
                                        label = { Text(s.apartAny, maxLines = 1, softWrap = false) },
                                    )
                                    otherMeds.filter { it.id != linkedTo }.forEach { other ->
                                        FilterChip(
                                            selected = other.id in apartIds,
                                            onClick = {
                                                apartIds = if (other.id in apartIds) apartIds - other.id else apartIds + other.id
                                            },
                                            label = { Text(other.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        )
                                    }
                                }
                            }
                        }
                        SectionCard(s.mealBeforeSection) {
                            MinutesPicker(
                                value = beforeMeal,
                                label = s.mealBeforeSection,
                                onPick = { beforeMeal = it; template = null },
                            )
                        }
                        SectionCard(s.mealAfterSection) {
                            Text(s.mealSectionBody, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            MinutesPicker(
                                value = afterMeal,
                                label = s.mealAfterSection,
                                onPick = { afterMeal = it; template = null },
                            )
                        }
                    }

                    Step.SUMMARY -> {
                        StepHeader(s.summaryQ, s.summaryBody)
                        SummaryCard(
                            name = name,
                            comment = comment,
                            amountText = amount,
                            times = times,
                            interval = interval,
                            days = days,
                            offset = offset,
                            asNeeded = asNeeded,
                            linkedName = otherMeds.firstOrNull { it.id == linkedTo }?.name,
                            linkedDelayMin = linkedDelay.toIntOrNull() ?: 120,
                        )
                        // Не чипы: метки на итоговом экране ничего не открывают, и это должно быть видно.
                        SectionCard(s.summaryQ) {
                            previewFacts(
                                form = form,
                                doseValue = doseValue,
                                doseUnit = doseUnit,
                                amount = amount,
                                times = times,
                                interval = interval,
                                everyNDays = days,
                                duration = duration,
                                byClock = byClock,
                                clockTimes = clockTimes,
                                asNeeded = asNeeded,
                                stock = stockText,
                            ).forEach { fact ->
                                Text("• " + fact, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                s.summaryHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun StepHeader(title: String, body: String) {
    Spacer(Modifier.height(8.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun HintCard(text: String) {
    Card(Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(14.dp))
    }
}

@Composable
private fun SummaryCard(
    name: String,
    comment: String,
    amountText: String,
    times: Int,
    interval: Int,
    days: Int,
    offset: Int,
    asNeeded: Boolean,
    linkedName: String?,
    linkedDelayMin: Int,
) {
    val s = Lang.s
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(s.summaryTitle, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                buildString {
                    append(name).append(" · ").append(s.perIntake(amountText)).append(" · ")
                    append(
                        when {
                            asNeeded -> s.asNeededShort
                            linkedName != null -> s.afterMed(linkedName, s.duration(linkedDelayMin))
                            else -> s.schedule(times, interval, days)
                        },
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (comment.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(comment, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!asNeeded && linkedName == null) {
                Spacer(Modifier.height(10.dp))
                Text(s.summaryPreview(previewTimes(times, interval, offset)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun previewTimes(times: Int, interval: Int, offset: Int): String =
    (0 until times.coerceAtMost(6)).joinToString(", ") { k ->
        val minutes = 8 * 60 + offset + k * interval
        val h = (minutes / 60) % 24
        val m = minutes % 60
        h.toString().padStart(2, '0') + ":" + m.toString().padStart(2, '0')
    }

private fun trimNumber(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
