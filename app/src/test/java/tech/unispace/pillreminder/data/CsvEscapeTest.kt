package tech.unispace.pillreminder.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** CSV открывают в Excel: формулы из названий и заметок исполняться не должны. */
class CsvEscapeTest {

    @Test
    fun plainValueUntouched() = assertEquals("Магний", Backup.csv("Магний"))

    @Test
    fun commaGetsQuoted() = assertEquals("\"Магний, 500\"", Backup.csv("Магний, 500"))

    @Test
    fun quotesDoubled() = assertEquals("\"он сказал \"\"пей\"\"\"", Backup.csv("он сказал \"пей\""))

    @Test
    fun formulaNeutralised() {
        assertEquals("'=SUM(A1:A9)", Backup.csv("=SUM(A1:A9)"))
        assertEquals("'+1", Backup.csv("+1"))
        assertEquals("'@name", Backup.csv("@name"))
        assertEquals("'-5", Backup.csv("-5"))
    }

    @Test
    fun formulaWithCommaIsBothQuotedAndNeutralised() =
        assertEquals("\"'=A1,B2\"", Backup.csv("=A1,B2"))
}
