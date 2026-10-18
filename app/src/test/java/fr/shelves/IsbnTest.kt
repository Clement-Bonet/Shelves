package fr.shelves

import fr.shelves.util.Isbn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IsbnTest {
    @Test
    fun validatesEan13() {
        assertTrue(Isbn.isValidEan13("9782723488525"))
        assertFalse(Isbn.isValidEan13("9782723488526"))
    }

    @Test
    fun validatesEan8() {
        assertTrue(Isbn.isValidEan8("96385074"))
        assertFalse(Isbn.isValidEan8("96385075"))
    }

    @Test
    fun convertsIsbn10() {
        assertTrue(Isbn.isValidIsbn10("2723488527"))
        assertEquals("9782723488525", Isbn.isbn10To13("2723488527"))
        assertTrue(Isbn.isValidIsbn10("080442957X"))
    }

    @Test
    fun normalizesInput() {
        assertEquals("9782723488525", Isbn.toSearchCode("978-2-7234-8852-5"))
        assertEquals("9782723488525", Isbn.toSearchCode("2-7234-8852-7"))
        assertNull(Isbn.toSearchCode("12345"))
    }
}
