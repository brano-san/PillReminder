package tech.unispace.pillreminder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tech.unispace.pillreminder.data.Dose
import tech.unispace.pillreminder.data.DoseStatus

/** Что за узел на схеме дня. */
enum class TimelineKind { WAKE, PILL, MEAL, BED }

/** Состояние приёма на схеме; у подъёма, еды и сна — [NONE]. */
enum class TimelineState { NONE, TAKEN, PENDING, OVERDUE, SKIPPED }

data class TimelineNode(
    val kind: TimelineKind,
    val at: Long,
    /** Название таблетки; для остальных узлов подпись берётся из Lang. */
    val label: String = "",
    val state: TimelineState = TimelineState.NONE,
    /** Форма выпуска — для иконки таблетки. */
    val form: String = "",
)

/**
 * Узлы схемы дня по времени: подъём, приёмы (выпитые — по факту, остальные — по плану),
 * отметки «Еда» этого цикла и отход ко сну. Чистая функция — проверяется тестом.
 * Еда до подъёма и после отбоя к схеме дня не относится. Приём из [waitingIds] ждёт кнопку «Еда»
 * и просроченным не считается — как и на карточке.
 */
fun buildTimelineNodes(
    wakeAt: Long?,
    bedAt: Long?,
    doses: List<Dose>,
    formById: Map<Long, String>,
    meals: List<Long>,
    now: Long,
    waitingIds: Set<Long> = emptySet(),
): List<TimelineNode> = buildList {
    wakeAt?.let { add(TimelineNode(TimelineKind.WAKE, it)) }
    doses.forEach { d ->
        val state = when (d.status) {
            DoseStatus.TAKEN -> TimelineState.TAKEN
            DoseStatus.SKIPPED -> TimelineState.SKIPPED
            DoseStatus.PENDING -> if (d.plannedAt <= now && d.id !in waitingIds) TimelineState.OVERDUE else TimelineState.PENDING
        }
        val at = if (d.status == DoseStatus.TAKEN) d.takenAt ?: d.plannedAt else d.plannedAt
        add(TimelineNode(TimelineKind.PILL, at, d.medNameSnapshot, state, formById[d.medId] ?: ""))
    }
    val from = wakeAt ?: doses.minOfOrNull { it.plannedAt } ?: 0L
    meals.filter { it >= from && (bedAt == null || it <= bedAt) }
        .forEach { add(TimelineNode(TimelineKind.MEAL, it)) }
    bedAt?.let { add(TimelineNode(TimelineKind.BED, it)) }
}.sortedBy { it.at }

private val TL_GREEN = Color(0xFF4CAF50)
private val TL_RED = Color(0xFFE53935)

private val NODE_WIDTH = 64.dp
private val NODE_SIZE = 34.dp
private val CONNECTOR_MIN_WIDTH = 44.dp

/**
 * Схема дня цепочкой: `| Подъём — 30 мин — 💊 — 1 ч — 🍴 — сразу — 💊 …`.
 * Это последовательность, а не шкала времени: расстояния одинаковые, промежуток подписан.
 * Иначе три утренние таблетки слипались бы в точку, а до вечерней был бы пустой экран.
 * Ряд сам прокручивается к первому будущему узлу — вечером видно «сейчас», а не утро.
 */
@Composable
fun DayTimeline(nodes: List<TimelineNode>, now: Long, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    // Высота строки над узлами — от шрифта, а не 16 dp: при крупном системном шрифте подпись промежутка не режется,
    // а линия по-прежнему проходит через центры кружков.
    val header: Dp = with(density) { MaterialTheme.typography.labelSmall.lineHeight.toDp() }
    val firstFuture = nodes.indexOfFirst { it.at > now }.let { if (it < 0) nodes.lastIndex else it }
    LaunchedEffect(firstFuture, nodes.size) {
        val target = with(density) { ((NODE_WIDTH + CONNECTOR_MIN_WIDTH) * firstFuture - 100.dp).roundToPx() }
        scroll.animateScrollTo(target.coerceAtLeast(0))
    }
    Row(modifier.horizontalScroll(scroll), verticalAlignment = Alignment.Top) {
        nodes.forEachIndexed { i, node ->
            if (i > 0) {
                val prev = nodes[i - 1]
                TimelineConnector(
                    minutes = ((node.at - prev.at) / 60_000L).toInt(),
                    passed = node.at <= now,
                    nowInside = prev.at <= now && now < node.at,
                    header = header,
                )
            }
            // Просроченный приём — единственный узел, которому нужно внимание: его не приглушаем.
            TimelineNodeView(node, past = node.at <= now && node.state != TimelineState.OVERDUE, header = header)
        }
    }
}

@Composable
private fun TimelineNodeView(node: TimelineNode, past: Boolean, header: Dp) {
    val s = Lang.s
    val scheme = MaterialTheme.colorScheme
    val fill = when (node.state) {
        TimelineState.TAKEN -> TL_GREEN
        TimelineState.OVERDUE -> TL_RED
        TimelineState.SKIPPED -> scheme.outlineVariant
        TimelineState.PENDING -> scheme.surface
        TimelineState.NONE -> scheme.primaryContainer
    }
    val content = when (node.state) {
        TimelineState.TAKEN, TimelineState.OVERDUE -> Color.White
        TimelineState.SKIPPED -> scheme.onSurfaceVariant
        TimelineState.PENDING -> scheme.primary
        TimelineState.NONE -> scheme.onPrimaryContainer
    }
    val icon = when (node.kind) {
        TimelineKind.WAKE -> Icons.Default.WbSunny
        TimelineKind.MEAL -> Icons.Default.Restaurant
        TimelineKind.BED -> Icons.Default.Bedtime
        TimelineKind.PILL -> formIcon(node.form)
    }
    // Короткие подписи специально для схемы: «Отход ко сну» из журнала в 64 dp не помещается.
    val label = when (node.kind) {
        TimelineKind.WAKE -> s.eventWokeUp
        TimelineKind.MEAL -> s.timelineMeal
        TimelineKind.BED -> s.timelineBed
        TimelineKind.PILL -> node.label
    }
    val circle = Modifier
        .size(NODE_SIZE)
        .background(fill, CircleShape)
        .then(if (node.state == TimelineState.PENDING) Modifier.border(2.dp, scheme.primary, CircleShape) else Modifier)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(NODE_WIDTH).alpha(if (past) 0.65f else 1f),
    ) {
        Spacer(Modifier.height(header))
        Box(circle, contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = content, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(formatClock(node.at), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Линия между узлами с подписью промежутка; засечка — где сейчас. Растёт под длинную подпись («5 ч 30 мин»).
 * Ширина колонки обязана быть ограниченной: ряд прокручивается по горизонтали, максимум там бесконечный,
 * а `fillMaxWidth()` у линии без содержимого при бесконечном максимуме даёт нулевую ширину — линия исчезает.
 * `width(IntrinsicSize.Max)` фиксирует ширину по подписи, `widthIn(min)` держит минимум.
 */
@Composable
private fun TimelineConnector(minutes: Int, passed: Boolean, nowInside: Boolean, header: Dp) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(min = CONNECTOR_MIN_WIDTH).width(IntrinsicSize.Max),
    ) {
        Text(
            if (minutes >= 1) Lang.s.duration(minutes) else "",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.height(header).padding(horizontal = 4.dp),
        )
        Box(Modifier.fillMaxWidth().height(NODE_SIZE), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(if (passed) scheme.primary else scheme.outlineVariant))
            if (nowInside) Box(Modifier.width(2.dp).height(14.dp).background(scheme.error))
        }
    }
}
