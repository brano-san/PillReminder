package tech.unispace.pillreminder.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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

private const val PAGE_COUNT = 8

/** Быстрая перелистка: длинная анимация блокирует кнопки и раздражает. */
private val pageAnim = tween<Float>(durationMillis = 150)

/**
 * Добавление и редактирование таблетки — мастером по шагам.
 * Один вопрос на страницу, чтобы не сваливать десяток полей разом.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditMedScreen(
    vm: MainViewModel,
    medId: Long,
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
    var doseInfo by remember { mutableStateOf("") }
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
                doseInfo = med.doseInfo
                asNeeded = med.asNeeded
                durationText = med.durationDays.toString()
                linkedTo = med.linkedToMedId
                linkedDelay = med.linkedDelayMinutes.toString()
                stockText = med.stockCount?.let { trimNumber(it) } ?: ""
            }
            loaded = true
        }
    }

    val times = (timesPerDay.toIntOrNull() ?: 1).coerceIn(1, 24)
    val interval = ((intervalHours.toIntOrNull() ?: 0) * 60 + (intervalMinutes.toIntOrNull() ?: 0))
        .coerceAtLeast(5)
    val offset = (offsetMinutes.toIntOrNull() ?: 0).coerceIn(0, 24 * 60)
    val days = (everyNDays.toIntOrNull() ?: 1).coerceIn(1, 365)
    val duration = (durationText.toIntOrNull() ?: 0).coerceIn(0, 3650)

    val pager = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()

    fun goTo(page: Int) {
        scope.launch { pager.animateScrollToPage(page, animationSpec = pageAnim) }
    }

    fun save() {
        if (name.isBlank()) return
        val med = (existing ?: Medication(groupId = 0, name = "", cycleStartEpochDay = today())).copy(
            name = name.trim().ifBlank { if (Lang.code == "en") "Untitled" else "Без названия" },
            comment = comment.trim(),
            dosesPerIntake = amount.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.01) ?: 1.0,
            timesPerDay = times,
            intervalMinutes = interval,
            everyNDays = days,
            firstDoseOffsetMinutes = offset,
            active = true,
            form = form.trim().ifBlank { MED_FORMS.first() },
            doseInfo = doseInfo.trim(),
            asNeeded = asNeeded,
            durationDays = duration,
            linkedToMedId = linkedTo,
            linkedDelayMinutes = (linkedDelay.toIntOrNull() ?: 120).coerceIn(1, 24 * 60),
            stockCount = stockText.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0),
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
                            Text(
                                s.stepOf(pager.currentPage + 1, PAGE_COUNT),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (pager.currentPage == 0) onDone() else goTo(pager.currentPage - 1)
                            },
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                        }
                    },
                    actions = {
                        // Иконки вместо текста: три текстовые кнопки не влезали в шапку.
                        if (!isNew) {
                            IconButton(onClick = { save() }, enabled = name.isNotBlank()) {
                                Icon(Icons.Default.Check, contentDescription = s.save)
                            }
                            IconButton(onClick = { vm.delete(medId) { onDone() } }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = s.delete)
                            }
                        }
                        IconButton(onClick = onDone) {
                            Icon(Icons.Default.Close, contentDescription = s.closeNoSave)
                        }
                    },
                )
                LinearProgressIndicator(
                    progress = { (pager.currentPage + 1f) / PAGE_COUNT },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        bottomBar = {
            // С открытой клавиатурой места мало — панель ужимается.
            val compact = WindowInsets.isImeVisible
            val buttonHeight = if (compact) 40.dp else 52.dp

            Row(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(
                        horizontal = 16.dp,
                        vertical = if (compact) 6.dp else 16.dp,
                    ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnimatedVisibility(visible = pager.currentPage > 0) {
                    OutlinedButton(
                        onClick = { goTo(pager.currentPage - 1) },
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        modifier = Modifier.height(buttonHeight),
                    ) {
                        Text(s.back)
                    }
                }
                Button(
                    onClick = {
                        if (pager.currentPage == PAGE_COUNT - 1) save() else goTo(pager.currentPage + 1)
                    },
                    enabled = name.isNotBlank(),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    modifier = Modifier.weight(1f).height(buttonHeight),
                ) {
                    Text(if (pager.currentPage == PAGE_COUNT - 1) s.save else s.next)
                }
            }
        },
    ) { padding ->
        if (!loaded) return@Scaffold

        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize().padding(padding),
            // Только кнопками: свайп по странице с полями ввода слишком легко поймать случайно.
            userScrollEnabled = false,
        ) { page ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (page) {
                    0 -> {
                        StepHeader(s.nameQ, s.nameBody)
                        if (libraryNames.isNotEmpty()) {
                            Text(
                                s.fromLibraryHint,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                libraryNames.forEach { n ->
                                    FilterChip(
                                        selected = name == n,
                                        onClick = { name = n },
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
                            singleLine = true,
                            colors = fieldColors(),
                            supportingText = {
                                if (name.isBlank()) Text(s.nameOptionalHint)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    1 -> {
                        StepHeader(s.formQ, s.formBody)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MED_FORMS.forEachIndexed { index, f ->
                                FilterChip(
                                    selected = form == f && !formIsCustom,
                                    onClick = {
                                        form = f
                                        formIsCustom = false
                                    },
                                    label = { Text(s.forms.getOrElse(index) { f }) },
                                )
                            }
                            FilterChip(
                                selected = formIsCustom,
                                onClick = {
                                    formIsCustom = true
                                    form = ""
                                },
                                label = { Text(s.otherForm) },
                            )
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

                    2 -> {
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

                    3 -> {
                        StepHeader(s.amountQ, s.amountBody)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("0.25", "0.5", "1", "2", "3").forEach { v ->
                                FilterChip(
                                    selected = amount.replace(',', '.') == v,
                                    onClick = { amount = v },
                                    label = { Text(v) },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { amount = it },
                            label = { Text(s.amountLabel) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = doseInfo,
                            onValueChange = { doseInfo = it },
                            label = { Text(s.doseInfoLabel) },
                            placeholder = { Text(s.doseInfoPlaceholder) },
                            singleLine = true,
                            colors = fieldColors(),
                            supportingText = { Text(s.doseInfoSupport) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = stockText,
                            onValueChange = { stockText = it },
                            label = { Text(s.stockLabel) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            supportingText = { Text(s.stockSupport) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    4 -> {
                        StepHeader(s.freqQ, s.freqBody)
                        SectionCard(s.asNeededTitle) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        s.asNeededBody,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Switch(checked = asNeeded, onCheckedChange = { asNeeded = it })
                            }
                        }
                        if (!asNeeded) {
                            SectionCard(s.perDaySection) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(1, 2, 3, 4).forEach { n ->
                                        FilterChip(
                                            selected = times == n,
                                            onClick = { timesPerDay = n.toString() },
                                            label = { Text(n.toString()) },
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = timesPerDay,
                                    onValueChange = { timesPerDay = it },
                                    label = { Text(s.otherNumber) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            SectionCard(s.everyNSection) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(
                                        1 to s.everyDayChip,
                                        2 to s.everyOtherDayChip,
                                        3 to s.every3DaysChip,
                                    ).forEach { (n, label) ->
                                        FilterChip(
                                            selected = days == n,
                                            onClick = { everyNDays = n.toString() },
                                            label = { Text(label) },
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = everyNDays,
                                    onValueChange = { everyNDays = it },
                                    label = { Text(s.otherPeriodLabel) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            HintCard(s.scheduleResult(s.schedule(times, interval, days)))
                        }
                    }

                    5 -> {
                        StepHeader(s.durationQ, s.durationBody)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                0 to s.durUnlimited,
                                7 to s.durWeek,
                                14 to s.dur2Weeks,
                                30 to s.durMonth,
                            ).forEach { (d, label) ->
                                FilterChip(
                                    selected = duration == d,
                                    onClick = { durationText = d.toString() },
                                    label = { Text(label) },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = durationText,
                            onValueChange = { durationText = it },
                            label = { Text(s.durationLabel) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (!isNew) {
                            HintCard(s.durationNote)
                            OutlinedButton(
                                onClick = { vm.finishCourse(medId) { onDone() } },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(s.finishCourseNow)
                            }
                        }
                    }

                    6 -> {
                        StepHeader(
                            s.intervalQ,
                            if (times > 1) s.intervalBodyMulti else s.intervalBodySingle,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                120 to "2 " + hourShort(),
                                180 to "3 " + hourShort(),
                                240 to "4 " + hourShort(),
                                360 to "6 " + hourShort(),
                                480 to "8 " + hourShort(),
                            ).forEach { (m, label) ->
                                FilterChip(
                                    selected = interval == m,
                                    onClick = {
                                        intervalHours = (m / 60).toString()
                                        intervalMinutes = (m % 60).toString()
                                    },
                                    label = { Text(label) },
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = intervalHours,
                                onValueChange = { intervalHours = it },
                                label = { Text(s.hoursLabel) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = intervalMinutes,
                                onValueChange = { intervalMinutes = it },
                                label = { Text(s.minutesLabel) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    7 -> {
                        StepHeader(s.firstDoseQ, s.firstDoseBody)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = linkedTo == null,
                                onClick = { linkedTo = null },
                                label = { Text(s.fromWake) },
                            )
                            FilterChip(
                                selected = linkedTo != null,
                                onClick = {
                                    if (otherMeds.isNotEmpty()) linkedTo = otherMeds.first().id
                                },
                                label = { Text(s.afterOtherPill) },
                                enabled = otherMeds.isNotEmpty(),
                            )
                        }

                        if (linkedTo == null) {
                            if (otherMeds.isEmpty()) {
                                HintCard(s.linkNoMeds)
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    0 to s.immediately,
                                    15 to "+15",
                                    30 to "+30",
                                    45 to "+45",
                                    60 to "+60",
                                    120 to "+120",
                                ).forEach { (m, label) ->
                                    FilterChip(
                                        selected = offset == m,
                                        onClick = { offsetMinutes = m.toString() },
                                        label = { Text(label) },
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = offsetMinutes,
                                onValueChange = { offsetMinutes = it },
                                label = { Text(s.offsetLabel) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            SectionCard(s.linkPickLabel) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    otherMeds.forEach { med ->
                                        FilterChip(
                                            selected = linkedTo == med.id,
                                            onClick = { linkedTo = med.id },
                                            label = { Text(med.name) },
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = linkedDelay,
                                onValueChange = { linkedDelay = it },
                                label = { Text(s.linkDelayLabel) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
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

private fun hourShort(): String = if (Lang.code == "en") "h" else "ч"

@Composable
private fun StepHeader(title: String, body: String) {
    Spacer(Modifier.height(8.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Text(
        body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(4.dp))
}

/** Именованная группа настроек: заголовок и содержимое в одной карточке. */
@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
private fun HintCard(text: String) {
    Card(Modifier.fillMaxWidth()) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp),
        )
    }
}

/**
 * Подсказка в пустом поле должна читаться как подсказка, а не как уже введённый текст,
 * поэтому она заметно бледнее обычного значения.
 */
@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
)

/** Итог на последнем шаге: во сколько встанут приёмы, если проснуться в 8 утра. */
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
                    append(name.ifBlank { if (Lang.code == "en") "Untitled" else "Без названия" })
                    append(" · ")
                    append(s.perIntake(amountText))
                    append(" · ")
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
                Text(
                    comment,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!asNeeded && linkedName == null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    s.summaryPreview(previewTimes(times, interval, offset)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

private fun trimNumber(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
