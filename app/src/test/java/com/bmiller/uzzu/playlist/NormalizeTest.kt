package com.bmiller.uzzu.playlist

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizeTest {
    @Test
    fun `lowercases, strips punctuation and collapses whitespace`() {
        assertEquals("st louis blues", normalizeName("St.Louis Blues"))
        assertEquals("st louis blues", normalizeName("  St. Louis   Blues "))
        assertEquals("st louis blues", normalizeName("ST LOUIS BLUES"))
        assertEquals("san francisco 49ers", normalizeName("San Francisco 49ers"))
        assertEquals("texas a m", normalizeName("Texas A&M"))
        assertEquals("hawaii", normalizeName("Hawai'i"))
        assertEquals("athletics", normalizeName("Athletics"))
        assertEquals("oakland as", normalizeName("Oakland A’s"))
        assertEquals("miami oh redhawks", normalizeName("Miami (OH) RedHawks"))
        assertEquals("arizona d backs", normalizeName("Arizona D-backs"))
        assertEquals("", normalizeName(" .- "))
    }

    @Test
    fun `folds accents`() {
        assertEquals("montreal canadiens", normalizeName("Montréal Canadiens"))
        assertEquals("san jose state spartans", normalizeName("San José State Spartans"))
    }
}
