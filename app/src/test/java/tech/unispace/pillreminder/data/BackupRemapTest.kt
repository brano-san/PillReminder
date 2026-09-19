package tech.unispace.pillreminder.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * При импорте база очищается и id выдаются заново, поэтому все ссылки между таблетками
 * обязаны переводиться по карте. Здесь проверяется само правило перевода списка «разносить с этими».
 */
class BackupRemapTest {

    /** Та же логика, что во втором проходе импорта (Backup.importParsed). */
    private fun remap(csv: String, map: Map<Long, Long>): String =
        csv.split(',').mapNotNull { it.trim().toLongOrNull() }.mapNotNull { map[it] }.joinToString(",")

    private val map = mapOf(10L to 1L, 11L to 2L, 12L to 3L)

    @Test
    fun idsAreTranslated() = assertEquals("1,3", remap("10,12", map))

    @Test
    fun missingIdsAreDropped() = assertEquals("2", remap("11,99", map))

    @Test
    fun emptyStaysEmpty() = assertEquals("", remap("", map))

    @Test
    fun garbageIgnored() = assertEquals("1", remap("10, x, ,", map))

    @Test
    fun exportKeepsTheField() {
        // Поле должно быть в файле — иначе переводить будет нечего.
        val med = JSONObject().put("id", 10).put("apartFromMedIds", "11,12")
        assertEquals("11,12", med.optString("apartFromMedIds"))
        assertEquals("2,3", remap(med.optString("apartFromMedIds"), map))
    }

    @Test
    fun groupsCarryTheirNames() {
        val groups = JSONArray().put(JSONObject().put("id", 5).put("name", "Утро").put("sortOrder", 1))
        assertEquals("Утро", groups.getJSONObject(0).getString("name"))
        assertEquals(1, groups.getJSONObject(0).optInt("sortOrder"))
    }
}
