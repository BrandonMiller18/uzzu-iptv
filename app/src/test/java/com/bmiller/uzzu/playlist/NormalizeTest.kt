package com.bmiller.uzzu.playlist

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizeTest {
    @Test
    fun `lowercases, strips punctuation and collapses whitespace`() {
        assertEquals("st louis blues", normalizeName("St.Louis Blues"))
        assertEquals("st louis blues", normalizeName("  St. Louis   Blues "))
        assertEquals("san francisco 49ers", normalizeName("San Francisco 49ers"))
        assertEquals("texas a m", normalizeName("Texas A&M"))
        assertEquals("hawaii", normalizeName("Hawai'i"))
        assertEquals("athletics", normalizeName("Athletics"))
        assertEquals("montréal canadiens", normalizeName("Montréal Canadiens"))
        assertEquals("", normalizeName(" .- "))
    }
}
