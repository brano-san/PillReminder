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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import tech.unispace.pillreminder.data.MED_FORMS
import tech.unispace.pillreminder.data.Medication
import tech.unispace.pillreminder.data.today

/** Шаги мастера. Промежуток между приёмами живёт на шаге «как часто». */
private enum class Step { NAME, FORM, COMMENT, AMOUNT, FREQ, DURATION, FIRST }

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

@Composable
fun EditMedScreen(
    vm: MainViewModel,
    medId: Long,
    onOpenLibrary: () -> Unit,
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
    var otherMeds by remember { mutableStateOf<List<Medication>>(emptyList()) }
    var libraryNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var existing by remember { mutableStateOf<Medication?>(null) }

    LaunchedEffect(medId) {
        otherMeds = vm.activeMeds().filter { it.id != medId && it.linkedToMedId == null }
        libraryNames = vm.libraryEntries().map { it.name }.distinct()
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
            }
            loaded = true
        }
    }

    /** Смена числа приёмов подставляет промежуток по схеме; пользователь может поправить руками. */
    fun setTimes(raw: String) {
        timesPerDay = raw
        val m = defaultIntervalMinutes(raw.toIntOrNull() ?: return) ?: return
        intervalHours = (m / 60).toString()
        intervalMinutes = (m % 60).toString()
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

    fun save() {
        if (name.isBlank()) return
        val doseInfo = if (doseValue.isBlank()) "" else (doseValue.trim() + " " + doseUnit.trim()).trim()
        val med = (existing ?: Medication(groupId = 0, name = "", cycleStartEpochDay = today())).copy(
            name = name.trim(),
            comment = comment.trim(),
            dosesPerIntake = amount.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.01) ?: 1.0,
            timesPerDay = times,
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
        vm.save(med) { onDone() }
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
                                libraryNames.forEach { n -> FilterChip(selected = name == n, onClick = { name = n }, label = { Text(n) }) }
                            }
                        }
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(s.nameLabel) },
                            placeholder = { Text(s.namePlaceholder) },
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

                    Step.FORM -> {
                        StepHeader(s.formQ, s.formBody)
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
                                singleLine = true,
                                colors = fieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    Step.COMMENT -> {
                        StepHeader(s.commentQ, s.commentBody)
                        OutlinedTextField(
                            value = comment,
                            onValueChange = { comment = it },
                            label = { Text(s.commentLabel) },
                            placeholder = { Text(s.commentPlaceholder) },
                            minLines = 3,
                            colors = fieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        HintCard(s.commentHint)
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
                            // Промежуток — прямо здесь, где выбирают число приёмов.
                            if (times > 1) {
                                SectionCard(s.intervalSection) {
                                    Text(s.intervalBodyMulti, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(s.intervalAutoHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
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
                            HintCard(s.scheduleResult(s.schedule(times, interval, days)))
                        }
                    }

                    Step.DURATION -> {
                        StepHeader(s.durationQ, s.durationBody)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to s.durUnlimited, 7 to s.durWeek, 14 to s.dur2Weeks, 30 to s.durMonth).forEach { (d, label) ->
                                FilterChip(selected = duration == d, onClick = { durationText = d.toString() }, label = { Text(label) })
                            }
                        }
                        OutlinedTextField(
                            value = durationText,
                            onValueChange = { durationText = it.filter { c -> c.isDigit() } },
                            label = { Text(s.durationLabel) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = fieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (!isNew) {
                            HintCard(s.durationNote)
                            OutlinedButton(onClick = { vm.finishCourse(medId) { onDone() } }, modifier = Modifier.fillMaxWidth()) { Text(s.finishCourseNow) }
                        }
                    }

                    Step.FIRST -> {
                        StepHeader(s.firstDoseQ, s.firstDoseBody)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = linkedTo == null, onClick = { linkedTo = null }, label = { Text(s.fromWake) })
                            FilterChip(
                                selected = linkedTo != null,
                                onClick = { if (otherMeds.isNotEmpty()) linkedTo = otherMeds.first().id },
                                label = { Text(s.afterOtherPill) },
                                enabled = otherMeds.isNotEmpty(),
                            )
                        }
                        if (linkedTo == null) {
                            if (otherMeds.isEmpty()) HintCard(s.linkNoMeds)
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
                        } else {
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
