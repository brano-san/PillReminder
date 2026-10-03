package tech.unispace.pillreminder.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {

    @Test
    fun comparesVersionsNumerically() {
        assertTrue(Updater.isNewer("v1.3.1", "1.3.0"))
        assertTrue(Updater.isNewer("v1.10", "1.9.9"))
        assertTrue(Updater.isNewer("2.0", "1.99"))
        assertFalse(Updater.isNewer("v1.3.0", "1.3.0"))
        assertFalse(Updater.isNewer("v1.3", "1.3.0"))
        assertFalse(Updater.isNewer("v1.2.4", "1.3.0"))
    }

    @Test
    fun parsesReleaseWithApk() {
        val json = """
            {"tag_name":"v1.3.1","body":"Added\r\n- Updates",
             "assets":[{"name":"notes.txt","browser_download_url":"x","size":1},
                       {"name":"DoseDay-1.3.1-release.apk","browser_download_url":"https://e/a.apk","size":1300000}]}
        """.trimIndent()
        val r = Updater.parseRelease(json)!!
        assertEquals("1.3.1", r.version)
        assertEquals("Added\n- Updates", r.notes)
        assertEquals("https://e/a.apk", r.apkUrl)
        assertEquals(1300000L, r.apkSize)
    }

    @Test
    fun releaseWithoutApkIsIgnored() {
        assertNull(Updater.parseRelease("""{"tag_name":"v1.4","assets":[]}"""))
    }
}
