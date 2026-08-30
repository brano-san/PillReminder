@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package tech.unispace.pillreminder.ui

import android.app.Activity
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.launch
import tech.unispace.pillreminder.alarm.AlarmScheduler
import tech.unispace.pillreminder.alarm.Notifications
import tech.unispace.pillreminder.alarm.VISIT_OFFSET_CHOICES
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

// ---------- Меню настроек ----------

@Composable
fun SettingsMenuScreen(
    contentPadding: PaddingValues,
    onOpenRepeats: () -> Unit,
    onOpenSound: () -> Unit,
    onOpenDelivery: () -> Unit,
    onOpenVisits: () -> Unit,
    onOpenMisc: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenReport: () -> Unit,
    onLanguageChanged: () -> Unit,
) {
    val s = Lang.s
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val delivery = deliveryOk(context)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(s.settingsTitle, style = MaterialTheme.typography.titleLarge)

        SettingsNavCard(
            icon = { Icon(Icons.Default.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = s.repeatsCard,
            subtitle = if (settings.repeatEnabled) {
                s.repeatsOn(settings.repeatIntervalMinutes, settings.repeatCount)
            } else {
                s.repeatsOff
            },
            onClick = onOpenRepeats,
        )

        SettingsNavCard(
            icon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = s.soundCard,
            subtitle = buildString {
                append(if (settings.alarmSound) s.soundAlarmShort else s.soundNormalShort)
                append(" · ")
                append(if (settings.fullScreenAlarm) s.fullScreenShort else s.notifShort)
            },
            onClick = onOpenSound,
        )

        SettingsNavCard(
            icon = {
                Icon(
                    if (delivery) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (delivery) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            },
            title = s.deliveryCard,
            subtitle = if (delivery) s.deliveryOkSub else s.deliveryBadSub,
            onClick = onOpenDelivery,
        )

        SettingsNavCard(
            icon = { Icon(Icons.Default.MedicalServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = s.visitsCard,
            subtitle = s.visitsCardSub(settings.visitOffsetsMinutes.size),
            onClick = onOpenVisits,
        )

        SettingsNavCard(
            icon = { Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = s.miscCard,
            subtitle = s.miscCardSub,
            onClick = onOpenMisc,
        )

        SettingsNavCard(
            icon = { Icon(Icons.Default.Save, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = s.backupCard,
            subtitle = s.backupCardSub,
            onClick = onOpenBackup,
        )

        SettingsNavCard(
            icon = { Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = s.reportCard,
            subtitle = s.reportCardSub,
            onClick = onOpenReport,
        )

        // Виджет: закрепление кнопкой прямо из меню.
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Widgets, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.widgetCard, fontWeight = FontWeight.SemiBold)
                        Text(
                            s.widgetCardSub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                val manager = AppWidgetManager.getInstance(context)
                if (manager.isRequestPinAppWidgetSupported) {
                    FilledTonalButton(
                        onClick = {
                            manager.requestPinAppWidget(
                                ComponentName(context, PillWidgetProvider::class.java),
                                null,
                                null,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(s.widgetAdd)
                    }
                } else {
                    Text(
                        s.widgetUnsupported,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Язык — переключается на месте.
        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Text(s.languageCard, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                FilterChip(
                    selected = Lang.code == "ru",
                    onClick = {
                        Lang.code = "ru"
                        settings.language = "ru"
                        Notifications.createChannels(context)
                        onLanguageChanged()
                    },
                    label = { Text("RU") },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = Lang.code == "en",
                    onClick = {
                        Lang.code = "en"
                        settings.language = "en"
                        Notifications.createChannels(context)
                        onLanguageChanged()
                    },
                    label = { Text("EN") },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsNavCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Каркас подэкрана настроек: шапка с «назад» и снекбар. */
@Composable
private fun SettingsSubScreen(
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

// ---------- Повторы + напоминание проснуться ----------

@Composable
fun RepeatSettingsScreen(onBack: () -> Unit, vm: MainViewModel) {
    val s = Lang.s
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var repeatEnabled by remember { mutableStateOf(settings.repeatEnabled) }
    var repeatInterval by remember { mutableIntStateOf(settings.repeatIntervalMinutes) }
    var repeatIntervalText by remember { mutableStateOf(settings.repeatIntervalMinutes.toString()) }
    var repeatCount by remember { mutableIntStateOf(settings.repeatCount) }
    var wakeRemind by remember { mutableStateOf(settings.wakeReminderEnabled) }
    var wakeRemindAt by remember { mutableIntStateOf(settings.wakeReminderMinutes) }
    var showTimePicker by remember { mutableStateOf(false) }
    val snackbars = remember { SnackbarHostState() }

    if (showTimePicker) {
        TimeWheelDialog(
            initial = LocalTime.of(wakeRemindAt / 60, wakeRemindAt % 60),
            onPick = {
                wakeRemindAt = it.hour * 60 + it.minute
                settings.wakeReminderMinutes = wakeRemindAt
                vm.rescheduleWakeReminder()
            },
            onDismiss = { showTimePicker = false },
        )
    }

    SettingsSubScreen(s.repeatTitle, onBack, snackbars) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SwitchRow(
                    title = s.repeatTitle,
                    body = s.repeatBody,
                    checked = repeatEnabled,
                    onChange = {
                        repeatEnabled = it
                        settings.repeatEnabled = it
                    },
                )

                if (repeatEnabled) {
                    Spacer(Modifier.height(12.dp))
                    Text(s.repeatHowOften, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 3, 5, 10, 15).forEach { m ->
                            FilterChip(
                                selected = repeatInterval == m,
                                onClick = {
                                    repeatInterval = m
                                    repeatIntervalText = m.toString()
                                    settings.repeatIntervalMinutes = m
                                },
                                label = { Text(m.toString() + " " + s.minutesLabel.take(3)) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = repeatIntervalText,
                        onValueChange = { raw ->
                            repeatIntervalText = raw
                            // Пустое или мусорное значение не сохраняем — остаётся прежнее.
                            raw.toIntOrNull()?.coerceIn(1, 120)?.let { v ->
                                repeatInterval = v
                                settings.repeatIntervalMinutes = v
                            }
                        },
                        label = { Text(s.customIntervalLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

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
                var snooze by remember { mutableIntStateOf(settings.snoozeMinutes) }
                Text(s.snoozeSettingTitle, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60).forEach { m ->
                        FilterChip(
                            selected = snooze == m,
                            onClick = {
                                snooze = m
                                settings.snoozeMinutes = m
                            },
                            label = { Text(m.toString()) },
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
                var showFromPicker by remember { mutableStateOf(false) }
                var showToPicker by remember { mutableStateOf(false) }

                if (showFromPicker) {
                    TimeWheelDialog(
                        initial = LocalTime.of(quietFrom / 60, quietFrom % 60),
                        onPick = {
                            quietFrom = it.hour * 60 + it.minute
                            settings.quietFromMinutes = quietFrom
                        },
                        onDismiss = { showFromPicker = false },
                    )
                }
                if (showToPicker) {
                    TimeWheelDialog(
                        initial = LocalTime.of(quietTo / 60, quietTo % 60),
                        onPick = {
                            quietTo = it.hour * 60 + it.minute
                            settings.quietToMinutes = quietTo
                        },
                        onDismiss = { showToPicker = false },
                    )
                }

                SwitchRow(
                    title = s.quietTitle,
                    body = s.quietBody,
                    checked = quiet,
                    onChange = {
                        quiet = it
                        settings.quietEnabled = it
                    },
                )
                if (quiet) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { showFromPicker = true }, modifier = Modifier.weight(1f)) {
                            Text(s.quietFrom + " " + "%02d:%02d".format(quietFrom / 60, quietFrom % 60))
                        }
                        OutlinedButton(onClick = { showToPicker = true }, modifier = Modifier.weight(1f)) {
                            Text(s.quietTo + " " + "%02d:%02d".format(quietTo / 60, quietTo % 60))
                        }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WbSunny, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(s.wakeRemindCard, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Switch(
                        checked = wakeRemind,
                        onCheckedChange = {
                            wakeRemind = it
                            settings.wakeReminderEnabled = it
                            vm.rescheduleWakeReminder()
                        },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    s.wakeRemindBody,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (wakeRemind) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { showTimePicker = true }) {
                        Text(
                            s.wakeRemindTime + ": " +
                                "%02d:%02d".format(wakeRemindAt / 60, wakeRemindAt % 60),
                        )
                    }
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
    val fsiAllowed = remember(refresh) { canUseFullScreenIntent(context) }

    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val say: (String) -> Unit = { message ->
        scope.launch {
            snackbars.currentSnackbarData?.dismiss()
            snackbars.showSnackbar(message)
        }
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refresh++ }

    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = result.data
                ?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            settings.soundUri = uri?.toString()
            // Настройки канала неизменяемы — пересоздаём каналы под новую мелодию.
            settings.channelVersion = settings.channelVersion + 1
            Notifications.createChannels(context)
        }
    }

    SettingsSubScreen(s.soundScreenTitle, onBack, snackbars) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SwitchRow(
                    title = s.alarmSoundTitle,
                    body = s.alarmSoundBody,
                    checked = alarmSound,
                    onChange = {
                        alarmSound = it
                        settings.alarmSound = it
                    },
                )
                Spacer(Modifier.height(12.dp))
                Text(s.notifSoundTitle, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    s.notifSoundBody,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(
                                RingtoneManager.EXTRA_RINGTONE_TYPE,
                                RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_NOTIFICATION,
                            )
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(
                                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                settings.soundUri?.let { Uri.parse(it) },
                            )
                        try {
                            ringtoneLauncher.launch(intent)
                        } catch (_: ActivityNotFoundException) {
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(s.pickSound)
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SwitchRow(
                    title = s.fullScreenTitle,
                    body = s.fullScreenBody,
                    checked = fullScreenAlarm,
                    onChange = {
                        fullScreenAlarm = it
                        settings.fullScreenAlarm = it
                    },
                )
                if (fullScreenAlarm && !fsiAllowed) {
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 34) {
                                safeLaunch(
                                    launcher,
                                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                                        .setData(Uri.parse("package:" + context.packageName)),
                                    context,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(s.allowFullScreen)
                    }
                    Text(
                        s.fsi14Note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(s.testSection, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    s.testHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = {
                        AlarmScheduler(context).scheduleTest(10_000)
                        say(s.testScheduled)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(s.testNormal)
                }
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = {
                        if (!canUseFullScreenIntent(context)) {
                            say(s.fsPermWarn)
                        } else {
                            AlarmScheduler(context).scheduleTest(10_000, fullScreen = true)
                            say(s.testFsScheduled)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(s.testFullScreen)
                }
            }
        }
    }
}

// ---------- Напоминания о врачах ----------

@Composable
fun VisitReminderSettingsScreen(onBack: () -> Unit, vm: MainViewModel) {
    val s = Lang.s
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var selected by remember { mutableStateOf(settings.visitOffsetsMinutes) }
    val snackbars = remember { SnackbarHostState() }

    SettingsSubScreen(s.visitRemindersTitle, onBack, snackbars) {
        Text(
            s.visitRemindersBody,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp)) {
                VISIT_OFFSET_CHOICES.forEach { offset ->
                    val label = when (offset) {
                        2880 -> s.offset2d
                        1440 -> s.offset1d
                        else -> s.offset3h
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (offset in selected) selected - offset else selected + offset
                                settings.visitOffsetsMinutes = selected
                                vm.rescheduleVisitAlarms()
                            }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = offset in selected,
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + offset else selected - offset
                                settings.visitOffsetsMinutes = selected
                                vm.rescheduleVisitAlarms()
                            },
                        )
                        Text(label)
                    }
                }
            }
        }
    }
}

// ---------- Конфиденциальность и запас ----------

@Composable
fun MiscSettingsScreen(onBack: () -> Unit) {
    val s = Lang.s
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var private by remember { mutableStateOf(settings.privateNotifications) }
    var thresholdText by remember { mutableStateOf(settings.lowStockThreshold.toString()) }
    val snackbars = remember { SnackbarHostState() }

    SettingsSubScreen(s.miscCard, onBack, snackbars) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SwitchRow(
                    title = s.privacyTitle,
                    body = s.privacyBody,
                    checked = private,
                    onChange = {
                        private = it
                        settings.privateNotifications = it
                    },
                )
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(s.lowStockTitle, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = thresholdText,
                    onValueChange = { raw ->
                        thresholdText = raw
                        raw.toIntOrNull()?.coerceIn(1, 100)?.let { settings.lowStockThreshold = it }
                    },
                    label = { Text(s.thresholdLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    s.stockSupport,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                var miniText by remember { mutableStateOf(settings.miniTrackerPoints.toString()) }
                OutlinedTextField(
                    value = miniText,
                    onValueChange = { raw ->
                        miniText = raw
                        raw.toIntOrNull()?.coerceIn(3, 60)?.let { settings.miniTrackerPoints = it }
                    },
                    label = { Text(s.miniPointsLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
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
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refresh++ }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh++ }

    val notificationsOn = remember(refresh) { notificationsEnabled(context) }
    val exactAlarmsOn = remember(refresh) { exactAlarmsAllowed(context) }
    val batteryOk = remember(refresh) { isBatteryUnrestricted(context) }
    val dndOk = remember(refresh) { hasDndAccess(context) }

    val open: (Intent) -> Unit = { safeLaunch(launcher, it, context) }

    SettingsSubScreen(s.deliveryTitle, onBack, snackbars) {
        Text(
            s.deliveryIntro,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        StepCard(
            done = notificationsOn,
            title = s.stepNotifTitle,
            body = s.stepNotifBody,
            action = s.allow,
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsOn) {
                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    open(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }
            },
        )

        StepCard(
            done = exactAlarmsOn,
            title = s.stepAlarmTitle,
            body = if (exactAlarmsOn) s.stepAlarmBodyOk else s.stepAlarmBodyBad,
            action = s.openBtn,
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    open(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .setData(Uri.parse("package:" + context.packageName)),
                    )
                } else {
                    open(appDetailsSettings(context))
                }
            },
        )

        StepCard(
            done = batteryOk,
            title = s.stepBatteryTitle,
            body = s.stepBatteryBody,
            action = s.configure,
            onClick = {
                open(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:" + context.packageName)),
                )
            },
        )

        StepCard(
            done = dndOk,
            title = s.stepDndTitle,
            body = s.stepDndBody,
            action = s.grantAccess,
            onClick = { open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
        )

        StepCard(
            done = false,
            title = s.stepAutostartTitle,
            body = s.vendorHint(Build.MANUFACTURER),
            action = s.openAppSettings,
            onClick = { open(appDetailsSettings(context)) },
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(s.extrasTitle, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    s.extrasBody,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                say(if (left.isEmpty()) s.allAllowed else s.remaining(left.joinToString(", ")))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(s.checkAgain)
        }

        Text(
            Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE +
                " (API " + Build.VERSION.SDK_INT + ")\n" +
                s.diag(notificationsOn, exactAlarmsOn, batteryOk, dndOk),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------- Общие детали ----------

@Composable
private fun SwitchRow(
    title: String,
    body: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StepCard(
    done: Boolean,
    title: String,
    body: String,
    action: String,
    onClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (done) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = onClick) { Text(action) }
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
) {
    try {
        launcher.launch(intent)
    } catch (_: ActivityNotFoundException) {
        try {
            launcher.launch(appDetailsSettings(context))
        } catch (_: ActivityNotFoundException) {
            // Совсем некуда вести — оставляем пользователя на месте.
        }
    }
}

private fun appDetailsSettings(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.parse("package:" + context.packageName))
