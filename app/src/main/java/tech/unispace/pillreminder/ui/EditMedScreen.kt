@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package tech.unispace.pillreminder.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material3.OutlinedIconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalTime
import tech.unispace.pillreminder.data.MEAL_NOW
import tech.unispace.pillreminder.data.MEAL_WITH
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Info
import tech.unispace.pillreminder.data.MED_FORMS
import tech.unispace.pillreminder.data.apartFromList
import tech.unispace.pillreminder.data.byClock
import tech.unispace.pillreminder.data.fixedTimesList
import tech.unispace.pillreminder.data.isExpiredOn
import tech.unispace.pillreminder.data.Medication
import tech.unispace.pillreminder.data.today
import tech.unispace.pillreminder.data.weekdaysList
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import java.time.DayOfWeek
import java.time.format.TextStyle

/** Шаги мастера: что пьём · как и сколько принимать · курс и контроль (со сводкой и кнопкой «Готово»). */
private enum class Step { WHAT, HOW, COURSE }

/** Связь с едой — один селектор: «до» и «после» одновременно выбрать нельзя. */
private enum class MealMode { NONE, BEFORE, WITH, AFTER }

/** Блок, к которому прокручивает тап по метке карточки-превью; [TOP] — начало шага. */
private enum class Anchor { TOP, FORM, DOSE, AMOUNT, TIMES, MODE, PERIOD, MEAL, ADVANCED, COURSE, STOCK }

/** Факт о лекарстве для превью и быстрого сохранения: текст, шаг и блок, где его правят. */
private data class Fact(val text: String, val step: Step, val anchor: Anchor = Anchor.TOP)

/** Пресеты «штук за приём»; остальное — за чипом «другое…». */
private val AMOUNT_PRESETS = listOf("0.25", "0.5", "1", "2", "3")

/** Частота приёма на шаге «как часто»: дни недели — отдельный режим, а не «раз в N дней». */
private enum class PeriodMode { DAILY, EVERY_OTHER, WEEKDAYS, INTERVAL }

/** Пресеты промежутка между приёмами, минуты. */
private val INTERVAL_PRESETS = listOf(180, 240, 300, 360, 480, 720)

/** Пресеты смещения первого приёма от подъёма, минуты; остальное — за чипом «своё время…». */
private val OFFSET_PRESETS = listOf(0, 30, 60, 120, 360, 720)

/** Пресеты задержки после другой таблетки, минуты. */
private val LINK_DELAY_PRESETS = listOf(30, 60, 120, 240)

/**
 * Чипы аккуратной сеткой: по [columns] в ряд, одинаковой ширины — россыпь чипов разной длины
 * в карточке выглядела неряшливо.
 */
@Composable
private fun ChipGrid(columns: Int, content: @Composable FlowRowScope.() -> Unit) {
    // У чипа M3 невидимая зона касания 48 dp при высоте 32: между рядами выходило 8 + 16 dp, между колонками 8.
    // Зону убираем — отступы по обеим осям одинаковые и меньше.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            maxItemsInEachRow = columns,
            content = content,
        )
    }
}

@Composable
private fun FlowRowScope.GridChip(
    selected: Boolean,
    label: String,
    modifier: Modifier = Modifier.weight(1f),
    enabled: Boolean = true,
    /** Карусель — только для названий таблеток: у пресетов вроде «не ближе 30 мин» бегущая строка отвлекает. */
    marquee: Boolean = false,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (marquee) {
                    MarqueeText(label, textAlign = TextAlign.Center)
                } else {
                    Text(label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        },
        modifier = modifier,
    )
}

/** Спойлер редких настроек расписания: заголовок с шестерёнкой, подзаголовок — что внутри. */
@Composable
private fun AdvancedSpoiler(open: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, highlighted: Boolean = false) {
    val s = Lang.s
    Card(modifier.fillMaxWidth().clickable(onClick = onToggle), colors = highlightColors(highlighted), border = highlightBorder(highlighted)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.advancedSpoiler, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(s.advancedSpoilerSub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val pageAnim = tween<Float>(durationMillis = 150)

/** Сколько имён из каталога показывать чипами над полем названия: остальное — через «Открыть каталог». */
private const val LIBRARY_CHIPS_MAX = 8

/** Часов бодрствования, за которые должны уложиться все приёмы дня; дальше — предупреждение в итоге. */
private const val DAY_SPAN_MINUTES = 16 * 60

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
    weekdays: List<Int>,
    duration: Int,
    byClock: Boolean,
    clockTimes: List<Int>,
    asNeeded: Boolean,
    stock: String,
    afterMeal: Int,
    beforeMeal: Int,
    mealCalories: Int = 0,
    linkedName: String? = null,
    linkedDelay: Int = 0,
): List<Fact> {
    val s = Lang.s
    // Тот же разбор и запас, что в save(): предпросмотр не должен обещать «0 таблеток» при сохранённых 0,01.
    val amountValue = amount.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.01) ?: 1.0
    // Пустая своя форма сохраняется как «Таблетка» — так и показываем.
    val formShown = s.formName(form.ifBlank { MED_FORMS.first() })
    val formValue = form.ifBlank { MED_FORMS.first() }
    // Число не введено — единица одна не печатается: «мг × 1 таб.» было ошибкой.
    val doseInfo = if (doseValue.isBlank()) "" else (doseValue.trim() + " " + doseUnit.trim()).trim()
    // Каждый факт знает свой шаг и блок: метка превью ведёт туда, где её правят.
    return buildList {
        // Форму на карточке показывает иконка; словами — только своя форма.
        if (formValue !in MED_FORMS) add(Fact(formShown, Step.WHAT, Anchor.FORM))
        add(
            if (doseInfo.isNotBlank()) Fact(s.amountFact(amountValue, formValue, doseInfo), Step.WHAT, Anchor.DOSE)
            else Fact(s.amountFact(amountValue, formValue, doseInfo), Step.HOW, Anchor.AMOUNT),
        )
        s.mealRelationParts(afterMeal, beforeMeal, mealCalories).forEach { add(Fact(it, Step.HOW, Anchor.MEAL)) }
        when {
            asNeeded -> add(Fact(s.asNeededShort, Step.HOW))
            byClock -> add(Fact(s.byClockShort + " " + clockTimes.sorted().joinToString(", ") { hhmmText(it) }, Step.HOW, Anchor.MODE))
            linkedName != null -> add(Fact(s.afterMed(linkedName, s.duration(linkedDelay)), Step.HOW, Anchor.ADVANCED))
            else -> add(Fact(s.schedule(times, interval, everyNDays, weekdays), Step.HOW, Anchor.TIMES))
        }
        if (duration > 0) add(Fact(s.durationLabelShort(duration), Step.COURSE, Anchor.COURSE))
        if (stock.isNotBlank()) add(Fact(s.stockLeft(stock), Step.COURSE, Anchor.STOCK))
    }
}

/**
 * Выбор промежутка: пресеты списком плюс «своё…». Отдельным компонентом, потому что
 * используется и для «после еды», и для «до еды»; подпись «сразу» у них разная.
 */
@Composable
private fun MinutesPicker(value: Int, label: String, nowLabel: String, onPick: (Int) -> Unit) {
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
            value = if (value == MEAL_NOW) nowLabel else s.duration(value.coerceAtLeast(1)),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            presets.forEach { m ->
                DropdownMenuItem(
                    text = { Text(if (m == MEAL_NOW) nowLabel else s.duration(m)) },
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

/**
 * Времена «по часам» по умолчанию: равномерно между 9:00 и 21:00, а не «с 9:00 через промежуток»
 * с переходом за полночь (три приёма давали 01:00). Промежуток намеренно не участвует.
 */
fun defaultClockTimes(times: Int, intervalMinutes: Int = 0): List<Int> {
    val n = times.coerceIn(1, 24)
    if (n == 1) return listOf(9 * 60)
    val start = 9 * 60
    val end = 21 * 60
    val step = (end - start) / (n - 1)
    return (0 until n).map { start + it * step }.distinct().sorted()
}

private fun hhmmText(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

/** Пара полей «часов / минут» с одним значением в минутах — для смещения от подъёма и задержки связки. */
@Composable
private fun HoursMinutesFields(hours: String, minutes: String, onHours: (String) -> Unit, onMinutes: (String) -> Unit) {
    val s = Lang.s
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = hours,
            onValueChange = { onHours(it.filter { c -> c.isDigit() }.take(2)) },
            label = { Text(s.hoursLabel) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            colors = fieldColors(),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = minutes,
            onValueChange = { onMinutes(it.filter { c -> c.isDigit() }.take(2)) },
            label = { Text(s.minutesLabel) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            colors = fieldColors(),
            modifier = Modifier.weight(1f),
        )
    }
}

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
    var periodMode by remember { mutableStateOf(PeriodMode.DAILY) }
    var weekdaySet by remember { mutableStateOf(setOf(1, 3, 5)) }
    // Поля «другое…» открываются только по запросу — иначе под чипами висели служебные «1» и «0».
    var customTimes by remember { mutableStateOf(false) }
    var customInterval by remember { mutableStateOf(false) }
    var customDuration by remember { mutableStateOf(false) }
    var customAmount by remember { mutableStateOf(false) }
    var customOffset by remember { mutableStateOf(false) }
    var customLinkDelay by remember { mutableStateOf(false) }
    // Якорь первого приёма и разнос — редкие настройки, за спойлером; раскрыт, если они уже заданы.
    var advancedOpen by remember { mutableStateOf(false) }
    var mealMode by remember { mutableStateOf(MealMode.NONE) }
    // Курс у «по необходимости» — за спойлером: ограничивать его неделями нужно редко.
    var courseOpen by remember { mutableStateOf(false) }
    // Смещение первого приёма от подъёма — часами и минутами, чтобы «вечером» не было «720».
    var offsetHours by remember { mutableStateOf("0") }
    var offsetMins by remember { mutableStateOf("0") }
    var form by remember { mutableStateOf(MED_FORMS.first()) }
    var formIsCustom by remember { mutableStateOf(false) }
    var doseValue by remember { mutableStateOf("") }
    var doseUnit by remember { mutableStateOf(s.doseUnits.first()) }
    var unitIsCustom by remember { mutableStateOf(false) }
    var asNeeded by remember { mutableStateOf(false) }
    var durationText by remember { mutableStateOf("0") }
    var linkedTo by remember { mutableStateOf<Long?>(null) }
    // Задержка связки — тоже часами и минутами, как и смещение от подъёма.
    var linkDelayHours by remember { mutableStateOf("2") }
    var linkDelayMins by remember { mutableStateOf("0") }
    var linkParentGone by remember { mutableStateOf(false) }
    var stockText by remember { mutableStateOf("") }
    // Расписание «по часам»: пустой список = приёмы считаются от кнопки «я проснулся».
    var byClock by remember { mutableStateOf(false) }
    var clockTimes by remember { mutableStateOf(listOf(9 * 60)) }
    var editTimeIndex by remember { mutableStateOf<Int?>(null) }
    var confirmQuickSave by remember { mutableStateOf(false) }
    // Такая таблетка уже в расписании: предупреждаем, но не запрещаем — схема может быть намеренно особой.
    var confirmDuplicate by remember { mutableStateOf(false) }
    var activeNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }
    var showRecommend by remember { mutableStateOf(false) }
    var photoPromptFor by remember { mutableStateOf<Long?>(null) }
    // Ограничения по еде и по соседству с другими таблетками; 0 — не важно.
    var afterMeal by remember { mutableIntStateOf(0) }
    var apartOthers by remember { mutableIntStateOf(0) }
    var beforeMeal by remember { mutableIntStateOf(0) }
    // Минимум калорий в еде — подсказка при правиле «после еды»; пусто — не задано.
    var mealCaloriesText by remember { mutableStateOf("") }
    var apartIds by remember { mutableStateOf(emptySet<Long>()) }
    var otherMeds by remember { mutableStateOf<List<Medication>>(emptyList()) }
    var libraryNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var libraryEntries by remember { mutableStateOf<List<MedLibraryEntry>>(emptyList()) }
    var existing by remember { mutableStateOf<Medication?>(null) }

    LaunchedEffect(medId) {
        // Родителем связки может быть только таблетка с расписанием: «по необходимости» приёмов не планирует.
        val active = vm.activeMeds()
        activeNames = active.filter { it.id != medId }.map { it.name }
        otherMeds = active.filter { it.id != medId && it.linkedToMedId == null && !it.asNeeded }
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
                val savedWeekdays = med.weekdaysList()
                periodMode = when {
                    savedWeekdays.isNotEmpty() -> PeriodMode.WEEKDAYS
                    med.everyNDays <= 1 -> PeriodMode.DAILY
                    med.everyNDays == 2 -> PeriodMode.EVERY_OTHER
                    else -> PeriodMode.INTERVAL
                }
                if (savedWeekdays.isNotEmpty()) weekdaySet = savedWeekdays.toSet()
                customTimes = med.timesPerDay !in 1..4 && !med.byClock
                customInterval = med.timesPerDay > 1 && med.intervalMinutes !in INTERVAL_PRESETS
                customDuration = med.durationDays !in listOf(0, 7, 14, 30)
                offsetHours = (med.firstDoseOffsetMinutes / 60).toString()
                offsetMins = (med.firstDoseOffsetMinutes % 60).toString()
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
                // Родитель удалён — связь мёртвая: чип был бы выбран без таблетки, а приёмы никогда не запланировались бы.
                if (med.linkedToMedId != null && otherMeds.none { it.id == med.linkedToMedId }) {
                    linkedTo = null
                    linkParentGone = true
                } else {
                    linkedTo = med.linkedToMedId
                }
                linkDelayHours = (med.linkedDelayMinutes / 60).toString()
                linkDelayMins = (med.linkedDelayMinutes % 60).toString()
                // Дробный остаток (9,5 после половинки) не обрезаем до целого.
                stockText = med.stockCount?.let { trimNumber(it) } ?: ""
                byClock = med.byClock
                clockTimes = med.fixedTimesList().ifEmpty { listOf(9 * 60) }
                afterMeal = med.afterMealMinutes
                apartOthers = med.apartFromOthersMinutes
                beforeMeal = med.beforeMealMinutes
                mealCaloriesText = if (med.mealCalories > 0) med.mealCalories.toString() else ""
                mealMode = when {
                    med.beforeMealMinutes > 0 -> MealMode.BEFORE
                    med.afterMealMinutes == MEAL_WITH -> MealMode.WITH
                    med.afterMealMinutes > 0 -> MealMode.AFTER
                    else -> MealMode.NONE
                }
                customAmount = trimNumber(med.dosesPerIntake) !in AMOUNT_PRESETS
                customOffset = med.firstDoseOffsetMinutes !in OFFSET_PRESETS
                customLinkDelay = med.linkedToMedId != null && med.linkedDelayMinutes !in LINK_DELAY_PRESETS
                advancedOpen = med.firstDoseOffsetMinutes > 0 || med.linkedToMedId != null || med.apartFromOthersMinutes > 0
                courseOpen = med.durationDays > 0
                apartIds = med.apartFromList().toSet()
            }
            loaded = true
        }
    }

    /**
     * Смена числа приёмов подставляет промежуток по схеме; пользователь может поправить руками.
     * В режиме «по часам» времена дополняются или урезаются, а не пересобираются: выставленные
     * 08:00 и 20:00 не должны пропадать от нажатия «3».
     */
    fun setTimes(raw: String) {
        timesPerDay = raw
        val n = raw.toIntOrNull() ?: return
        val m = defaultIntervalMinutes(n)
        if (m != null) {
            intervalHours = (m / 60).toString()
            intervalMinutes = (m % 60).toString()
        }
        if (byClock && n in 1..24) {
            clockTimes = when {
                n < clockTimes.size -> clockTimes.take(n)
                n > clockTimes.size -> (clockTimes + defaultClockTimes(n).filter { it !in clockTimes }).take(n).sorted()
                else -> clockTimes
            }
        }
    }
    val timesRaw = timesPerDay.toIntOrNull()
    val times = (timesRaw ?: 1).coerceIn(1, 24)
    val timesInvalid = timesRaw == null || timesRaw !in 1..24
    val intervalRaw = (intervalHours.toIntOrNull() ?: 0) * 60 + (intervalMinutes.toIntOrNull() ?: 0)
    // Пустой или слишком короткий промежуток не превращаем молча в 5 минут: подсвечиваем и подставляем схему.
    val intervalInvalid = times > 1 && !byClock && !asNeeded && intervalRaw < 5
    val interval = if (intervalRaw < 5) defaultIntervalMinutes(times) ?: 240 else intervalRaw
    val offset = ((offsetHours.toIntOrNull() ?: 0) * 60 + (offsetMins.toIntOrNull() ?: 0)).coerceIn(0, 24 * 60)
    val daysRaw = everyNDays.toIntOrNull()
    // Число дней имеет смысл только в режиме «свой интервал»; остальные режимы задают его сами.
    val days = when (periodMode) {
        PeriodMode.DAILY, PeriodMode.WEEKDAYS -> 1
        PeriodMode.EVERY_OTHER -> 2
        PeriodMode.INTERVAL -> (daysRaw ?: 1).coerceIn(1, 365)
    }
    val daysInvalid = periodMode == PeriodMode.INTERVAL && (daysRaw == null || daysRaw !in 1..365)
    val weekdayList = if (periodMode == PeriodMode.WEEKDAYS) weekdaySet.sorted() else emptyList()
    val weekdaysInvalid = !asNeeded && periodMode == PeriodMode.WEEKDAYS && weekdaySet.isEmpty()
    val duration = (durationText.toIntOrNull() ?: 0).coerceIn(0, 3650)
    val amountValue = amount.replace(',', '.').toDoubleOrNull()
    val amountInvalid = amountValue == null || amountValue < 0.01
    val linkedDelayMinutes = ((linkDelayHours.toIntOrNull() ?: 0) * 60 + (linkDelayMins.toIntOrNull() ?: 0)).coerceIn(1, 24 * 60)
    val linkedName = otherMeds.firstOrNull { it.id == linkedTo }?.name
    // Курс отсчитывается с сегодня, если его не было или он уже истёк: иначе «неделя» для таблетки,
    // которую пьют 40 дней, закончилась бы мгновенно.
    val courseStart = existing?.takeIf { it.durationDays > 0 && !it.isExpiredOn(today()) }?.cycleStartEpochDay ?: today()
    val courseRestarts = existing != null && duration > 0 && (existing!!.durationDays == 0 || existing!!.isExpiredOn(today()))
    val canProceed = name.isNotBlank() && !amountInvalid && !intervalInvalid && !timesInvalid && !daysInvalid && !weekdaysInvalid

    val steps = Step.entries
    val pager = rememberPagerState(pageCount = { steps.size })
    val scope = rememberCoroutineScope()
    val page = pager.currentPage
    val isLast = page == steps.lastIndex
    // Прокрутка каждого шага — своя и явная: тап по метке превью должен довести до нужного блока,
    // а не только перелистнуть страницу (пользователь был внизу шага 3, блок — вверху шага 2).
    val scrollStates = remember { List(steps.size) { ScrollState(0) } }
    val anchors = remember { mutableStateMapOf<Anchor, Int>() }
    var pendingAnchor by remember { mutableStateOf<Pair<Int, Anchor>?>(null) }
    // Блок, к которому только что прокрутили, полторы секунды подсвечен — видно, куда именно привёл тап.
    var highlightAnchor by remember { mutableStateOf<Anchor?>(null) }
    LaunchedEffect(highlightAnchor) {
        if (highlightAnchor != null) {
            delay(1600)
            highlightAnchor = null
        }
    }
    val anchorGap = with(LocalDensity.current) { 12.dp.roundToPx() }
    // Писать в state-карту только при изменении: onGloballyPositioned зовётся на каждом кадре прокрутки,
    // и запись того же значения десятью карточками подряд давала лишние рекомпозиции — мастер начинал лагать.
    fun anchorMod(anchor: Anchor) = Modifier.onGloballyPositioned {
        val y = it.positionInParent().y.roundToInt()
        if (anchors[anchor] != y) anchors[anchor] = y
    }

    fun goTo(target: Int, anchor: Anchor? = null) {
        val t = target.coerceIn(0, steps.lastIndex)
        if (anchor != null) {
            if (anchor == Anchor.ADVANCED) advancedOpen = true
            pendingAnchor = t to anchor
        }
        scope.launch { pager.animateScrollToPage(t, animationSpec = pageAnim) }
    }
    // Координата блока известна только после раскладки новой страницы — ждём её, потом прокручиваем.
    val anchorY = pendingAnchor?.let { (_, a) -> if (a == Anchor.TOP) 0 else anchors[a] }
    LaunchedEffect(pendingAnchor, anchorY, page) {
        val (target, anchor) = pendingAnchor ?: return@LaunchedEffect
        if (page != target || anchorY == null) return@LaunchedEffect
        scrollStates[target].animateScrollTo((anchorY - anchorGap).coerceAtLeast(0))
        pendingAnchor = null
        if (anchor != Anchor.TOP) highlightAnchor = anchor
    }

    editTimeIndex?.let { idx ->
        val cur = clockTimes.getOrElse(idx) { 12 * 60 }
        TimeWheelDialog(
            initial = LocalTime.of(cur / 60, cur % 60),
            onPick = { t ->
                val m = t.hour * 60 + t.minute
                // Индекс за концом списка — добавление нового времени; число приёмов следует за списком.
                clockTimes = (if (idx < clockTimes.size) clockTimes.toMutableList().also { it[idx] = m } else clockTimes + m).distinct().sorted()
                timesPerDay = clockTimes.size.toString()
            },
            onDismiss = { editTimeIndex = null },
        )
    }

    /** Таблетка из текущих полей — общая для сохранения и для проверки «есть ли несохранённые правки». */
    fun buildMed(): Medication {
        val doseInfo = if (doseValue.isBlank()) "" else (doseValue.trim() + " " + doseUnit.trim()).trim()
        return (existing ?: Medication(groupId = 0, name = "", cycleStartEpochDay = today())).copy(
            name = name.trim(),
            comment = comment.trim(),
            dosesPerIntake = amountValue?.coerceAtLeast(0.01) ?: 1.0,
            timesPerDay = if (byClock && !asNeeded) clockTimes.size else times,
            fixedTimes = if (byClock && !asNeeded) clockTimes.sorted().joinToString(",") else "",
            afterMealMinutes = afterMeal,
            beforeMealMinutes = beforeMeal,
            mealCalories = if (afterMeal > 0) mealCaloriesText.toIntOrNull() ?: 0 else 0,
            apartFromOthersMinutes = apartOthers,
            apartFromMedIds = if (apartOthers > 0) apartIds.joinToString(",") else "",
            intervalMinutes = interval,
            everyNDays = days,
            weekdays = weekdayList.joinToString(","),
            firstDoseOffsetMinutes = offset,
            cycleStartEpochDay = if (duration > 0) courseStart else (existing?.cycleStartEpochDay ?: today()),
            active = true,
            form = form.trim().ifBlank { MED_FORMS.first() },
            doseInfo = doseInfo,
            asNeeded = asNeeded,
            durationDays = duration,
            linkedToMedId = linkedTo,
            linkedDelayMinutes = linkedDelayMinutes,
            stockCount = stockText.replace(',', '.').toDoubleOrNull(),
        )
    }
    val dirty = if (isNew) name.isNotBlank() || comment.isNotBlank() else existing?.let { buildMed() != it } ?: false

    val duplicateName = activeNames.firstOrNull { it.trim().equals(name.trim(), ignoreCase = true) }

    fun save() {
        if (!canProceed) return
        val med = buildMed()
        vm.save(med) { savedId ->
            // Новая таблетка заводится и в каталоге — предлагаем сразу добавить фото упаковки, если его ещё нет.
            if (isNew) {
                scope.launch {
                    val entry = vm.libraryEntries().firstOrNull { it.name.equals(med.name, ignoreCase = true) }
                    if (entry != null && entry.photoUri == null) photoPromptFor = entry.id else onDone()
                }
            } else {
                onDone()
            }
        }
    }

    /** Сохранить, но если такое название уже в расписании — сначала спросить. */
    fun saveChecked() {
        if (duplicateName != null) confirmDuplicate = true else save()
    }

    /** Закрыть мастер: с несохранёнными правками — через подтверждение. */
    fun requestClose() {
        if (dirty) confirmDiscard = true else onDone()
    }

    // Системная «назад»: на шаг назад, как стрелка в шапке; с первого шага — как крестик.
    BackHandler { if (page > 0) goTo(page - 1) else requestClose() }

    if (showRecommend) {
        AlertDialog(
            onDismissRequest = { showRecommend = false },
            title = { Text(s.recommendTitle) },
            text = { Text(s.recommendBody) },
            confirmButton = { TextButton(onClick = { showRecommend = false }) { Text(s.done) } },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(s.discardTitle) },
            text = { Text(s.discardBody) },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onDone() }) { Text(s.closeNoSave) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(s.cancel) } },
        )
    }
    if (confirmDuplicate) {
        AlertDialog(
            onDismissRequest = { confirmDuplicate = false },
            title = { Text(s.duplicateTitle) },
            text = { Text(s.duplicateBody(duplicateName ?: name.trim())) },
            confirmButton = { TextButton(onClick = { confirmDuplicate = false; save() }) { Text(s.duplicateAdd) } },
            dismissButton = { TextButton(onClick = { confirmDuplicate = false }) { Text(s.cancel) } },
        )
    }
    if (confirmDelete) {
        ConfirmDeleteDialog(title = name, onConfirm = { vm.delete(medId) { onDone() } }, onDismiss = { confirmDelete = false })
    }
    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text(s.finishCourseNow) },
            text = { Text(s.finishCourseBody) },
            confirmButton = { TextButton(onClick = { confirmFinish = false; vm.finishCourse(medId) { onDone() } }) { Text(s.finishCourseNow) } },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text(s.cancel) } },
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
                            weekdays = weekdayList,
                            duration = duration,
                            byClock = byClock,
                            clockTimes = clockTimes,
                            asNeeded = asNeeded,
                            stock = stockText,
                            afterMeal = afterMeal,
                            beforeMeal = beforeMeal,
                            mealCalories = mealCaloriesText.toIntOrNull() ?: 0,
                            linkedName = linkedName,
                            linkedDelay = linkedDelayMinutes,
                        ).forEach { fact ->
                            Text(
                                fact.text,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { confirmQuickSave = false; saveChecked() }) { Text(s.save) } },
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
                        IconButton(onClick = { if (page == 0) requestClose() else goTo(page - 1) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                        }
                    },
                    actions = {
                        if (!isNew) {
                            IconButton(onClick = { saveChecked() }, enabled = canProceed) { Icon(Icons.Default.Check, contentDescription = s.save) }
                            // Корзина рядом с галочкой: удаление только через подтверждение.
                            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.DeleteOutline, contentDescription = s.delete) }
                        }
                        IconButton(onClick = { requestClose() }) { Icon(Icons.Default.Close, contentDescription = s.closeNoSave) }
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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // «Назад» — только стрелка: подпись дублировала стрелку в шапке и не оставляла места «Далее».
                // Именно if: скрытый AnimatedVisibility всё равно добавлял отступ на первом шаге.
                if (page > 0) {
                    OutlinedIconButton(onClick = { goTo(page - 1) }, modifier = Modifier.size(buttonHeight)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                    }
                }
                // Быстрый путь для новой таблетки: имя есть — можно сохранить с дефолтами, не листая мастер.
                // При редактировании эта кнопка лишняя: галочка в шапке сохраняет сразу.
                if (!isLast && isNew && name.isNotBlank()) {
                    OutlinedButton(
                        onClick = { confirmQuickSave = true },
                        enabled = canProceed,
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.weight(1f).height(buttonHeight),
                    ) { Text(s.quickSaveBtn, maxLines = 1, softWrap = false) }
                }
                Button(
                    onClick = { if (isLast) saveChecked() else goTo(page + 1) },
                    enabled = canProceed,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.weight(1f).height(buttonHeight),
                ) { Text(if (isLast) s.done else s.next, maxLines = 1, softWrap = false) }
            }
        },
    ) { padding ->
        if (!loaded) return@Scaffold

        // Все шаги остаются в композиции: переход «превью → блок → превью» не пересобирает тяжёлый шаг 2 каждый раз,
        // а координаты блоков известны заранее — прокрутка к ним без ожидания раскладки.
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize().padding(padding),
            userScrollEnabled = false,
            beyondViewportPageCount = steps.lastIndex,
        ) { index ->
            val step = steps[index]
            Column(
                Modifier.fillMaxSize().verticalScroll(scrollStates[index]).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (step) {
                    Step.WHAT -> {
                        StepHeader(s.stepWhatQ, s.stepWhatBody)
                        if (libraryNames.isNotEmpty()) {
                            Text(s.fromLibraryHint, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            // Не весь каталог чипами: иначе поле названия уезжает за экран.
                            val shownNames = libraryNames.take(LIBRARY_CHIPS_MAX)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                shownNames.forEach { n ->
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
                                                    // Дозировка без единицы («500») не должна выбирать «другое» с пустым полем.
                                                    if (u.isNotBlank()) {
                                                        doseUnit = u
                                                        unitIsCustom = u !in s.doseUnits
                                                    }
                                                }
                                            }
                                        },
                                        label = { MarqueeText(n) },
                                    )
                                }
                            }
                            if (libraryNames.size > shownNames.size) {
                                Text(
                                    s.moreInLibrary(libraryNames.size - shownNames.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
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
                            supportingText = {
                                when {
                                    name.isBlank() -> Text(s.nameOptionalHint)
                                    duplicateName != null -> Text(s.duplicateHint, color = MaterialTheme.colorScheme.error)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedButton(onClick = onOpenLibrary, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(s.openLibrary, maxLines = 1, softWrap = false)
                        }
                                            SectionCard(s.formSection, anchorMod(Anchor.FORM), highlightAnchor == Anchor.FORM) {
                            // Восемь вариантов выпадающим списком: сетка чипов занимала три ряда ради одного выбора,
                            // который делают один раз и почти всегда оставляют «Таблетка».
                            var formMenu by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(expanded = formMenu, onExpandedChange = { formMenu = it }) {
                                OutlinedTextField(
                                    value = if (formIsCustom) s.otherForm else s.forms.getOrElse(MED_FORMS.indexOf(form)) { form },
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(s.formSection) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = formMenu) },
                                    colors = fieldColors(),
                                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                )
                                ExposedDropdownMenu(expanded = formMenu, onDismissRequest = { formMenu = false }) {
                                    MED_FORMS.forEachIndexed { i, f ->
                                        DropdownMenuItem(
                                            text = { Text(s.forms.getOrElse(i) { f }) },
                                            onClick = {
                                                form = f
                                                formIsCustom = false
                                                formMenu = false
                                            },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(s.otherForm) },
                                        onClick = {
                                            formIsCustom = true
                                            form = ""
                                            formMenu = false
                                        },
                                    )
                                }
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
                        // Дозировка: число + единица чипами, а не свободный текст.
                        SectionCard(s.doseSection, anchorMod(Anchor.DOSE), highlightAnchor == Anchor.DOSE) {
                            // Единица не дублируется справа от поля: она и так выбрана чипом ниже, а поле
                            // из-за неё было уже сетки пресетов — границы не совпадали.
                            OutlinedTextField(
                                value = doseValue,
                                onValueChange = { doseValue = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                                label = { Text(s.doseValueLabel) },
                                placeholder = { Text("500") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                colors = fieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            // Восемь единиц и «другая…» — сетка 3×3.
                            ChipGrid(columns = 3) {
                                s.doseUnits.forEach { u ->
                                    GridChip(selected = doseUnit == u && !unitIsCustom, label = u) { doseUnit = u; unitIsCustom = false }
                                }
                                GridChip(selected = unitIsCustom, label = s.otherUnit) { unitIsCustom = true; doseUnit = "" }
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
                    }

                    Step.HOW -> {
                        StepHeader(s.stepHowQ, s.stepHowBody)
                        SectionCard(s.asNeededTitle) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s.asNeededBody, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(12.dp))
                                Switch(checked = asNeeded, onCheckedChange = { asNeeded = it })
                            }
                        }
                            // Штук за приём: пресеты покрывают почти всех, поле — только за чипом «другое…».
                            SectionCard(s.amountSection, anchorMod(Anchor.AMOUNT), highlightAnchor == Anchor.AMOUNT) {
                                // 3×2: шесть чипов в один ряд ужимали «0.25» до «0.…».
                                ChipGrid(columns = 3) {
                                    AMOUNT_PRESETS.forEach { v ->
                                        GridChip(selected = amount.replace(',', '.') == v && !customAmount, label = v) {
                                            customAmount = false
                                            amount = v
                                        }
                                    }
                                    GridChip(selected = customAmount, label = s.otherChip) { customAmount = true }
                                }
                                if (customAmount) {
                                    OutlinedTextField(
                                        value = amount,
                                        onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                                        label = { Text(s.amountLabel) },
                                        isError = amountInvalid,
                                        supportingText = { if (amountInvalid) Text(s.amountInvalid) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        colors = fieldColors(),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        if (!asNeeded) {
                            // Поля ввода — только по запросу («Другое…»): готовые варианты покрывают почти всех,
                            // а открытое поле с «1» или «0 — без ограничения» читается как служебная надпись.
                            SectionCard(s.perDaySection, anchorMod(Anchor.TIMES), highlightAnchor == Anchor.TIMES) {
                                // «другое…» — чипом в сетке, как у «штук за приём»; в ряду из пяти оно резалось до «Д…»,
                                // поэтому три колонки и чип на две из них.
                                ChipGrid(columns = 3) {
                                    listOf(1, 2, 3, 4).forEach { n ->
                                        GridChip(selected = times == n && !timesInvalid && !customTimes, label = n.toString()) {
                                            customTimes = false
                                            setTimes(n.toString())
                                        }
                                    }
                                    GridChip(selected = customTimes, label = s.otherChip, modifier = Modifier.weight(2f)) { customTimes = true }
                                }
                                if (customTimes) {
                                    OutlinedTextField(
                                        value = timesPerDay,
                                        onValueChange = { setTimes(it.filter { c -> c.isDigit() }.take(2)) },
                                        label = { Text(s.otherNumber) },
                                        isError = timesInvalid,
                                        supportingText = { if (timesInvalid) Text(s.rangeHint(1, 24)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        colors = fieldColors(),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            SectionCard(s.scheduleSection, anchorMod(Anchor.MODE), highlightAnchor == Anchor.MODE) {
                                Text(s.scheduleBody, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ChipGrid(columns = 2) {
                                    GridChip(selected = !byClock, label = s.modeWake) { byClock = false }
                                    GridChip(selected = byClock, label = s.modeClock) {
                                        byClock = true
                                        // По умолчанию одно время — 09:00; остальные добавляют кнопкой «Добавить время».
                                        clockTimes = listOf(9 * 60)
                                        timesPerDay = "1"
                                        // «По часам» не совместимо со связкой: время задаётся явно.
                                        linkedTo = null
                                    }
                                }
                                if (byClock) {
                                    Text(s.clockTimesTitle, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                    // Время можно поправить, убрать и добавить прямо здесь; число приёмов следует за списком.
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        clockTimes.forEachIndexed { i, m ->
                                            InputChip(
                                                selected = false,
                                                onClick = { editTimeIndex = i },
                                                label = { Text(hhmmText(m), maxLines = 1, softWrap = false) },
                                                trailingIcon = if (clockTimes.size > 1) {
                                                    {
                                                        Icon(
                                                            Icons.Default.Close,
                                                            contentDescription = s.removeTime,
                                                            modifier = Modifier.size(16.dp),
                                                        )
                                                    }
                                                } else {
                                                    null
                                                },
                                            )
                                        }
                                        if (clockTimes.size < 24) {
                                            AssistChip(
                                                onClick = { editTimeIndex = clockTimes.size },
                                                label = { Text(s.addTime, maxLines = 1, softWrap = false) },
                                            )
                                        }
                                    }
                                    if (clockTimes.size > 1) {
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            clockTimes.forEach { m ->
                                                AssistChip(
                                                    onClick = {
                                                        clockTimes = (clockTimes - m).ifEmpty { listOf(m) }
                                                        timesPerDay = clockTimes.size.toString()
                                                    },
                                                    label = { Text(s.removeTime + " " + hhmmText(m), maxLines = 1, softWrap = false) },
                                                )
                                            }
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
                                    ChipGrid(columns = 3) {
                                        INTERVAL_PRESETS.forEach { m ->
                                            GridChip(selected = intervalRaw == m && !customInterval, label = s.duration(m)) {
                                                customInterval = false
                                                intervalHours = (m / 60).toString()
                                                intervalMinutes = (m % 60).toString()
                                            }
                                        }
                                        GridChip(selected = customInterval, label = s.otherChip) { customInterval = true }
                                    }
                                    if (customInterval) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            OutlinedTextField(
                                                value = intervalHours,
                                                onValueChange = { intervalHours = it.filter { c -> c.isDigit() }.take(2) },
                                                label = { Text(s.hoursLabel) },
                                                isError = intervalInvalid,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                colors = fieldColors(),
                                                modifier = Modifier.weight(1f),
                                            )
                                            OutlinedTextField(
                                                value = intervalMinutes,
                                                onValueChange = { intervalMinutes = it.filter { c -> c.isDigit() }.take(2) },
                                                label = { Text(s.minutesLabel) },
                                                isError = intervalInvalid,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                colors = fieldColors(),
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                    if (intervalInvalid) {
                                        Text(s.intervalInvalid, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            // Частота: каждый день · через день · по дням недели · свой интервал. Дни недели —
                            // отдельный режим, а не «раз в N дней»: метотрексат «пн, ср, пт» интервалом не описать.
                            SectionCard(s.everyNSection, anchorMod(Anchor.PERIOD), highlightAnchor == Anchor.PERIOD) {
                                ChipGrid(columns = 2) {
                                    GridChip(selected = periodMode == PeriodMode.DAILY, label = s.everyDayChip) { periodMode = PeriodMode.DAILY }
                                    GridChip(selected = periodMode == PeriodMode.EVERY_OTHER, label = s.everyOtherDayChip) { periodMode = PeriodMode.EVERY_OTHER }
                                    GridChip(selected = periodMode == PeriodMode.WEEKDAYS, label = s.periodWeekdaysChip) { periodMode = PeriodMode.WEEKDAYS }
                                    GridChip(selected = periodMode == PeriodMode.INTERVAL, label = s.periodIntervalChip) { periodMode = PeriodMode.INTERVAL }
                                }
                                if (periodMode == PeriodMode.WEEKDAYS) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        (1..7).forEach { d ->
                                            val on = d in weekdaySet
                                            Box(
                                                Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                                    .clickable { weekdaySet = if (on) weekdaySet - d else weekdaySet + d },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, s.locale).replaceFirstChar { it.uppercase() },
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                )
                                            }
                                        }
                                    }
                                    if (weekdaySet.isEmpty()) {
                                        Text(s.weekdaysEmpty, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                                if (periodMode == PeriodMode.INTERVAL) {
                                    OutlinedTextField(
                                        value = everyNDays,
                                        onValueChange = { everyNDays = it.filter { c -> c.isDigit() }.take(3) },
                                        label = { Text(s.otherPeriodLabel) },
                                        isError = daysInvalid,
                                        supportingText = { if (daysInvalid) Text(s.rangeHint(1, 365)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        colors = fieldColors(),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                        // Связь с едой — один селектор: таблетку нельзя пить и «до», и «после» одновременно,
                        // а две независимые карточки позволяли выбрать оба.
                        SectionCard(s.mealSection, anchorMod(Anchor.MEAL), highlightAnchor == Anchor.MEAL) {
                            ChipGrid(columns = 2) {
                                GridChip(selected = mealMode == MealMode.NONE, label = s.mealModeNone) { mealMode = MealMode.NONE; beforeMeal = 0; afterMeal = 0 }
                                GridChip(selected = mealMode == MealMode.BEFORE, label = s.mealModeBefore) {
                                    mealMode = MealMode.BEFORE
                                    afterMeal = 0
                                    if (beforeMeal <= 0) beforeMeal = 30
                                }
                                GridChip(selected = mealMode == MealMode.WITH, label = s.mealModeWith) { mealMode = MealMode.WITH; beforeMeal = 0; afterMeal = MEAL_WITH }
                                GridChip(selected = mealMode == MealMode.AFTER, label = s.mealModeAfter) {
                                    mealMode = MealMode.AFTER
                                    beforeMeal = 0
                                    if (afterMeal <= 0 || afterMeal == MEAL_WITH) afterMeal = 30
                                }
                            }
                            when (mealMode) {
                                MealMode.BEFORE -> {
                                    // Честно: «до еды» — подсказка, время напоминания от неё не зависит.
                                    Text(s.mealBeforeBody, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    MinutesPicker(value = beforeMeal, label = s.mealBeforeLabel, nowLabel = s.mealBeforeNowPick, onPick = { beforeMeal = it })
                                }
                                MealMode.AFTER, MealMode.WITH -> {
                                    Text(s.mealSectionBody, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (mealMode == MealMode.AFTER) {
                                        MinutesPicker(value = afterMeal, label = s.mealAfterLabel, nowLabel = s.mealImmediately, onPick = { afterMeal = it })
                                    }
                                    OutlinedTextField(
                                        value = mealCaloriesText,
                                        onValueChange = { mealCaloriesText = it.filter { c -> c.isDigit() }.take(4) },
                                        label = { Text(s.mealCaloriesLabel) },
                                        supportingText = { Text(s.mealCaloriesHint) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        colors = fieldColors(),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                MealMode.NONE -> Unit
                            }
                        }
                        if (!asNeeded) {
                        // Якорь первого приёма и разнос с другими таблетками — редкие настройки: обычная схема —
                        // «2 раза в день после еды». За спойлером, иначе шаг скроллился четыре экрана.
                        // «По необходимости» расписания не имеет, «по часам» стоит на своих временах —
                        // якорь и разнос им ни к чему; для «по часам» внутри спойлера одна поясняющая строка.
                        AdvancedSpoiler(open = advancedOpen, onToggle = { advancedOpen = !advancedOpen }, modifier = anchorMod(Anchor.ADVANCED), highlighted = highlightAnchor == Anchor.ADVANCED)
                        if (advancedOpen && byClock) HintCard(s.clockNoOffset)
                        if (advancedOpen && !byClock) {
                            SectionCard(s.firstDoseSection) {
                                Text(s.firstDoseBody, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (linkParentGone) {
                                    Text(s.linkParentGone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                                // По одному чипу в ряд: «после другой таблетки» не помещается в одну строку с соседом.
                                ChipGrid(columns = 1) {
                                    // Еда — не якорь, а условие («ждёт «Еда»»), поэтому живёт в секциях ниже.
                                    GridChip(selected = linkedTo == null, label = s.fromWake) { linkedTo = null }
                                    GridChip(selected = linkedTo != null, label = s.afterOtherPill, enabled = otherMeds.isNotEmpty()) {
                                        if (otherMeds.isNotEmpty()) linkedTo = otherMeds.first().id
                                    }
                                }
                                // Подсказка относится только к недоступному варианту, поэтому стоит под ним.
                                if (otherMeds.isEmpty()) {
                                    Text(
                                        s.linkNoMeds,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (linkedTo == null) {
                                    // Пресеты до «вечером»: одна таблетка в день вечером — два касания, а не «720».
                                    // Поля часов и минут — только за чипом «своё время…»: под нажатым «Сразу» висели «0» и «0».
                                    // Сетка 2 в ряд, как у «штук за приём»: «Вечером (+12 ч)» в узком чипе резалось.
                                    ChipGrid(columns = 2) {
                                        listOf(
                                            0 to s.offsetNow,
                                            30 to "+" + s.duration(30),
                                            60 to "+" + s.duration(60),
                                            120 to "+" + s.duration(120),
                                            360 to s.offsetDay,
                                            720 to s.offsetEvening,
                                        ).forEach { (m, label) ->
                                            GridChip(selected = offset == m && !customOffset, label = label) {
                                                customOffset = false
                                                offsetHours = (m / 60).toString()
                                                offsetMins = (m % 60).toString()
                                            }
                                        }
                                        GridChip(selected = customOffset, label = s.customOffsetChip) { customOffset = true }
                                    }
                                    if (customOffset) {
                                        Text(s.offsetCaption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        HoursMinutesFields(offsetHours, offsetMins, { offsetHours = it }, { offsetMins = it })
                                    }
                                } else {
                                    Text(s.linkPickLabel, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                    ChipGrid(columns = 2) {
                                        otherMeds.forEach { med ->
                                            GridChip(selected = linkedTo == med.id, label = med.name, marquee = true) { linkedTo = med.id }
                                        }
                                    }
                                    // Та же раскладка, что у смещения от подъёма: пресеты и часы/минуты, а не голые «120».
                                    ChipGrid(columns = 2) {
                                        LINK_DELAY_PRESETS.forEach { m ->
                                            GridChip(selected = linkedDelayMinutes == m && !customLinkDelay, label = "+" + s.duration(m)) {
                                                customLinkDelay = false
                                                linkDelayHours = (m / 60).toString()
                                                linkDelayMins = (m % 60).toString()
                                            }
                                        }
                                        GridChip(selected = customLinkDelay, label = s.customOffsetChip) { customLinkDelay = true }
                                    }
                                    if (customLinkDelay) {
                                        Text(s.linkDelayLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        HoursMinutesFields(linkDelayHours, linkDelayMins, { linkDelayHours = it }, { linkDelayMins = it })
                                    }
                                }
                            }
                            SectionCard(s.apartSection) {
                                Text(s.apartSectionBody, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ChipGrid(columns = 2) {
                                    GridChip(selected = apartOthers == 0, label = s.apartNo) { apartOthers = 0 }
                                    listOf(30, 60, 120).forEach { m ->
                                        GridChip(selected = apartOthers == m, label = s.apartFor(s.duration(m))) { apartOthers = m }
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
                                    ChipGrid(columns = 2) {
                                        GridChip(selected = apartIds.isEmpty(), label = s.apartAny) { apartIds = emptySet() }
                                        otherMeds.filter { it.id != linkedTo }.forEach { other ->
                                            GridChip(selected = other.id in apartIds, label = other.name, marquee = true) {
                                                apartIds = if (other.id in apartIds) apartIds - other.id else apartIds + other.id
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        }
                        // Итог — самым последним и целиком: частота, число приёмов и курс одной строкой.
                        HintCard(
                            s.scheduleResult(
                                listOf(
                                    when {
                                        asNeeded -> s.asNeededShort.replaceFirstChar { it.uppercase() }
                                        byClock -> s.periodWords(days, weekdayList).replaceFirstChar { it.uppercase() } + ", " +
                                            s.byClockShort + " " + clockTimes.sorted().joinToString(", ") { hhmmText(it) }
                                        else -> s.periodWords(days, weekdayList).replaceFirstChar { it.uppercase() } + ", " + s.schedule(times, interval, 1)
                                    },
                                    "(" + s.courseLabel(if (duration > 0) s.durationLabelShort(duration) else s.durUnlimited) + ")",
                                ).joinToString(" "),
                            ),
                        )
                                        }

                    Step.COURSE -> {
                        StepHeader(s.stepCourseQ, s.stepCourseBody)
                        // «По необходимости» ограничивают курс редко — карточка спрятана за спойлером.
                        if (asNeeded && !courseOpen) {
                            TextButton(onClick = { courseOpen = true }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                                Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(s.courseSpoiler, maxLines = 1, softWrap = false)
                            }
                        }
                        if (!asNeeded || courseOpen) {
                        // Курс — и для «по необходимости»: его тоже можно ограничить по дням и завершить досрочно.
                        SectionCard(s.durationQ, anchorMod(Anchor.COURSE), highlightAnchor == Anchor.COURSE) {
                            Text(s.durationBody, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ChipGrid(columns = 2) {
                                listOf(0 to s.durUnlimited, 7 to s.durWeek, 14 to s.dur2Weeks, 30 to s.durMonth).forEach { (d, label) ->
                                    GridChip(selected = duration == d && !customDuration, label = label) {
                                        customDuration = false
                                        durationText = d.toString()
                                    }
                                }
                            }
                            FilterChip(
                                selected = customDuration,
                                onClick = { customDuration = true },
                                label = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text(s.durCustomChip, maxLines = 1, softWrap = false) } },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (customDuration) {
                                OutlinedTextField(
                                    value = durationText,
                                    onValueChange = { durationText = it.filter { c -> c.isDigit() }.take(4) },
                                    label = { Text(s.durationLabel) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    colors = fieldColors(),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (duration > 0) {
                                // Дата конца видна сразу — иначе «неделя» для давней таблетки заканчивалась молча.
                                Text(
                                    s.courseEnds(shortDayText(courseStart + duration)) + if (courseRestarts) " " + s.courseRestart else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (!isNew) {
                                Text(s.durationNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedButton(onClick = { confirmFinish = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text(s.finishCourseNow, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                        }
                        SectionCard(s.stockSection, anchorMod(Anchor.STOCK), highlightAnchor == Anchor.STOCK) {
                            OutlinedTextField(
                                value = stockText,
                                onValueChange = { stockText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                                label = { Text(s.stockLabel) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                colors = fieldColors(),
                                supportingText = { Text(s.stockHintEmpty) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(s.stockSupport, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        // Вместо двух списков «Итог» и «Что будет на карточке» (они дублировали друг друга) —
                        // сама карточка таблетки в миниатюре, как на главном экране. Тап — на нужный шаг.
                        CardPreview(
                            name = name,
                            form = form,
                            comment = comment,
                            facts = previewFacts(
                                form = form,
                                doseValue = doseValue,
                                doseUnit = doseUnit,
                                amount = amount,
                                times = times,
                                interval = interval,
                                everyNDays = days,
                                weekdays = weekdayList,
                                duration = duration,
                                byClock = byClock,
                                clockTimes = clockTimes,
                                asNeeded = asNeeded,
                                stock = stockText,
                                afterMeal = afterMeal,
                                beforeMeal = beforeMeal,
                                mealCalories = mealCaloriesText.toIntOrNull() ?: 0,
                                linkedName = linkedName,
                                linkedDelay = linkedDelayMinutes,
                            ),
                            times = times,
                            interval = interval,
                            offset = offset,
                            asNeeded = asNeeded,
                            byClock = byClock,
                            clockTimes = clockTimes,
                            linkedName = linkedName,
                            onEdit = { step, anchor -> goTo(step.ordinal, anchor) },
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

/** Цвет карточки при подсветке «сюда прокрутили»: плавно к primaryContainer и обратно. */
@Composable
private fun highlightColors(highlighted: Boolean): androidx.compose.material3.CardColors {
    val base = CardDefaults.cardColors()
    val container by animateColorAsState(if (highlighted) MaterialTheme.colorScheme.primaryContainer else base.containerColor, label = "highlight")
    return base.copy(containerColor = container)
}

@Composable
private fun highlightBorder(highlighted: Boolean): BorderStroke? =
    if (highlighted) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null

@Composable
private fun SectionCard(title: String, modifier: Modifier = Modifier, highlighted: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), colors = highlightColors(highlighted), border = highlightBorder(highlighted)) {
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

/** Метка факта на карточке-превью — как InfoPill на главном, но с тапом на шаг, где факт правят. */
@Composable
private fun PreviewPill(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * Карточка таблетки в миниатюре — та же раскладка, что у карточки на главном экране: иконка формы,
 * название, время первого приёма, метки фактов, заметка, кнопки (неактивные). Тап по карточке — на шаг
 * «как принимать», по метке — к блоку, где её правят (страница + прокрутка), по карандашу — к названию.
 */
@Composable
private fun CardPreview(
    name: String,
    form: String,
    comment: String,
    facts: List<Fact>,
    times: Int,
    interval: Int,
    offset: Int,
    asNeeded: Boolean,
    byClock: Boolean,
    clockTimes: List<Int>,
    linkedName: String?,
    onEdit: (Step, Anchor) -> Unit,
) {
    val s = Lang.s
    val formValue = form.ifBlank { MED_FORMS.first() }
    // Предпросмотр времён — только для схемы «по промежутку»: у «по часам» и связки времена другие.
    val previewApplies = !asNeeded && linkedName == null && !byClock
    val timeText = when {
        asNeeded || linkedName != null -> null
        byClock -> clockTimes.minOrNull()?.let { hhmmText(it) }
        else -> hhmmText((8 * 60 + offset) % (24 * 60))
    }
    Text(s.cardPreviewTitle, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Card(Modifier.fillMaxWidth().clickable { onEdit(Step.HOW, Anchor.TOP) }) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(formIcon(formValue), contentDescription = s.formName(formValue), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                MarqueeText(
                    name.ifBlank { s.namePlaceholder },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onEdit(Step.WHAT, Anchor.TOP) }) { Icon(Icons.Default.Edit, contentDescription = s.edit) }
            }
            if (timeText != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(timeText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (byClock) s.byClockShort else s.previewWakeNote,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                facts.forEach { fact -> PreviewPill(fact.text) { onEdit(fact.step, fact.anchor) } }
            }
            if (comment.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(end = 8.dp)) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp).padding(top = 1.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(comment, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            // Кнопки — как на настоящей карточке, но неактивные: это предпросмотр, отмечать тут нечего.
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (asNeeded) {
                    Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f).height(44.dp)) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(s.takeNow, maxLines = 1, softWrap = false)
                    }
                } else {
                    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f).height(44.dp)) {
                        Text(s.skip, maxLines = 1, softWrap = false)
                    }
                    Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f).height(44.dp)) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(s.took, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
    if (previewApplies && times > 1) {
        Text(s.summaryPreview(previewTimes(times, interval, offset, s)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    // Последний приём за пределами дня останется ждать до следующего подъёма — лучше предупредить здесь.
    if (previewApplies && offset + (times - 1) * interval > DAY_SPAN_MINUTES) {
        Text(s.dayOverflowWarn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    Text(s.summaryTapHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** «08:00, 20:00, 08:00 (завтра)» — времена от условного подъёма в 8:00; переход через полночь помечен. */
private fun previewTimes(times: Int, interval: Int, offset: Int, s: S): String {
    val shown = (0 until times.coerceAtMost(6)).joinToString(", ") { k ->
        val minutes = 8 * 60 + offset + k * interval
        val h = (minutes / 60) % 24
        val m = minutes % 60
        h.toString().padStart(2, '0') + ":" + m.toString().padStart(2, '0') + if (minutes >= 24 * 60) " " + s.nextDayMark else ""
    }
    return if (times > 6) "$shown…" else shown
}

private fun trimNumber(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
