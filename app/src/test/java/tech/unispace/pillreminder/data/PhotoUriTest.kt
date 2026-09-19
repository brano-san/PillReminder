package tech.unispace.pillreminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Файл бэкапа мог приехать от другого человека: открывать произвольный URI своими правами нельзя. */
class PhotoUriTest {

    @Test
    fun contentUriKept() {
        val uri = "content://media/external/images/media/42"
        assertEquals(uri, Backup.safePhotoUri(uri))
    }

    @Test
    fun ownFileUriKept() {
        val uri = "file:///data/user/0/tech.unispace.pillreminder/files/photo.jpg"
        assertEquals(uri, Backup.safePhotoUri(uri))
    }

    @Test
    fun foreignSchemesDropped() {
        assertNull(Backup.safePhotoUri("file:///etc/passwd"))
        assertNull(Backup.safePhotoUri("http://example.com/a.jpg"))
        assertNull(Backup.safePhotoUri("javascript:alert(1)"))
        assertNull(Backup.safePhotoUri(""))
    }

    @Test
    fun nullStaysNull() = assertNull(Backup.safePhotoUri(null))
}
