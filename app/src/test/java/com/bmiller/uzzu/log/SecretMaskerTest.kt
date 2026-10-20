package com.bmiller.uzzu.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SecretMaskerTest {
    private fun mask(s: String) = SecretMasker.mask(s)

    @Test
    fun `account id path segment keeps last 4 digits`() {
        assertEquals("https://uzzu.tv/hls/******7220/g4678.m3u8", mask("https://uzzu.tv/hls/10000007220/g4678.m3u8"))
        assertEquals("/hls/******7220/g4678.m3u8", mask("/hls/10000007220/g4678.m3u8"))
        assertEquals("uzzu.tv/hls/******7220/12.m3u8", mask("uzzu.tv/hls/10000007220/12.m3u8"))
    }

    @Test
    fun `short numbers in paths are left alone`() {
        assertEquals("https://h/perdias/444734-away/chunks.m3u8", mask("https://h/perdias/444734-away/chunks.m3u8"))
        assertEquals("https://uzzu.tv/hls/******7220/logo/t519", mask("https://uzzu.tv/hls/10000007220/logo/t519"))
    }

    @Test
    fun `fastly expiry and hash token is masked`() {
        assertEquals(
            "https://tran03.global.ssl.fastly.net/****~****/perdias/444734-away/chunks.m3u8",
            mask("https://tran03.global.ssl.fastly.net/1792476833~30dbd327540cffa518ffa27bf3ae58eb/perdias/444734-away/chunks.m3u8"),
        )
    }

    @Test
    fun `emails are masked anywhere`() {
        assertEquals("contact ***@*** now", mask("contact someone.name+tv@example.co.uk now"))
        assertEquals("https://host.tv/get/***@***", mask("https://host.tv/get/someone@example.com"))
        assertEquals("https://host.tv/get/***@***", mask("https://host.tv/get/someone%40example.com"))
    }

    @Test
    fun `playlist url with account id and email in the path is fully masked`() {
        val url = "https://uzzu.tv/m3u10000007220/someone0809@example.com"
        val masked = mask(url)
        assertEquals("https://uzzu.tv/m3u******7220/***@***", masked)
        assertFalse("10000007220" in masked)
        assertFalse("someone" in masked)
    }

    @Test
    fun `url userinfo is masked`() {
        assertEquals("https://***@host.tv/a.m3u", mask("https://user:secret@host.tv/a.m3u"))
    }

    @Test
    fun `query values are masked`() {
        assertEquals(
            "https://uzzu.tv/get.php?username=***&password=***&type=***",
            mask("https://uzzu.tv/get.php?username=bob&password=hunter2&type=m3u_plus"),
        )
    }

    @Test
    fun `free text and url inside a sentence`() {
        assertEquals(
            "tune Ducks · HOME uzzu.tv/hls/******7220/g4678.m3u8 +120 ms",
            mask("tune Ducks · HOME uzzu.tv/hls/10000007220/g4678.m3u8 +120 ms"),
        )
        assertEquals("", SecretMasker.mask(null))
        assertEquals("plain 1234567890 text", mask("plain 1234567890 text"))
    }
}
