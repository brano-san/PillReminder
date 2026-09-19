@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package tech.unispace.pillreminder.ui

import android.app.Activity
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.AssistChip
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.launch
import tech.unispace.pillreminder.alarm.AlarmActivity
import tech.unispace.pillreminder.alarm.AlarmScheduler
import tech.unispace.pillreminder.alarm.Notifications
import tech.unispace.pillreminder.alarm.VISIT_OFFSET_PRESETS
import tech.unispace.pillreminder.data.Medication
import tech.unispace.pillreminder.ui.theme.ThemeMode
import tech.unispace.pillreminder.widget.PillWidgetProvider
import tech.unispace.pillreminder.data.Settings as AppSettings
import java.time.LocalTime

// ---------- Системные проверки (используются и главным экраном) ----------

fun notificationsEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

fun exactAlarmsAllowed(context: Context): Boolean =
    AlarmScheduler(context).canScheduleExact()

fun isBatteryUnrestricted(context: Context): Boolean {
    val pm = context.getSystemService(PowerManager::class.java) ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

fun hasDndAccess(context: Context): Boolean =
    context.getSystemService(NotificationManager::class.java)
        ?.isNotificationPolicyAccessGranted == true

/** До Android 14 отдельного разрешения на полноэкранные уведомления нет. */
fun canUseFullScreenIntent(context: Context): Boolean =
    Build.VERSION.SDK_INT < 34 ||
        context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true

/** Всё ли обязательное для доставки уведомлений выдано. */
fun deliveryOk(context: Context): Boolean =
    notificationsEnabled(context) && exactAlarmsAllowed(context) && isBatteryUnrestricted(context)

private fun hhmm(m: Int) = "%02d:%02d".format(m / 60, m % 60)

// ---------- Меню настроек ----------

@Composable
fun SettingsMenuScreen(
    contentPadding: PaddingValues,
    onOpenRepeats: () -> Unit,
    onOpenSound: () -> Unit,
    onOpenDelivery: () -> Unit,
    onOpenVisits: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenStock: () -> Unit,
    onOpenCharts: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenTutorial: () -> Unit,
    onOpenWidget: () -> Unit,
    onOpenTips: () -> Unit,
    onOpenArchive: () -> Unit,
) {
    val s = Lang.s
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val delivery = deliveryOk(context)
    val primary = MaterialTheme.colorScheme.primary

    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(s.settingsTitle, style = MaterialTheme.typography.titleLarge)

        // Пункты сгруппированы: «Повторы напоминаний» прятали в себе тихие часы, «Отложить»
        // и напоминание «Подъём», а список из двенадцати одинаковых карточек читался наугад.
        SettingsGroup(s.setGroupReminders)
        SettingsNavCard(
            icon = { Icon(Icons.Default.Repeat, contentDescription = null, tint = primary) },
            title = s.repeatsCard,
            subtitle = (if (settings.repeatEnabled) s.repeatsOn(settings.repeatIntervalMinutes, settings.repeatCount) else s.repeatsOff) +
                " · " + s.repeatsCardMore,
            onClick = onOpenRepeats,
        )
        SettingsNavCard(
            icon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = primary) },
            title = s.soundCard,
            subtitle = (if (settings.alarmSound) s.soundAlarmShort else s.soundNormalShort) + " · " +
                (if (settings.fullScreenAlarm) s.fullScreenShort else s.notifShort),
            onClick = onOpenSound,
        )
        SettingsNavCard(
            icon = {
                Icon(
                    if (delivery) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (delivery) primary else MaterialTheme.colorScheme.error,
                )
            },
            title = s.deliveryCard,
            subtitle = if (delivery) s.deliveryOkSub else s.deliveryBadSub,
            onClick = onOpenDelivery,
        )
        SettingsNavCard(
            icon = { Icon(Icons.Default.MedicalServices, contentDescription = null, tint = primary) },
            title = s.visitsCard,
            subtitle = s.visitsCardSub(settings.visitOffsetsMinutes.size),
            onClick = onOpenVisits,
        )

        SettingsGroup(s.setGroupPills)
        SettingsNavCard(
            icon = { Icon(Icons.Default.Inventory2, contentDescription = null, tint = primary) },
            title = s.archiveCard,
            subtitle = s.archiveCardSub,
            onClick = onOpenArchive,
        )
        SettingsNavCard(
            icon = { Icon(Icons.Default.Shield, contentDescription = null, tint = primary) },
            title = s.privacyCard,
            // Подзаголовок — состояние, а не название настройки: «Скрывать названия» читалось как факт.
            subtitle = if (settings.privateNotifications) s.privacyOnSub else s.privacyOffSub,
            onClick = onOpenPrivacy,
        )
        SettingsNavCard(
            icon = { Icon(Icons.Default.Inventory, contentDescription = null, tint = primary) },
            title = s.stockCard,
            // «Таблетки заканчиваются» в меню выглядело как тревога при полных упаковках.
            subtitle = s.stockCardSub(settings.lowStockThreshold),
            onClick = onOpenStock,
        )

        SettingsGroup(s.setGroupData)
        SettingsNavCard(
            icon = { Icon(Icons.Default.Save, contentDescription = null, tint = primary) },
            title = s.backupCard,
            subtitle = s.backupCardSub,
            onClick = onOpenBackup,
        )
        SettingsNavCard(
            icon = { Icon(Icons.Default.Description, contentDescription = null, tint = primary) },
            title = s.reportCard,
            subtitle = s.reportCardSub,
            onClick = onOpenReport,
        )

        SettingsGroup(s.setGroupApp)
        SettingsNavCard(
            icon = { Icon(Icons.Default.Palette, contentDescription = null, tint = primary) },
            title = s.appearanceCard,
            subtitle = s.appearanceCardSub,
            onClick = onOpenCharts,
        )
        // Виджет — такой же пункт со стрелкой, как остальные; добавление и стиль живут на своём экране.
        SettingsNavCard(
            icon = { Icon(Icons.Default.Widgets, contentDescription = null, tint = primary) },
            title = s.widgetCard,
            subtitle = s.widgetCardSub,
            onClick = onOpenWidget,
        )
        SettingsNavCard(
            icon = { Icon(Icons.Default.School, contentDescription = null, tint = primary) },
            title = s.tutorialCard,
            subtitle = s.tutorialCardSub,
            onClick = onOpenTutorial,
        )
        // Советы жили только в блоке кнопок на главной — а его можно выключить, и они пропадали совсем.
        SettingsNavCard(
            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = primary) },
            title = s.tipsCard,
            subtitle = s.tipsCardSub,
            onClick = onOpenTips,
        )

        // «Что нового» — обычным пунктом меню: нажатие на строку версии внизу экрана
        // тоже работает, но найти его без подсказки было невозможно.
        var showChangelog by remember { mutableStateOf(false) }
        SettingsNavCard(
            icon = { Icon(Icons.Default.NewReleases, contentDescription = null, tint = primary) },
            title = s.changelogCard,
            subtitle = s.changelogCardSub,
            onClick = { showChangelog = true },
        )

        // Язык — переключается на месте.
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Language, contentDescription = null, tint = primary)
                Spacer(Modifier.width(16.dp))
                Text(s.languageCard, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                listOf("ru" to "RU", "en" to "EN").forEach { (code, label) ->
                    FilterChip(
                        selected = Lang.code == code,
                        onClick = {
                            Lang.code = code
                            settings.language = code
                            Notifications.createChannels(context)
                            // Виджет читает язык только при перерисовке — иначе он до получаса остаётся на старом.
                            PillWidgetProvider.refreshAsync(context)
                        },
                        label = { Text(label, maxLines = 1, softWrap = false) },
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        val versionName = remember {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
            } catch (_: Exception) {
                ""
            }
        }
        if (showChangelog) {
            AlertDialog(
                onDismissRequest = { showChangelog = false },
                title = { Text(s.changelogTitle) },
                text = {
                    // Сначала — только последний релиз: восемь версий и восемьдесят пунктов
                    // в одном окне читать невозможно, а интересна обычно свежая.
                    var allVersions by remember { mutableStateOf(false) }
                    Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        (if (allVersions) CHANGELOG else CHANGELOG.take(1)).forEach { release ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    release.version + " · " + release.date.ifBlank { s.versionUnreleased },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                (if (Lang.code == "en") release.en else release.ru).forEach { line ->
                                    Text("• " + line, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        if (!allVersions && CHANGELOG.size > 1) {
                            TextButton(onClick = { allVersions = true }) { Text(s.changelogShowAll) }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showChangelog = false }) { Text(s.done) } },
            )
        }
        Text(
            s.versionLabel(versionName),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable { showChangelog = true },
        )
        Spacer(Modifier.height(24.dp))
    }
}

/** Заголовок группы пунктов настроек: без них двенадцать одинаковых карточек читались списком без смысла. */
@Composable
private fun SettingsGroup(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}

@Composable
internal fun SettingsNavCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Каркас подэкрана настроек: шапка с «назад» и снекбар. */
@Composable
internal fun SettingsSubScreen(
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

// ---------- Повторы, отложить, тихие часы, напоминание проснуться ----------

/** Пресеты интервала повторов, минуты; остальное — за чипом «другое…». */
private val REPEAT_PRESETS = listOf(1, 3, 5, 10, 15)

@Composable
fun RepeatSettingsScreen(onBack: () -> Unit, vm: MainViewModel) {
    val s = Lang.s
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var repeatEnabled by remember { mutableStateOf(settings.repeatEnabled) }
    var repeatInterval by remember { mutableIntStateOf(settings.repeatIntervalMinutes) }
    var repeatIntervalText by remember { mutableStateOf(settings.repeatIntervalMinutes.toString()) }
    var repeatCount by remember { mutableIntStateOf(settings.repeatCount) }
    // Поле «свой интервал» — только за чипом «другое…»: под выбранными «3 мин» висело поле с «3».
    var customRepeat by remember { mutableStateOf(settings.repeatIntervalMinutes !in REPEAT_PRESETS) }
    var wakeRemind by remember { mutableStateOf(settings.wakeReminderEnabled) }
    var wakeRemindAt by remember { mutableIntStateOf(settings.wakeReminderMinutes) }
    var showWakePicker by remember { mutableStateOf(false) }
    var notifyLowStock by remember { mutableStateOf(settings.notifyLowStock) }
    var notifyCourseDone by remember { mutableStateOf(settings.notifyCourseDone) }
    var notifyDayDone by remember { mutableStateOf(settings.notifyDayDone) }
    val snackbars = remember { SnackbarHostState() }

    if (showWakePicker) {
        TimeWheelDialog(
            initial = LocalTime.of(wakeRemindAt / 60, wakeRemindAt % 60),
            onPick = {
                wakeRemindAt = it.hour * 60 + it.minute
                settings.wakeReminderMinutes = wakeRemindAt
                vm.rescheduleWakeReminder()
            },
            onDismiss = { showWakePicker = false },
        )
    }

    SettingsSubScreen(s.repeatTitle, onBack, snackbars) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SwitchRow(s.repeatTitle, s.repeatBody, repeatEnabled) {
                    repeatEnabled = it
                    settings.repeatEnabled = it
                }
                if (repeatEnabled) {
                    Spacer(Modifier.height(12.dp))
                    Text(s.repeatHowOften, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        REPEAT_PRESETS.forEach { m ->
                            FilterChip(
                                selected = repeatInterval == m && !customRepeat,
                                onClick = {
                                    customRepeat = false
                                    repeatInterval = m
                                    repeatIntervalText = m.toString()
                                    settings.repeatIntervalMinutes = m
                                },
                                label = { Text(s.duration(m)) },
                            )
                        }
                        FilterChip(selected = customRepeat, onClick = { customRepeat = true }, label = { Text(s.otherChip) })
                    }
                    if (customRepeat) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = repeatIntervalText,
                        onValueChange = { raw ->
                            // Не обрезаем молча: вне диапазона — ошибка поля, значение не сохраняется.
                            val digits = raw.filter { it.isDigit() }.take(3)
                            repeatIntervalText = digits
                            digits.toIntOrNull()?.takeIf { it in 1..120 }?.let { v ->
                                repeatInterval = v
                                settings.repeatIntervalMinutes = v
                            }
                        },
                        label = { Text(s.customIntervalLabel) },
                        isError = repeatIntervalText.toIntOrNull()?.let { it !in 1..120 } ?: repeatIntervalText.isNotEmpty(),
                        supportingText = { Text(s.rangeHint(1, 120)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(s.repeatHowMany, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 20, 50).forEach { n ->
                            FilterChip(
                                selected = repeatCount == n,
                                onClick = {
                                    repeatCount = n
                                    settings.repeatCount = n
                                },
                                label = { Text(n.toString()) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        s.repeatTotal(s.duration(repeatInterval * repeatCount)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // «Отложить» в уведомлении
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                var snoozeSet by remember { mutableStateOf(settings.snoozeOptions.toSet()) }
                Text(s.snoozeOptionsTitle, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    s.snoozeOptionsBody + " " + s.snoozeAtLeastOne,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 30, 60, 120).forEach { m ->
                        // Последний включённый вариант не снимается: кнопке «Отложить» нужен хотя бы один.
                        val last = snoozeSet.size == 1 && m in snoozeSet
                        FilterChip(
                            enabled = !last,
                            selected = m in snoozeSet,
                            onClick = {
                                val next = if (m in snoozeSet) snoozeSet - m else snoozeSet + m
                                // Хотя бы один вариант нужен: иначе кнопке «Отложить» нечего показать.
                                if (next.isNotEmpty()) {
                                    snoozeSet = next
                                    settings.snoozeOptions = next.toList()
                                    settings.snoozeMinutes = next.min()
                                }
                            },
                            label = { Text(s.duration(m), maxLines = 1, softWrap = false) },
                        )
                    }
                }
            }
        }

        // Тихие часы
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                var quiet by remember { mutableStateOf(settings.quietEnabled) }
                var quietFrom by remember { mutableIntStateOf(settings.quietFromMinutes) }
                var quietTo by remember { mutableIntStateOf(settings.quietToMinutes) }
                var showFrom by remember { mutableStateOf(false) }
                var showTo by remember { mutableStateOf(false) }
                if (showFrom) {
                    TimeWheelDialog(
                        initial = LocalTime.of(quietFrom / 60, quietFrom % 60),
                        onPick = { quietFrom = it.hour * 60 + it.minute; settings.quietFromMinutes = quietFrom },
                        onDismiss = { showFrom = false },
                    )
                }
                if (showTo) {
                    TimeWheelDialog(
                        initial = LocalTime.of(quietTo / 60, quietTo % 60),
                        onPick = { quietTo = it.hour * 60 + it.minute; settings.quietToMinutes = quietTo },
                        onDismiss = { showTo = false },
                    )
                }
                SwitchRow(s.quietTitle, s.quietBody, quiet) {
                    quiet = it
                    settings.quietEnabled = it
                }
                if (quiet) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { showFrom = true }, modifier = Modifier.weight(1f)) {
                            Text(s.quietFrom + " " + hhmm(quietFrom))
                        }
                        OutlinedButton(onClick = { showTo = true }, modifier = Modifier.weight(1f)) {
                            Text(s.quietTo + " " + hhmm(quietTo))
                        }
                    }
                }
            }
        }

        // Напоминание «я проснулся»
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WbSunny, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Text(s.wakeRemindCard, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(end = 16.dp))
                    Switch(
                        checked = wakeRemind,
                        onCheckedChange = {
                            wakeRemind = it
                            settings.wakeReminderEnabled = it
                            vm.rescheduleWakeReminder()
                        },
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(s.wakeRemindBody, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (wakeRemind) {
                    Spacer(Modifier.height(10.dp))
                    // Время — по центру блока, во всю ширину.
                    OutlinedButton(onClick = { showWakePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(s.wakeRemindTime + ": " + hhmm(wakeRemindAt))
                    }
                }
            }
        }

        // Служебные уведомления: напоминания о приёмах выключать нельзя (ради них приложение
        // и существует), а вот эти три человек вправе отключить, не отключая всё подряд.
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(s.notifySection, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                SwitchRow(s.notifyLowStockTitle, s.stockCardSub(settings.lowStockThreshold), notifyLowStock) {
                    notifyLowStock = it
                    settings.notifyLowStock = it
                }
                Spacer(Modifier.height(12.dp))
                SwitchRow(s.notifyCourseDoneTitle, s.courseDoneBodyPrivate, notifyCourseDone) {
                    notifyCourseDone = it
                    settings.notifyCourseDone = it
                }
                Spacer(Modifier.height(12.dp))
                SwitchRow(s.notifyDayDoneTitle, s.dayDoneBody, notifyDayDone) {
                    notifyDayDone = it
                    settings.notifyDayDone = it
                }
            }
        }
    }
}

// ---------- Звук и экран ----------

@Composable
fun SoundSettingsScreen(onBack: () -> Unit) {
    val s = Lang.s
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var alarmSound by remember { mutableStateOf(settings.alarmSound) }
    var fullScreenAlarm by remember { mutableStateOf(settings.fullScreenAlarm) }
    var refresh by remember { mutableIntStateOf(0) }
    // Тумблер «во весь экран» отправил в системные настройки за разрешением: по возвращении включаем его сами.
    var fsiPending by remember { mutableStateOf(false) }
    val overlayAllowed = remember(refresh) { Settings.canDrawOverlays(context) }

    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val say: (String) -> Unit = { message ->
        scope.launch {
            snackbars.currentSnackbarData?.dismiss()
            snackbars.showSnackbar(message)
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }
    LaunchedEffect(refresh) {
        val allowed = canUseFullScreenIntent(context)
        if (fsiPending) {
            fsiPending = false
            if (allowed) {
                fullScreenAlarm = true
                settings.fullScreenAlarm = true
            }
        }
        // Разрешения нет (отозвали или включили до этой версии) — тумблер не может быть включён:
        // иначе он обещает полноэкранный будильник, а придёт обычное уведомление.
        if (fullScreenAlarm && !allowed) {
            fullScreenAlarm = false
            settings.fullScreenAlarm = false
        }
    }

    var soundUri by remember { mutableStateOf(settings.soundUri) }
    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            settings.soundUri = uri?.toString()
            soundUri = settings.soundUri
            // Настройки канала неизменяемы — пересоздаём каналы под новую мелодию.
            settings.channelVersion = settings.channelVersion + 1
            Notifications.createChannels(context)
            say(s.soundSaved)
        }
    }
    // Название выбранной мелодии — чтобы было видно, что именно сейчас играет.
    val soundName = remember(soundUri) {
        soundUri?.let { runCatching { RingtoneManager.getRingtone(context, Uri.parse(it))?.getTitle(context) }.getOrNull() } ?: s.soundDefault
    }

    SettingsSubScreen(s.soundScreenTitle, onBack, snackbars) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SwitchRow(s.alarmSoundTitle, s.alarmSoundBody, alarmSound) {
                    alarmSound = it
                    settings.alarmSound = it
                }
                Spacer(Modifier.height(12.dp))
                Text(s.notifSoundTitle, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(s.notifSoundBody, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_NOTIFICATION)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, settings.soundUri?.let { Uri.parse(it) })
                        try {
                            ringtoneLauncher.launch(intent)
                        } catch (_: ActivityNotFoundException) {
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(s.pickSound, maxLines = 1, softWrap = false) }
                Spacer(Modifier.height(6.dp))
                Text(s.soundCurrent(soundName), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                // Одно действие вместо трёх: без разрешения (Android 14+) тумблер сам открывает системный экран
                // и включается только когда разрешение выдано. Предупреждение до включения не показываем.
                SwitchRow(s.fullScreenTitle, s.fullScreenBody, fullScreenAlarm) { on ->
                    if (on && !canUseFullScreenIntent(context)) {
                        fsiPending = true
                        safeLaunch(
                            launcher,
                            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                                .setData(Uri.parse("package:" + context.packageName)),
                            context,
                        ) { say(it) }
                    } else {
                        fullScreenAlarm = on
                        settings.fullScreenAlarm = on
                    }
                }
            }
        }

        if (fullScreenAlarm) {
            // Оформление как в чек-листе доставки: сразу видно, выдано разрешение или нет.
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (overlayAllowed) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (overlayAllowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(s.overlayTitle, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (overlayAllowed) s.overlayBody else s.overlayMissing,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overlayAllowed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(10.dp))
                    if (overlayAllowed) {
                        Text(s.overlayOk, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    } else {
                        FilledTonalButton(
                            onClick = {
                                // Через safeLaunch: не у каждой оболочки есть этот экран, без запасного варианта — падение.
                                safeLaunch(
                                    launcher,
                                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName)),
                                    context,
                                ) { say(it) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(s.overlayAllow, maxLines = 1, softWrap = false) }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(s.testSection, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(s.testHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = {
                        AlarmScheduler(context).scheduleTest(3_000)
                        say(s.testScheduled)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(s.testNormal) }
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = {
                        if (!canUseFullScreenIntent(context)) {
                            say(s.fsPermWarn)
                        } else {
                            AlarmScheduler(context).scheduleTest(3_000, fullScreen = true)
                            say(s.testFsScheduled)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(s.testFullScreen) }
                Spacer(Modifier.height(8.dp))
                Text(s.fsLockHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                // Прямой запуск экрана — чтобы отделить «не открывается система» от «сломан экран».
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(context, AlarmActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                .putExtra(AlarmActivity.EXTRA_TITLE, s.testFsTitle)
                                .putExtra(AlarmActivity.EXTRA_TEXT, s.testBody),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(s.fsOpenNow) }
            }
        }
    }
}

// ---------- Напоминания о врачах: пресеты + своё ----------

@Composable
fun VisitReminderSettingsScreen(onBack: () -> Unit, vm: MainViewModel) {
    val s = Lang.s
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var selected by remember { mutableStateOf(settings.visitOffsetsMinutes) }
    // Свои смещения живут отдельно от галочек: снятая галочка не должна стирать время.
    var custom by remember { mutableStateOf(settings.visitOffsetsCustom) }
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun apply(newSet: Set<Int>) {
        selected = newSet
        settings.visitOffsetsMinutes = newSet
        vm.rescheduleVisitAlarms()
    }

    // Пресеты + всё, что пользователь добавлял сам.
    val options = (VISIT_OFFSET_PRESETS + custom + selected).distinct().sortedDescending()

    SettingsSubScreen(s.visitRemindersTitle, onBack, snackbars) {
        Text(s.visitRemindersBody, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp)) {
                options.forEach { offset ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { apply(if (offset in selected) selected - offset else selected + offset) }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = offset in selected,
                            onCheckedChange = { checked -> apply(if (checked) selected + offset else selected - offset) },
                        )
                        Text(s.visitOffsetLabel(offset), modifier = Modifier.weight(1f))
                        // Крестик — только у своих сроков: встроенные «за неделю / за день / за 3 часа» отключаются
                        // галочкой, удалять их незачем. Снекбар с «Вернуть» — на случай промаха.
                        if (offset !in VISIT_OFFSET_PRESETS) {
                            IconButton(onClick = {
                                val wasSelected = offset in selected
                                custom = custom - offset
                                settings.visitOffsetsCustom = custom
                                apply(selected - offset)
                                scope.launch {
                                    snackbars.currentSnackbarData?.dismiss()
                                    val result = snackbars.showSnackbar(s.visitOffsetRemovedMsg, actionLabel = s.undo)
                                    if (result == SnackbarResult.ActionPerformed) {
                                        custom = custom + offset
                                        settings.visitOffsetsCustom = custom
                                        if (wasSelected) apply(selected + offset)
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Close, contentDescription = s.delete)
                            }
                        }
                    }
                }
            }
        }
        var showCustomPicker by remember { mutableStateOf(false) }
        if (showCustomPicker) {
            // Дни + часы, а не циферблат: напомнить могут и за трое суток.
            var daysText by remember { mutableStateOf("0") }
            var hoursText by remember { mutableStateOf("2") }
            val minutes = ((daysText.toIntOrNull() ?: 0) * 24 + (hoursText.toIntOrNull() ?: 0)) * 60
            AlertDialog(
                onDismissRequest = { showCustomPicker = false },
                title = { Text(s.visitCustomTitle) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = daysText,
                                onValueChange = { daysText = it.filter { c -> c.isDigit() }.take(3) },
                                label = { Text(s.daysField) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                colors = fieldColors(),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = hoursText,
                                onValueChange = { hoursText = it.filter { c -> c.isDigit() }.take(2) },
                                label = { Text(s.hoursField) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                colors = fieldColors(),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (minutes > 0) {
                            Text(s.visitOffsetLabel(minutes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = minutes > 0,
                        onClick = {
                            custom = custom + minutes
                            settings.visitOffsetsCustom = custom
                            apply(selected + minutes)
                            showCustomPicker = false
                        },
                    ) { Text(s.addBtn) }
                },
                dismissButton = { TextButton(onClick = { showCustomPicker = false }) { Text(s.cancel) } },
            )
        }
        OutlinedButton(onClick = { showCustomPicker = true }, modifier = Modifier.fillMaxWidth()) {
            Text(s.visitCustomAdd, maxLines = 1, softWrap = false)
        }
    }
}

// ---------- Конфиденциальность ----------

/**
 * Системный запрос отпечатка или кода устройства. Если экран открыт не из активити
 * (теоретически невозможно, но проверка дешёвая) — действие выполняется без запроса.
 */
fun promptDeviceLock(context: Context, onError: (String) -> Unit = {}, onSuccess: () -> Unit) {
    // Проверка входа не имеет права «пропустить» при неожиданном контексте: это защита медицинских данных.
    val activity = context as? FragmentActivity ?: return onError(Lang.s.lockCheckFailed)
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            // Сняли код блокировки, слишком много попыток — пользователь должен это увидеть.
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString())
            }
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(Lang.s.lockPrompt)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            .build(),
    )
}

/** Есть ли на устройстве отпечаток или код блокировки — без них замок включать нельзя. */
fun deviceLockAvailable(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
    ) == BiometricManager.BIOMETRIC_SUCCESS


@Composable
fun PrivacySettingsScreen(onBack: () -> Unit) {
    val s = Lang.s
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var private by remember { mutableStateOf(settings.privateNotifications) }
    var appLock by remember { mutableStateOf(settings.appLockEnabled) }
    val canLock = remember { deviceLockAvailable(context) }
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    SettingsSubScreen(s.privacyCard, onBack, snackbars) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SwitchRow(s.privacyTitle, s.privacyBody, private) {
                    private = it
                    settings.privateNotifications = it
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SwitchRow(s.lockTitle, s.lockBody, appLock) { on ->
                    // Без настроенного замка включать нечего — иначе приложение станет недоступным.
                    if (on && !canLock) {
                        scope.launch { snackbars.showSnackbar(s.lockUnavailable) }
                    } else {
                        // Проверяем палец сразу: и наглядно при включении, и защита от чужих рук при выключении.
                        promptDeviceLock(
                            context,
                            onError = { message ->
                                scope.launch {
                                    snackbars.currentSnackbarData?.dismiss()
                                    snackbars.showSnackbar(message)
                                }
                            },
                        ) {
                            appLock = on
                            settings.appLockEnabled = on
                        }
                    }
                }
                if (!canLock) {
                    Spacer(Modifier.height(8.dp))
                    Text(s.lockUnavailable, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ---------- Запас таблеток ----------

@Composable
fun StockSettingsScreen(onBack: () -> Unit) {
    val s = Lang.s
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var threshold by remember { mutableIntStateOf(settings.lowStockThreshold) }
    var thresholdText by remember { mutableStateOf(settings.lowStockThreshold.toString()) }
    val snackbars = remember { SnackbarHostState() }
    fun setThreshold(v: Int) {
        threshold = v.coerceIn(1, 100)
        thresholdText = threshold.toString()
        settings.lowStockThreshold = threshold
    }

    SettingsSubScreen(s.stockCard, onBack, snackbars) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                // Заголовок — про настройку, а не текст будущего уведомления: «Таблетки заканчиваются»
                // здесь читалось как тревога, хотя это экран порога.
                Text(s.stockThresholdTitle, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(s.thresholdLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                // Степпер: число между «−» и «+» вводится и с цифровой клавиатуры; чипы задают его сразу.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    OutlinedIconButton(onClick = { setThreshold(threshold - 1) }, enabled = threshold > 1, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Remove, contentDescription = s.stepDown)
                    }
                    OutlinedTextField(
                        value = thresholdText,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }.take(3)
                            thresholdText = digits
                            digits.toIntOrNull()?.takeIf { it in 1..100 }?.let {
                                threshold = it
                                settings.lowStockThreshold = it
                            }
                        },
                        textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
                        isError = thresholdText.toIntOrNull()?.let { it !in 1..100 } ?: true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = fieldColors(),
                        modifier = Modifier.width(112.dp).padding(horizontal = 10.dp),
                    )
                    OutlinedIconButton(onClick = { setThreshold(threshold + 1) }, enabled = threshold < 100, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Add, contentDescription = s.stepUp)
                    }
                }
                Text(
                    s.thresholdFieldHint(1, 100),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Чипы задают порог, а не прибавляют к нему: «+3» рядом с числом читалось
                    // как выбор значения, и два нажатия давали 6 вместо 3.
                    listOf(3, 5, 10, 20).forEach { n ->
                        FilterChip(
                            selected = threshold == n,
                            onClick = { setThreshold(n) },
                            label = { Text(n.toString(), maxLines = 1, softWrap = false) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(s.stockSupport, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------- Внешний вид: главный экран, графики, виджет ----------

@Composable
fun ChartSettingsScreen(onBack: () -> Unit) {
    val s = Lang.s
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var miniText by remember { mutableStateOf(settings.miniTrackerPoints.toString()) }
    var compact by remember { mutableStateOf(settings.homeCompact) }
    var homeActions by remember { mutableStateOf(settings.showHomeActions) }
    var timeline by remember { mutableStateOf(settings.showDayTimeline) }
    var smooth by remember { mutableStateOf(settings.chartSmooth) }
    var theme by remember { mutableStateOf(settings.theme) }
    val snackbars = remember { SnackbarHostState() }

    SettingsSubScreen(s.appearanceCard, onBack, snackbars) {
        // Тема: раньше приложение слепо следовало системе, и светлую тему днём было не получить.
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(s.themeSection, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        AppSettings.THEME_SYSTEM to s.themeSystem,
                        AppSettings.THEME_LIGHT to s.themeLight,
                        AppSettings.THEME_DARK to s.themeDark,
                    ).forEach { (code, label) ->
                        FilterChip(
                            selected = theme == code,
                            onClick = {
                                theme = code
                                settings.theme = code
                                // Экран перекрашивается сразу: значение живёт в памяти процесса.
                                ThemeMode.code = code
                            },
                            label = { Text(label, maxLines = 1, softWrap = false) },
                        )
                    }
                }
            }
        }

        // Главный экран: режим карточек, ряд кнопок, схема дня — всё, что про него, в одной карточке.
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(s.homeSectionTitle, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(s.homeModeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = !compact,
                        onClick = { compact = false; settings.homeCompact = false },
                        label = { Text(s.homeFull, maxLines = 1, softWrap = false) },
                    )
                    FilterChip(
                        selected = compact,
                        onClick = { compact = true; settings.homeCompact = true },
                        label = { Text(s.homeCompact, maxLines = 1, softWrap = false) },
                    )
                }
                Spacer(Modifier.height(12.dp))
                SwitchRow(s.homeActionsTitle, s.homeActionsBody, homeActions) {
                    homeActions = it
                    settings.showHomeActions = it
                }
                Spacer(Modifier.height(12.dp))
                SwitchRow(s.timelineTitle, s.timelineBody, timeline) {
                    timeline = it
                    settings.showDayTimeline = it
                }
            }
        }
        // Графики: вид линии и число точек на мини-графике.
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(s.chartStyleTitle, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(s.chartStyleBody, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = smooth,
                        onClick = { smooth = true; settings.chartSmooth = true },
                        label = { Text(s.chartSmooth, maxLines = 1, softWrap = false) },
                    )
                    FilterChip(
                        selected = !smooth,
                        onClick = { smooth = false; settings.chartSmooth = false },
                        label = { Text(s.chartSharp, maxLines = 1, softWrap = false) },
                    )
                }
                Spacer(Modifier.height(12.dp))
                // Поле не обрезает молча: вне диапазона — красная рамка и подсказка, значение не сохраняется.
                val miniValue = miniText.toIntOrNull()
                OutlinedTextField(
                    value = miniText,
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }.take(2)
                        miniText = digits
                        digits.toIntOrNull()?.takeIf { it in 3..60 }?.let { settings.miniTrackerPoints = it }
                    },
                    label = { Text(s.miniPointsLabel) },
                    isError = miniValue?.let { it !in 3..60 } ?: miniText.isNotEmpty(),
                    supportingText = { Text(s.rangeHint(3, 60)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ---------- Доставка уведомлений ----------

@Composable
fun DeliverySettingsScreen(onBack: () -> Unit) {
    val s = Lang.s
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }

    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val say: (String) -> Unit = { message ->
        scope.launch {
            snackbars.currentSnackbarData?.dismiss()
            snackbars.showSnackbar(message)
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh++
        // После двух отказов система молча не показывает диалог — ведём в системные настройки, иначе кнопка «не работает».
        if (!granted && !notificationsEnabled(context)) {
            safeLaunch(launcher, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName), context) { say(it) }
        }
    }

    val notificationsOn = remember(refresh) { notificationsEnabled(context) }
    val exactAlarmsOn = remember(refresh) { exactAlarmsAllowed(context) }
    val batteryOk = remember(refresh) { isBatteryUnrestricted(context) }
    val dndOk = remember(refresh) { hasDndAccess(context) }
    val open: (Intent) -> Unit = { safeLaunch(launcher, it, context) { message -> say(message) } }

    SettingsSubScreen(s.deliveryTitle, onBack, snackbars) {
        Text(s.deliveryIntro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        StepCard(notificationsOn, s.stepNotifTitle, s.stepNotifBody, s.allow) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsOn) {
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
        }
        StepCard(exactAlarmsOn, s.stepAlarmTitle, if (exactAlarmsOn) s.stepAlarmBodyOk else s.stepAlarmBodyBad, s.openBtn) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(Uri.parse("package:" + context.packageName)))
            } else {
                open(appDetailsSettings(context))
            }
        }
        StepCard(batteryOk, s.stepBatteryTitle, s.stepBatteryBody, s.configure) {
            open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:" + context.packageName)))
        }
        // «Не беспокоить» — по желанию: не красим красным то, без чего напоминания всё равно приходят.
        StepCard(if (dndOk) true else null, s.stepDndTitle, s.stepDndBody, s.grantAccess) {
            open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
        // Автозапуск приложение проверить не может — нейтральный значок, а не вечно красный пункт.
        StepCard(null, s.stepAutostartTitle, s.stepAutostartBody + "\n" + s.vendorHint(Build.MANUFACTURER), s.openAppSettings) {
            open(appDetailsSettings(context))
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(s.extrasTitle, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(s.extrasBody, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        FilledTonalButton(
            onClick = {
                refresh++
                val left = listOfNotNull(
                    s.notifWord.takeIf { !notificationsEnabled(context) },
                    s.alarmsWord.takeIf { !exactAlarmsAllowed(context) },
                    s.batteryWord.takeIf { !isBatteryUnrestricted(context) },
                )
                // «Не беспокоить» — необязательное: меню и чек-лист должны говорить одно и то же.
                say(
                    when {
                        left.isNotEmpty() -> s.remaining(left.joinToString(", "))
                        !hasDndAccess(context) -> s.allAllowedDndOptional
                        else -> s.allAllowed
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(s.checkAgain) }

        // Строка с моделью телефона и статусами нужна для разбора жалоб, а не человеку каждый
        // день — поэтому она под кнопкой, а не висит внизу экрана постоянно.
        var showDiag by remember { mutableStateOf(false) }
        if (showDiag) {
            Text(
                Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE +
                    " (API " + Build.VERSION.SDK_INT + ")\n" + s.diag(notificationsOn, exactAlarmsOn, batteryOk, dndOk),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            TextButton(onClick = { showDiag = true }, modifier = Modifier.fillMaxWidth()) {
                Text(s.showDiag, maxLines = 1, softWrap = false)
            }
        }
    }
}

// ---------- Архив таблеток ----------

/**
 * Снятые с расписания таблетки. Раньше их не было видно нигде: курс кончался, карточка исчезала
 * с главной, и вернуть её можно было только заведя таблетку заново.
 */
@Composable
fun ArchiveScreen(vm: MainViewModel, onBack: () -> Unit) {
    val s = Lang.s
    val archived by vm.archived.collectAsState()
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var purgeTarget by remember { mutableStateOf<Medication?>(null) }

    purgeTarget?.let { med ->
        AlertDialog(
            onDismissRequest = { purgeTarget = null },
            title = { Text(med.name) },
            text = { Text(s.archivePurgeConfirm) },
            confirmButton = {
                TextButton(onClick = {
                    vm.purgeFromArchive(med.id)
                    purgeTarget = null
                }) { Text(s.archivePurge) }
            },
            dismissButton = { TextButton(onClick = { purgeTarget = null }) { Text(s.cancel) } },
        )
    }

    SettingsSubScreen(s.archiveCard, onBack, snackbars) {
        if (archived.isEmpty()) {
            Text(
                s.archiveEmpty,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SettingsSubScreen
        }
        Text(s.archiveRestoreHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        archived.forEach { med ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    MarqueeText(med.name, fontWeight = FontWeight.SemiBold)
                    if (med.doseInfo.isNotBlank() || med.form.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            listOf(s.formName(med.form), med.doseInfo).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = {
                            vm.restoreFromArchive(med.id)
                            scope.launch {
                                snackbars.currentSnackbarData?.dismiss()
                                snackbars.showSnackbar(s.archiveRestored)
                            }
                        }) { Text(s.archiveRestore, maxLines = 1, softWrap = false) }
                        TextButton(
                            onClick = { purgeTarget = med },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text(s.archivePurge, maxLines = 1, softWrap = false) }
                    }
                }
            }
        }
    }
}

// ---------- Общие детали ----------

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Пункт чек-листа: `done = null` — проверить нельзя или пункт необязательный (нейтральный значок, не красный). */
@Composable
private fun StepCard(done: Boolean?, title: String, body: String, action: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (done) {
                        true -> Icons.Default.CheckCircle
                        false -> Icons.Default.Warning
                        null -> Icons.Default.Info
                    },
                    contentDescription = null,
                    tint = when (done) {
                        true -> MaterialTheme.colorScheme.primary
                        false -> MaterialTheme.colorScheme.error
                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = onClick) { Text(action, maxLines = 1, softWrap = false) }
        }
    }
}

/**
 * Не у всех оболочек есть экран под конкретный системный интент — на Samsung, например,
 * страницы точных будильников может не быть вовсе. Без запасного варианта это падение.
 */
private fun safeLaunch(
    launcher: ActivityResultLauncher<Intent>,
    intent: Intent,
    context: Context,
    /** Открыть нечего: раньше нажатие просто ничего не делало, и это выглядело поломкой. */
    onFail: (String) -> Unit = {},
) {
    try {
        launcher.launch(intent)
    } catch (_: ActivityNotFoundException) {
        try {
            launcher.launch(appDetailsSettings(context))
        } catch (_: ActivityNotFoundException) {
            onFail(Lang.s.systemScreenMissing)
        }
    }
}

private fun appDetailsSettings(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:" + context.packageName))
