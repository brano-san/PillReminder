package tech.unispace.pillreminder.data

import android.content.Context
import tech.unispace.pillreminder.widget.WidgetStyle

/**
 * Настройки напоминаний. SharedPreferences, а не DataStore, потому что читать их
 * приходится синхронно из BroadcastReceiver в момент срабатывания будильника.
 */
class Settings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("reminder_settings", Context.MODE_PRIVATE)

    /**
     * Состояние самого устройства, а не пользователя: этот файл исключён из облачного
     * бэкапа (res/xml/backup_rules.xml), поэтому после переустановки гайд снова показывается.
     */
    private val localPrefs = context.applicationContext
        .getSharedPreferences("local_state", Context.MODE_PRIVATE)

    /**
     * true — приложение обновили поверх установленного; false — поставили заново.
     * После восстановления из облака тоже false: restore идёт сразу за install,
     * поэтому firstInstallTime == lastUpdateTime.
     */
    private val isUpdate: Boolean by lazy {
        runCatching {
            val app = context.applicationContext
            val info = app.packageManager.getPackageInfo(app.packageName, 0)
            info.lastUpdateTime > info.firstInstallTime
        }.getOrDefault(false)
    }

    /** Повторять уведомление, пока приём не отмечен. */
    var repeatEnabled: Boolean
        get() = prefs.getBoolean(KEY_REPEAT_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_REPEAT_ENABLED, value).apply()

    /** Через сколько минут повторять. */
    var repeatIntervalMinutes: Int
        get() = prefs.getInt(KEY_REPEAT_INTERVAL, 3)
        set(value) = prefs.edit().putInt(KEY_REPEAT_INTERVAL, value.coerceIn(1, 120)).apply()

    /** Сколько раз повторить, прежде чем отстать. */
    var repeatCount: Int
        get() = prefs.getInt(KEY_REPEAT_COUNT, 20)
        set(value) = prefs.edit().putInt(KEY_REPEAT_COUNT, value.coerceIn(1, 200)).apply()

    /**
     * Звук по каналу будильника вместо канала уведомлений.
     * Поток будильника не глушится беззвучным режимом и вибро-режимом.
     */
    var alarmSound: Boolean
        get() = prefs.getBoolean(KEY_ALARM_SOUND, true)
        set(value) = prefs.edit().putBoolean(KEY_ALARM_SOUND, value).apply()

    /** Показывать напоминание во весь экран (поверх заблокированного тоже). */
    var fullScreenAlarm: Boolean
        get() = prefs.getBoolean(KEY_FULL_SCREEN, false)
        set(value) = prefs.edit().putBoolean(KEY_FULL_SCREEN, value).apply()

    /** Язык интерфейса: "ru" или "en". */
    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "ru") ?: "ru"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    /** URI своего звука уведомления; null = будильник по умолчанию. */
    var soundUri: String?
        get() = prefs.getString(KEY_SOUND_URI, null)
        set(value) = prefs.edit().putString(KEY_SOUND_URI, value).apply()

    /**
     * Версия каналов уведомлений. Настройки канала неизменяемы после создания,
     * поэтому смена звука = новый канал с новым id и удаление старого.
     */
    var channelVersion: Int
        get() = prefs.getInt(KEY_CHANNEL_VERSION, 0)
        set(value) = prefs.edit().putInt(KEY_CHANNEL_VERSION, value).apply()

    /** За сколько минут напоминать о визите к врачу (можно несколько). */
    var visitOffsetsMinutes: Set<Int>
        get() = prefs.getStringSet(KEY_VISIT_OFFSETS, null)
            ?.mapNotNull { it.toIntOrNull() }?.toSet()
            ?: setOf(2880, 1440, 180)
        set(value) {
            prefs.edit()
                .putStringSet(KEY_VISIT_OFFSETS, value.map { it.toString() }.toSet())
                .apply()
            visitOffsetsEver = visitOffsetsEver + value
        }

    /**
     * Свои смещения напоминаний о визите: живут отдельно от выбранных, поэтому снятая
     * галочка не стирает добавленное время — его убирает только кнопка удаления.
     */
    var visitOffsetsCustom: Set<Int>
        get() = prefs.getStringSet(KEY_VISIT_CUSTOM, null)?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_VISIT_CUSTOM, value.map { it.toString() }.toSet()).apply()

    /** Варианты кнопки «Отложить» на полноэкранном напоминании, минуты. */
    var snoozeOptions: List<Int>
        get() = (prefs.getString(KEY_SNOOZE_OPTIONS, null) ?: "")
            .split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..720 }.distinct().sorted()
            .ifEmpty { listOf(10, 30, 60) }
        set(value) = prefs.edit()
            .putString(KEY_SNOOZE_OPTIONS, value.filter { it in 1..720 }.distinct().sorted().joinToString(","))
            .apply()

    /** Пресетные смещения напоминаний о визите, которые пользователь убрал из списка. */
    var visitOffsetsHidden: Set<Int>
        get() = prefs.getStringSet(KEY_VISIT_HIDDEN, null)?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_VISIT_HIDDEN, value.map { it.toString() }.toSet()).apply()

    /** Момент нажатия «Ложусь спать»; 0 — кнопку не нажимали. */
    var pendingSleepStart: Long
        get() = prefs.getLong(KEY_SLEEP_START, 0L)
        set(value) = prefs.edit().putLong(KEY_SLEEP_START, value).apply()

    /** Спрашивать оценку сна при нажатии «Я проснулся». */
    var askSleepOnWake: Boolean
        get() = prefs.getBoolean(KEY_ASK_SLEEP, true)
        set(value) = prefs.edit().putBoolean(KEY_ASK_SLEEP, value).apply()

    /** День (epochDay цикла), про который уже сказали «всё выпито»; -1 — ещё не говорили. */
    var dayDoneNotifiedFor: Long
        get() = prefs.getLong(KEY_DAY_DONE, -1L)
        set(value) = prefs.edit().putLong(KEY_DAY_DONE, value).apply()

    /** Просить отпечаток или код устройства при открытии приложения. */
    var appLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_LOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_APP_LOCK, value).apply()

    /** Показывать на главной ряд кнопок «Советы · Туториал · Отчёт · Сон · Еда». */
    var showHomeActions: Boolean
        get() = prefs.getBoolean(KEY_HOME_ACTIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_HOME_ACTIONS, value).apply()

    /** Не показывать названия таблеток в уведомлениях. */
    var privateNotifications: Boolean
        get() = prefs.getBoolean(KEY_PRIVATE, false)
        set(value) = prefs.edit().putBoolean(KEY_PRIVATE, value).apply()

    /** Порог «таблетки заканчиваются», в штуках. */
    var lowStockThreshold: Int
        get() = prefs.getInt(KEY_LOW_STOCK, 5)
        set(value) = prefs.edit().putInt(KEY_LOW_STOCK, value.coerceIn(1, 100)).apply()

    /** Напоминать нажать «я проснулся», если к назначенному времени день не начат. */
    var wakeReminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_WAKE_REMIND, false)
        set(value) = prefs.edit().putBoolean(KEY_WAKE_REMIND, value).apply()

    /** Время напоминания «я проснулся», минут от полуночи. */
    var wakeReminderMinutes: Int
        get() = prefs.getInt(KEY_WAKE_REMIND_AT, 600)
        set(value) = prefs.edit().putInt(KEY_WAKE_REMIND_AT, value.coerceIn(0, 24 * 60 - 1)).apply()

    /** Сглаженная линия на графиках вместо ломаной. */
    var chartSmooth: Boolean
        get() = prefs.getBoolean(KEY_CHART_SMOOTH, true)
        set(value) = prefs.edit().putBoolean(KEY_CHART_SMOOTH, value).apply()

    /** Сколько последних записей показывать на мини-графике трекера. */
    var miniTrackerPoints: Int
        get() = prefs.getInt(KEY_MINI_POINTS, 10)
        set(value) = prefs.edit().putInt(KEY_MINI_POINTS, value.coerceIn(3, 60)).apply()

    /**
     * На сколько минут откладывает кнопка «Отложить» в уведомлении. Пока не задано явно —
     * первый из вариантов [snoozeOptions], как и обещает подпись в настройках.
     */
    var snoozeMinutes: Int
        get() = prefs.getInt(KEY_SNOOZE, -1).takeIf { it > 0 } ?: snoozeOptions.first()
        set(value) = prefs.edit().putInt(KEY_SNOOZE, value.coerceIn(1, 720)).apply()

    /** Тихие часы: повторы напоминаний не беспокоят. */
    var quietEnabled: Boolean
        get() = prefs.getBoolean(KEY_QUIET_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_QUIET_ENABLED, value).apply()

    /** Начало тихих часов, минут от полуночи. */
    var quietFromMinutes: Int
        get() = prefs.getInt(KEY_QUIET_FROM, 23 * 60)
        set(value) = prefs.edit().putInt(KEY_QUIET_FROM, value.coerceIn(0, 24 * 60 - 1)).apply()

    /** Конец тихих часов, минут от полуночи. */
    var quietToMinutes: Int
        get() = prefs.getInt(KEY_QUIET_TO, 8 * 60)
        set(value) = prefs.edit().putInt(KEY_QUIET_TO, value.coerceIn(0, 24 * 60 - 1)).apply()

    /** Туториал уже показан при первом запуске. */
    var tutorialSeen: Boolean
        // Старое значение из основного файла подхватываем один раз, чтобы после обновления
        // приложения гайд не показался тем, кто его уже прошёл.
        // Старое значение подхватываем ТОЛЬКО при обновлении: на чистой установке
        // reminder_settings.xml мог приехать из облачного бэкапа, и гайд бы не показался.
        get() = localPrefs.getBoolean(KEY_TUTORIAL_SEEN, isUpdate && prefs.getBoolean(KEY_TUTORIAL_SEEN, false))
        set(value) = localPrefs.edit().putBoolean(KEY_TUTORIAL_SEEN, value).apply()

    /**
     * Все смещения напоминаний о визитах, которые когда-либо включались — чтобы при
     * пересборке будильников отменить и те, что пользователь потом убрал.
     */
    var visitOffsetsEver: Set<Int>
        get() = prefs.getStringSet(KEY_VISIT_OFFSETS_EVER, null)
            ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
        set(value) = prefs.edit()
            .putStringSet(KEY_VISIT_OFFSETS_EVER, value.map { it.toString() }.toSet())
            .apply()

    /** Компактные карточки таблеток на главном экране. */
    var homeCompact: Boolean
        get() = prefs.getBoolean(KEY_HOME_COMPACT, false)
        set(value) = prefs.edit().putBoolean(KEY_HOME_COMPACT, value).apply()

    /** Схема дня (подъём → таблетки → еда → сон) в карточке пробуждения на главной. */
    var showDayTimeline: Boolean
        get() = prefs.getBoolean(KEY_DAY_TIMELINE, true)
        set(value) = prefs.edit().putBoolean(KEY_DAY_TIMELINE, value).apply()

    /** Цвет фона виджета (ARGB); по умолчанию бирюзовый из палитры. */
    var widgetColor: Int
        get() = prefs.getInt(KEY_WIDGET_COLOR, WidgetStyle.DEFAULT_COLOR)
        set(value) = prefs.edit().putInt(KEY_WIDGET_COLOR, value).apply()

    /** Непрозрачность фона виджета, 0–100 %; 0 — полностью прозрачный. */
    var widgetOpacity: Int
        get() = prefs.getInt(KEY_WIDGET_OPACITY, 100)
        set(value) = prefs.edit().putInt(KEY_WIDGET_OPACITY, value.coerceIn(0, 100)).apply()

    /** Цвет текста виджета: [WidgetStyle.TEXT_AUTO] (по яркости фона), [WidgetStyle.TEXT_LIGHT], [WidgetStyle.TEXT_DARK]. */
    var widgetText: String
        get() = prefs.getString(KEY_WIDGET_TEXT, WidgetStyle.TEXT_AUTO) ?: WidgetStyle.TEXT_AUTO
        set(value) = prefs.edit().putString(KEY_WIDGET_TEXT, value).apply()

    private companion object {
        const val KEY_DAY_TIMELINE = "show_day_timeline"
        const val KEY_WIDGET_COLOR = "widget_color"
        const val KEY_WIDGET_OPACITY = "widget_opacity"
        const val KEY_WIDGET_TEXT = "widget_text"
        const val KEY_HOME_COMPACT = "home_compact"
        const val KEY_TUTORIAL_SEEN = "tutorial_seen"
        const val KEY_VISIT_OFFSETS_EVER = "visit_offsets_ever"
        const val KEY_SNOOZE = "snooze_minutes"
        const val KEY_QUIET_ENABLED = "quiet_enabled"
        const val KEY_QUIET_FROM = "quiet_from"
        const val KEY_QUIET_TO = "quiet_to"
        const val KEY_MINI_POINTS = "mini_tracker_points"
        const val KEY_PRIVATE = "private_notifications"
        const val KEY_LOW_STOCK = "low_stock_threshold"
        const val KEY_WAKE_REMIND = "wake_reminder_enabled"
        const val KEY_WAKE_REMIND_AT = "wake_reminder_minutes"
        const val KEY_FULL_SCREEN = "full_screen_alarm"
        const val KEY_LANGUAGE = "language"
        const val KEY_SOUND_URI = "sound_uri"
        const val KEY_CHANNEL_VERSION = "channel_version"
        const val KEY_VISIT_OFFSETS = "visit_offsets"
        const val KEY_VISIT_CUSTOM = "visit_offsets_custom"
        const val KEY_VISIT_HIDDEN = "visit_offsets_hidden"
        const val KEY_CHART_SMOOTH = "chart_smooth"
        const val KEY_APP_LOCK = "app_lock"
        const val KEY_HOME_ACTIONS = "home_actions"
        const val KEY_SLEEP_START = "pending_sleep_start"
        const val KEY_ASK_SLEEP = "ask_sleep_on_wake"
        const val KEY_DAY_DONE = "day_done_notified_for"
        const val KEY_SNOOZE_OPTIONS = "snooze_options"
        const val KEY_REPEAT_ENABLED = "repeat_enabled"
        const val KEY_REPEAT_INTERVAL = "repeat_interval"
        const val KEY_REPEAT_COUNT = "repeat_count"
        const val KEY_ALARM_SOUND = "alarm_sound"
    }
}
