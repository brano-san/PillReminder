package tech.unispace.pillreminder.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import tech.unispace.pillreminder.data.Settings
import tech.unispace.pillreminder.widget.PillWidgetProvider
import tech.unispace.pillreminder.widget.PillWidgetWideProvider
import tech.unispace.pillreminder.widget.WidgetStyle

/**
 * Экран «Виджет»: добавить на рабочий стол, предпросмотр на светлых и тёмных обоях, палитра фона,
 * прозрачность и цвет текста. Предпросмотр показывает реальные ближайшие приёмы — тот же
 * [PillWidgetProvider.loadData], что рисует сам виджет; без данных — пример с пометкой.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetSettingsScreen(onBack: () -> Unit) {
    val s = Lang.s
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val snackbars = remember { SnackbarHostState() }
    val manager = remember { AppWidgetManager.getInstance(context) }
    // Стиль виджета: каждое изменение сразу уходит на рабочий стол (в фоне — refresh читает базу).
    var widgetColor by remember { mutableStateOf(settings.widgetColor) }
    var widgetOpacity by remember { mutableStateOf(settings.widgetOpacity) }
    var widgetText by remember { mutableStateOf(settings.widgetText) }
    var lines by remember { mutableStateOf<List<PillWidgetProvider.Companion.WidgetLine>>(emptyList()) }
    var sample by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        val data = PillWidgetProvider.loadData(context)
        val real = data.lines.filter { it.main.isNotBlank() && data.nextDoseId >= 0 }
        if (real.isNotEmpty()) {
            lines = real
            sample = false
        }
    }
    var pickWidget by remember { mutableStateOf(false) }
    if (pickWidget) {
        // Вариантов виджета два — даём выбрать, какой закрепить.
        // Оба варианта — в теле диалога, кнопка снизу одна и она отменяет: раньше обе видимые
        // кнопки закрепляли виджет, и отменить можно было только промахом мимо окна.
        AlertDialog(
            onDismissRequest = { pickWidget = false },
            title = { Text(s.pickWidgetTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            pickWidget = false
                            manager.requestPinAppWidget(ComponentName(context, PillWidgetWideProvider::class.java), null, null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(s.widgetWideName, maxLines = 1, softWrap = false) }
                    FilledTonalButton(
                        onClick = {
                            pickWidget = false
                            manager.requestPinAppWidget(ComponentName(context, PillWidgetProvider::class.java), null, null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(s.widgetNarrowName, maxLines = 1, softWrap = false) }
                }
            },
            confirmButton = { TextButton(onClick = { pickWidget = false }) { Text(s.cancel) } },
        )
    }

    SettingsSubScreen(s.widgetCard, onBack, snackbars) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Widgets, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Text(s.widgetCardSub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                if (manager.isRequestPinAppWidgetSupported) {
                    FilledTonalButton(onClick = { pickWidget = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(s.widgetAdd, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Text(s.widgetUnsupported, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                val lightText = WidgetStyle.lightText(widgetColor, widgetText)
                val primaryText = Color(WidgetStyle.textColor(primary = true, light = lightText))
                val secondaryText = Color(WidgetStyle.textColor(primary = false, light = lightText))
                Text(s.widgetStyleTitle, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(s.widgetStyleBody, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                // Предпросмотр на двухцветной подложке: светлая и тёмная половины — образцы обоев, а не цвет темы.
                // Подписи стоят внутри половин, поля широкие: узкая светлая кайма снаружи виджета читалась
                // как оторванная «белая дуга». Состав — как у широкого виджета: строки, подстрока, кнопка.
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))) {
                    Row(Modifier.matchParentSize()) {
                        Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFFDCDCDC)))
                        Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF2B2B2B)))
                    }
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
                            Text(s.widgetPreviewLight, style = MaterialTheme.typography.labelSmall, color = Color(0xFF555555), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                            Text(s.widgetPreviewDark, style = MaterialTheme.typography.labelSmall, color = Color(0xFFBBBBBB), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                                .background(Color(widgetColor).copy(alpha = widgetOpacity / 100f), RoundedCornerShape(14.dp))
                                .padding(12.dp),
                        ) {
                            Text(s.widgetTitle, style = MaterialTheme.typography.labelSmall, color = secondaryText)
                            if (sample) {
                                Text(s.widgetPreviewLine1, fontWeight = FontWeight.SemiBold, color = primaryText, maxLines = 1)
                                Text(s.mealAfterNow, style = MaterialTheme.typography.labelSmall, color = secondaryText, maxLines = 1)
                                Text(s.widgetPreviewLine2, color = primaryText, maxLines = 1)
                            } else {
                                lines.forEachIndexed { i, line ->
                                    Text(line.main, fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal, color = primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    line.sub?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .background(if (lightText) Color(0x33FFFFFF) else Color(0x1A000000), RoundedCornerShape(12.dp))
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(s.widgetTake, style = MaterialTheme.typography.labelLarge, color = primaryText, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
                if (sample) {
                    Spacer(Modifier.height(4.dp))
                    Text(s.widgetPreviewSample, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                Text(s.widgetColorTitle, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                // Палитра кружками: цвет виден сразу, подпись нужна только TalkBack.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    WidgetStyle.presets.forEachIndexed { i, argb ->
                        val selected = widgetColor == argb
                        val name = s.widgetColorNames.getOrElse(i) { "" }
                        Box(
                            Modifier
                                .size(38.dp)
                                .background(Color(argb), CircleShape)
                                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                                .clip(CircleShape)
                                .clickable {
                                    widgetColor = argb
                                    settings.widgetColor = argb
                                    // Текст сразу под цвет: на тёмном фоне светлый, на светлом тёмный; руками можно переключить ниже.
                                    widgetText = WidgetStyle.textModeFor(argb)
                                    settings.widgetText = widgetText
                                    PillWidgetProvider.refreshAsync(context)
                                }
                                .semantics { contentDescription = name },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(WidgetStyle.textColor(primary = true, light = WidgetStyle.luminance(argb) < 0.5)),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Ползунок — про прозрачность (так думает пользователь), хранится непрозрачность.
                Text(s.widgetTransparency(100 - widgetOpacity), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = (100 - widgetOpacity).toFloat(),
                    onValueChange = {
                        widgetOpacity = (100 - it.roundToInt()).coerceIn(0, 100)
                        settings.widgetOpacity = widgetOpacity
                    },
                    onValueChangeFinished = { PillWidgetProvider.refreshAsync(context) },
                    valueRange = 0f..100f,
                    steps = 9,
                )
                Text(s.widgetTextTitle, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        WidgetStyle.TEXT_LIGHT to s.widgetTextLight,
                        WidgetStyle.TEXT_DARK to s.widgetTextDark,
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = widgetText == mode,
                            onClick = {
                                widgetText = mode
                                settings.widgetText = mode
                                PillWidgetProvider.refreshAsync(context)
                            },
                            label = { Text(label, maxLines = 1, softWrap = false) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(s.widgetTextHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
