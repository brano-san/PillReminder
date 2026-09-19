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
        get() = prefs.getInt(KEY_REPEAT_INTERVAL, 10)
        set(value) = prefs.edit().putInt(KEY_REPEAT_INTERVAL, value.coerceIn(1, 120)).apply()

    /** Сколько раз повторить, прежде чем отстать. */
    var repeatCount: Int
        get() = prefs.getInt(KEY_REPEAT_COUNT, 6)
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

    /**
     * Настройки для бэкапа. Ключи — как в SharedPreferences; читаем и пишем по одному списку,
     * чтобы новая настройка не потерялась при восстановлении на новом телефоне.
     */
    fun exportMap(): Map<String, Any?> = BACKUP_KEYS.associateWith { prefs.all[it] }

    /** Применить настройки из бэкапа: чужие и неизвестные ключи игнорируются. */
    fun importMap(values: Map<String, Any?>) {
        val editor = prefs.edit()
        for ((key, value) in values) {
            if (key !in BACKUP_KEYS) continue
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                null -> editor.remove(key)
            }
        }
        editor.apply()
    }

    /** Тема приложения: "system" (как в системе), "light" или "dark". */
    var theme: String
        get() = prefs.getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    /** Уведомление «Таблетки заканчиваются». */
    var notifyLowStock: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_LOW_STOCK, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFY_LOW_STOCK, value).apply()

    /** Уведомление «Курс закончился». */
    var notifyCourseDone: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_COURSE_DONE, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFY_COURSE_DONE, value).apply()

    /** Уведомление «День закрыт — все приёмы отмечены». */
    var notifyDayDone: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_DAY_DONE, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFY_DAY_DONE, value).apply()

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

    /**
     * Цвет текста виджета: [WidgetStyle.TEXT_LIGHT] или [WidgetStyle.TEXT_DARK]. Режима «авто» с 1.2.1 нет —
     * он гадал по цвету пресета, а не по обоям; старое сохранённое «auto» читается как режим по яркости фона.
     */
    var widgetText: String
        get() = prefs.getString(KEY_WIDGET_TEXT, null)?.takeIf { it == WidgetStyle.TEXT_LIGHT || it == WidgetStyle.TEXT_DARK }
            ?: WidgetStyle.textModeFor(widgetColor)
        set(value) = prefs.edit().putString(KEY_WIDGET_TEXT, value).apply()

    internal companion object {
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
        const val KEY_THEME = "theme"
        const val KEY_NOTIFY_LOW_STOCK = "notify_low_stock"
        const val KEY_NOTIFY_COURSE_DONE = "notify_course_done"
        const val KEY_NOTIFY_DAY_DONE = "notify_day_done"

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        /**
         * Что попадает в бэкап настроек. Сюда НЕ входит состояние устройства: показанный туториал,
         * незакрытый сон, отметка «про день уже сказали» — их переносить на новый телефон нельзя.
         */
        val BACKUP_KEYS = setOf(
            KEY_REPEAT_ENABLED, KEY_REPEAT_INTERVAL, KEY_REPEAT_COUNT, KEY_SNOOZE, KEY_SNOOZE_OPTIONS,
            KEY_QUIET_ENABLED, KEY_QUIET_FROM, KEY_QUIET_TO, KEY_ALARM_SOUND, KEY_FULL_SCREEN,
            KEY_LANGUAGE, KEY_SOUND_URI, KEY_PRIVATE, KEY_LOW_STOCK, KEY_WAKE_REMIND, KEY_WAKE_REMIND_AT,
            KEY_CHART_SMOOTH, KEY_MINI_POINTS, KEY_VISIT_OFFSETS, KEY_VISIT_CUSTOM,
            KEY_WIDGET_COLOR, KEY_WIDGET_OPACITY, KEY_WIDGET_TEXT, KEY_HOME_COMPACT,
            KEY_HOME_ACTIONS, KEY_DAY_TIMELINE, KEY_ASK_SLEEP, KEY_THEME,
            KEY_NOTIFY_LOW_STOCK, KEY_NOTIFY_COURSE_DONE, KEY_NOTIFY_DAY_DONE,
        )
    }
}
