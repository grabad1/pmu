package rs.etf.focusguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.etf.focusguard.util.PRESET_CATEGORIES
import rs.etf.focusguard.util.normaliseLabel

/**
 * Categories and topics are free text, so the only thing standing between "Math" and "math "
 * becoming two separate topics is this normalisation. A topic recorded inconsistently splits
 * its own averages, which is the one thing the grouping exists to prevent.
 */
class CategoriesTest {

    @Test
    fun `the preset list is offered in a stable order`() {
        assertEquals(listOf("Studying", "Work", "Reading", "Yoga", "Other"), PRESET_CATEGORIES)
    }

    @Test
    fun `every preset category is distinct`() {
        assertEquals(PRESET_CATEGORIES.distinct(), PRESET_CATEGORIES)
    }

    @Test
    fun `no preset category is blank`() {
        assertTrue(PRESET_CATEGORIES.all { it.isNotBlank() })
    }

    @Test
    fun `no preset category carries stray whitespace`() {
        assertTrue(PRESET_CATEGORIES.all { it == it.trim() })
    }

    @Test
    fun `a plain label is returned unchanged`() {
        assertEquals("Math", normaliseLabel("Math"))
    }

    @Test
    fun `surrounding whitespace is removed`() {
        assertEquals("Math", normaliseLabel("  Math  "))
    }

    @Test
    fun `a tab and newline count as whitespace`() {
        assertEquals("Math", normaliseLabel("\tMath\n"))
    }

    @Test
    fun `an empty label becomes nothing`() {
        assertNull(normaliseLabel(""))
    }

    @Test
    fun `a label of only spaces becomes nothing`() {
        assertNull(normaliseLabel("     "))
    }

    @Test
    fun `a null label stays nothing`() {
        assertNull(normaliseLabel(null))
    }

    @Test
    fun `capitalisation is preserved rather than corrected`() {
        assertEquals("math", normaliseLabel("math"))
        assertEquals("MATH", normaliseLabel("MATH"))
    }

    @Test
    fun `inner spacing is left alone`() {
        assertEquals("Operating Systems", normaliseLabel("  Operating Systems "))
    }

    @Test
    fun `normalising twice changes nothing further`() {
        val once = normaliseLabel("  Compilers ")
        assertEquals(once, normaliseLabel(once))
    }

    @Test
    fun `a label that is only punctuation is kept`() {
        assertEquals("-", normaliseLabel(" - "))
        assertFalse(normaliseLabel(" - ") == null)
    }
}
