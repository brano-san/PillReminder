package tech.unispace.pillreminder.data

import android.content.Context

/**
 * Настройки напоминаний. SharedPreferences, а не DataStore, потому что читать их
 * приходится синхронно из BroadcastReceiver в момент срабатывания будильника.
 */
class Settings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("reminder_settings", Context.MODE_PRIVATE)

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

    /** Сколько последних записей показывать на мини-графике трекера. */
    var miniTrackerPoints: Int
        get() = prefs.getInt(KEY_MINI_POINTS, 10)
        set(value) = prefs.edit().putInt(KEY_MINI_POINTS, value.coerceIn(3, 60)).apply()

    /** На сколько минут откладывает кнопка «Отложить» в уведомлении. */
    var snoozeMinutes: Int
        get() = prefs.getInt(KEY_SNOOZE, 15)
        set(value) = prefs.edit().putInt(KEY_SNOOZE, value.coerceIn(5, 120)).apply()

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
        get() = prefs.getBoolean(KEY_TUTORIAL_SEEN, false)
        set(value) = prefs.edit().putBoolean(KEY_TUTORIAL_SEEN, value).apply()

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

    private companion object {
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
        const val KEY_REPEAT_ENABLED = "repeat_enabled"
        const val KEY_REPEAT_INTERVAL = "repeat_interval"
        const val KEY_REPEAT_COUNT = "repeat_count"
        const val KEY_ALARM_SOUND = "alarm_sound"
    }
}
