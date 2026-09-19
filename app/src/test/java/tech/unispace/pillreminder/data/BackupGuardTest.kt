package tech.unispace.pillreminder.data

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Импорт стирает базу, поэтому чужой файл обязан отсеиваться до очистки. */
class BackupGuardTest {

    @Test
    fun ownFileWithMarker() =
        assertTrue(Backup.looksLikeBackup(JSONObject().put("app", Backup.APP_MARKER)))

    @Test
    fun oldFileWithoutMarker() =
        assertTrue(Backup.looksLikeBackup(JSONObject().put("version", 5).put("medications", org.json.JSONArray())))

    @Test
    fun emptyObjectRejected() = assertFalse(Backup.looksLikeBackup(JSONObject()))

    @Test
    fun foreignJsonRejected() {
        val alien = JSONObject().put("name", "config").put("items", org.json.JSONArray()).put("version", 2)
        assertFalse(Backup.looksLikeBackup(alien))
    }

    @Test
    fun versionWithoutMedicationsRejected() =
        assertFalse(Backup.looksLikeBackup(JSONObject().put("version", 6)))
}
