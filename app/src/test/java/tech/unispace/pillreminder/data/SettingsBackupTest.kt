package tech.unispace.pillreminder.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Настройки переезжают вместе с данными: тихие часы, повторы и свои сроки визитов
 * человек настраивал руками, и терять их при восстановлении нельзя.
 */
class SettingsBackupTest {

    private val values: Map<String, Any?> = mapOf(
        Settings.KEY_REPEAT_ENABLED to true,
        Settings.KEY_REPEAT_INTERVAL to 15,
        Settings.KEY_QUIET_FROM to 1380,
        Settings.KEY_LANGUAGE to "en",
        Settings.KEY_WIDGET_COLOR to 0xFF2196F3.toInt(),
        Settings.KEY_VISIT_CUSTOM to setOf("4320", "60"),
    )

    @Test
    fun roundTripKeepsEveryValue() {
        val back = Backup.settingsFromJson(Backup.settingsToJson(values))
        assertEquals(true, back[Settings.KEY_REPEAT_ENABLED])
        assertEquals(15, back[Settings.KEY_REPEAT_INTERVAL])
        assertEquals(1380, back[Settings.KEY_QUIET_FROM])
        assertEquals("en", back[Settings.KEY_LANGUAGE])
        assertEquals(setOf("4320", "60"), back[Settings.KEY_VISIT_CUSTOM])
    }

    @Test
    fun negativeColorSurvives() =
        assertEquals(0xFF2196F3.toInt(), Backup.settingsFromJson(Backup.settingsToJson(values))[Settings.KEY_WIDGET_COLOR])

    @Test
    fun numbersComeBackAsIntNotLong() {
        val back = Backup.settingsFromJson(Backup.settingsToJson(mapOf(Settings.KEY_LOW_STOCK to 7)))
        assertTrue(back[Settings.KEY_LOW_STOCK] is Int)
    }

    @Test
    fun nullsAreNotWritten() {
        val json = Backup.settingsToJson(mapOf(Settings.KEY_SOUND_URI to null))
        assertFalse(json.has(Settings.KEY_SOUND_URI))
    }

    @Test
    fun emptyObjectGivesEmptyMap() = assertTrue(Backup.settingsFromJson(JSONObject()).isEmpty())

    @Test
    fun deviceStateIsNotBackedUp() {
        // Показанный туториал, незакрытый сон и «про день уже сказали» — состояние устройства.
        assertFalse(Settings.KEY_TUTORIAL_SEEN in Settings.BACKUP_KEYS)
        assertFalse(Settings.KEY_SLEEP_START in Settings.BACKUP_KEYS)
        assertFalse(Settings.KEY_DAY_DONE in Settings.BACKUP_KEYS)
        assertFalse(Settings.KEY_CHANNEL_VERSION in Settings.BACKUP_KEYS)
    }

    @Test
    fun everythingTheUserTunesIsBackedUp() {
        listOf(
            Settings.KEY_QUIET_ENABLED, Settings.KEY_QUIET_TO, Settings.KEY_REPEAT_COUNT,
            Settings.KEY_SNOOZE_OPTIONS, Settings.KEY_VISIT_OFFSETS, Settings.KEY_VISIT_CUSTOM,
            Settings.KEY_LOW_STOCK, Settings.KEY_WAKE_REMIND_AT, Settings.KEY_WIDGET_OPACITY,
            Settings.KEY_LANGUAGE, Settings.KEY_FULL_SCREEN, Settings.KEY_ALARM_SOUND,
        ).forEach { assertTrue(it, it in Settings.BACKUP_KEYS) }
    }
}
